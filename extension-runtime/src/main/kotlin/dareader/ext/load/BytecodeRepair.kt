package dareader.ext.load

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val logger = KotlinLogging.logger {}

/**
 * Repairs R8 full-mode output that dex2jar mistranslates.
 *
 * R8 removes trivial constructors but leaves allocation sites paired with a
 * superclass `<init>` call (ART types the value by the `new` and accepts
 * this). dex2jar copies the init-owner into the `new`, emitting
 * `new Object` + `Object.<init>`, which the JVM verifier rejects at the
 * first typed use (`putfield`, `checkcast`, ...). HotSpot additionally
 * requires the init owner to match the allocation type exactly, so both the
 * `new` and the paired call are rewritten and the missing trivial ctor is
 * synthesized (R8 proved it side-effect-free by stripping it).
 *
 * A second R8 shape, stripped `super()` calls (`Object.<init>` on `this`
 * where the direct super differs), is retargeted at the direct superclass.
 * A third, stripped enum entries (`new Enum` + `Enum.<init>`, never valid),
 * is rewritten to the enclosing enum with the standard entry ctor.
 *
 * Anything unprovable is reported loudly instead of silently kept.
 */
fun fixR8ConstructorSites(jar: Path, report: MutableList<String>? = null) {
    FileSystems.newFileSystem(jar, null as ClassLoader?).use { fs ->
        val entries = Files.walk(fs.getPath("/"))
            .filter { !Files.isDirectory(it) }
            .filter { it.toString().endsWith(".class") }
            .toList()
        val classes = entries.associateWith { path ->
            ClassNode().also { ClassReader(Files.readAllBytes(path)).accept(it, 0) }
        }
        val byName = classes.values.associateBy { it.name }

        var repaired = 0
        val unhandled = mutableListOf<String>()
        val needsCtor = mutableMapOf<String, MutableSet<String>>()
        fun needCtor(target: String, desc: String) {
            needsCtor.getOrPut(target) { mutableSetOf() } += desc
        }
        val loader = java.net.URLClassLoader(
            arrayOf(jar.toUri().toURL()),
            RepairAnchor::class.java.classLoader,
        )
        fun hasCtor(target: String, desc: String): Boolean {
            byName[target]?.let { node ->
                return node.methods.any { it.name == "<init>" && it.desc == desc }
            }
            return try {
                Class.forName(target.replace('/', '.'), false, loader)
                    .declaredConstructors.any { Type.getConstructorDescriptor(it) == desc }
            } catch (_: Exception) {
                false
            }
        }
        repaired += fixEnumEntries(classes, ::needCtor, unhandled, report)
        for ((_, node) in classes) {
            for (method in node.methods) {
                repaired += repairMethod(node.name, node.superName, method, ::needCtor, ::hasCtor, unhandled, report)
            }
        }

        // Synthesize missing trivial ctors to a fixpoint: each synth chains to
        // its direct super's no-arg ctor, which may itself need synthesis.
        // ()V chains to super(); (String,int) is the enum-entry shape and
        // forwards to Enum.<init>. Anything else fails loud on evidence.
        val done = mutableSetOf<Pair<String, String>>()
        while (true) {
            val next = needsCtor.entries
                .flatMap { (target, descs) -> descs.map { target to it } }
                .firstOrNull { it !in done } ?: break
            done += next
            val (target, desc) = next
            if (desc != "()V" && desc != "(Ljava/lang/String;I)V") {
                unhandled += "non-trivial ctor synthesis: $target$desc"
                continue
            }
            val targetNode = byName[target] ?: run {
                unhandled += "ctor target outside jar: $target$desc"
                continue
            }
            if (targetNode.methods.any { it.name == "<init>" && it.desc == desc }) continue
            val superName = targetNode.superName.ifEmpty { "java/lang/Object" }
            val ctor = MethodNode(Opcodes.ACC_PUBLIC, "<init>", desc, null, null)
            ctor.instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            if (desc == "(Ljava/lang/String;I)V") {
                // Enum entry: forward (name, ordinal) to Enum.<init>.
                ctor.instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
                ctor.instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
                ctor.instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, superName, "<init>", desc, false))
            } else {
                ctor.instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false))
                if (!hasCtor(superName, "()V")) needCtor(superName, "()V")
            }
            ctor.instructions.add(InsnNode(Opcodes.RETURN))
            targetNode.methods.add(ctor)
        }
        if (unhandled.isNotEmpty()) {
            loader.close()
            error("unresolved R8 constructor sites:\n" + unhandled.joinToString("\n"))
        }

        for ((path, node) in classes) {
            val writer = SafeClassWriter(ClassWriter.COMPUTE_FRAMES, loader)
            node.accept(writer)
            Files.write(path, writer.toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
        }
        loader.close()
        logger.debug { "repaired $repaired R8 constructor sites in $jar" }
    }
}

private data class Tag(val id: Int)

private data object ThisRef

private object RepairAnchor

/**
 * Rewrites a mistranslated `new Object` to the type recovered from its use
 * site, retargets the paired superclass init call (HotSpot requires an exact
 * owner match), and records ctor synthesis.
 */
private fun applyFix(
    method: MethodNode,
    newInsn: TypeInsnNode,
    typeDesc: String,
    needCtor: (String, String) -> Unit,
): Boolean {
    val internal = Type.getType(typeDesc).takeIf { it.sort == Type.OBJECT }?.internalName ?: return false
    if (internal == "java/lang/Object") return true
    newInsn.desc = internal
    // Pair with the init call, skipping labels/line numbers dex2jar emits.
    var probe: AbstractInsnNode? = nextCode(newInsn.next)
    if (probe?.opcode == Opcodes.DUP) probe = nextCode(probe.next)
    if (probe is MethodInsnNode && probe.opcode == Opcodes.INVOKESPECIAL &&
        probe.owner == "java/lang/Object" && probe.name == "<init>" && probe.desc == "()V"
    ) {
        probe.owner = internal
    }
    needCtor(internal, "()V")
    return true
}

private fun nextCode(node: AbstractInsnNode?): AbstractInsnNode? {
    var probe = node
    while (probe != null &&
        (probe.type == AbstractInsnNode.LABEL || probe.type == AbstractInsnNode.LINE ||
            probe.type == AbstractInsnNode.FRAME)
    ) {
        probe = probe.next
    }
    return probe
}

/**
 * Rewrites R8-stripped enum entries (`new Enum` can never verify: abstract
 * class, protected ctor). The only sound target is the enclosing enum.
 */
private fun fixEnumEntries(
    classes: Map<Path, ClassNode>,
    needCtor: (String, String) -> Unit,
    unhandled: MutableList<String>,
    report: MutableList<String>?,
): Int {
    var repaired = 0
    for ((_, node) in classes) {
        if (node.superName != "java/lang/Enum") continue
        for (method in node.methods) {
            var insn = method.instructions.first
            while (insn != null) {
                val isNewEnum = insn is TypeInsnNode && insn.opcode == Opcodes.NEW && insn.desc == "java/lang/Enum"
                if (isNewEnum) {
                    // Entry args (name, ordinal) sit between DUP and the call.
                    var probe = nextCode(insn.next)
                    if (probe?.opcode == Opcodes.DUP) probe = nextCode(probe.next)
                    while (probe != null && probe.opcode in ARG_PUSH_OPS) probe = nextCode(probe.next)
                    if (probe is MethodInsnNode && probe.opcode == Opcodes.INVOKESPECIAL &&
                        probe.owner == "java/lang/Enum" && probe.name == "<init>" &&
                        probe.desc == "(Ljava/lang/String;I)V"
                    ) {
                        (insn as TypeInsnNode).desc = node.name
                        probe.owner = node.name
                        needCtor(node.name, "(Ljava/lang/String;I)V")
                        report?.add("${node.name}.${method.name}${method.desc}: enum entry")
                        repaired++
                    } else {
                        unhandled += "${node.name}.${method.name}: untargetable new Enum"
                    }
                }
                insn = insn.next
            }
        }
    }
    return repaired
}

/** Opcodes that only push entry-constructor args; anything else ends the scan. */
private val ARG_PUSH_OPS = setOf(
    Opcodes.LDC, Opcodes.ALOAD, Opcodes.ILOAD,
    Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
    Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5,
    Opcodes.BIPUSH, Opcodes.SIPUSH, Opcodes.GETSTATIC,
)

private fun repairMethod(
    className: String,
    superName: String,
    method: MethodNode,
    needCtor: (String, String) -> Unit,
    hasCtor: (String, String) -> Boolean,
    unhandled: MutableList<String>,
    report: MutableList<String>?,
): Int {
    // NEW insn by tag for `new Object` allocations.
    val sites = mutableMapOf<Tag, TypeInsnNode>()
    val stack = ArrayDeque<Any?>()
    val locals = mutableMapOf<Int, Any?>()
    var nextId = 0
    var repaired = 0
    var thisDone = false

    fun push(v: Any?) = stack.addLast(v)
    fun pop(): Any? = if (stack.isEmpty()) null else stack.removeLast()
    fun fixValue(v: Any?, typeDesc: String, what: String): Boolean {
        if (v !is Tag) return false
        val newInsn = sites[v] ?: return false
        if (!applyFix(method, newInsn, typeDesc, needCtor)) {
            unhandled += "$className.${method.name}: non-object use $what : $typeDesc"
            return false
        }
        sites.remove(v)
        report?.add("$className.${method.name}${method.desc}: $typeDesc")
        for (i in stack.indices) if (stack[i] == v) stack[i] = null
        for ((k, existing) in locals) if (existing == v) locals[k] = null
        repaired++
        return true
    }

    var insn = method.instructions.first
    while (insn != null) {
        when (insn.opcode) {
            Opcodes.NEW -> {
                insn as TypeInsnNode
                if (insn.desc == "java/lang/Object") {
                    val tag = Tag(nextId++)
                    sites[tag] = insn
                    push(tag)
                } else {
                    push(null)
                }
            }
            Opcodes.DUP -> {
                val v = pop()
                push(v)
                push(v)
            }
            Opcodes.POP -> pop()
            Opcodes.POP2 -> {
                pop()
                pop()
            }
            Opcodes.SWAP -> {
                val a = pop()
                val b = pop()
                push(a)
                push(b)
            }
            Opcodes.ASTORE -> {
                insn as VarInsnNode
                locals[insn.`var`] = pop()
            }
            Opcodes.ALOAD -> {
                insn as VarInsnNode
                if (method.name == "<init>" && insn.`var` == 0 && !thisDone) {
                    push(ThisRef)
                } else {
                    push(locals[insn.`var`])
                }
            }
            Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE -> pop()
            Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD -> push(null)
            Opcodes.INVOKESPECIAL, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE, Opcodes.INVOKESTATIC -> {
                insn as MethodInsnNode
                val argTypes = Type.getArgumentTypes(insn.desc)
                val args = argTypes.indices.map { pop() }.reversed()
                val receiver = if (insn.opcode == Opcodes.INVOKESTATIC) null else pop()
                // Super-ctor call on a tracked allocation is the paired init: keep simulating.
                val isInitPair = insn.opcode == Opcodes.INVOKESPECIAL &&
                    insn.owner == "java/lang/Object" && insn.name == "<init>" && receiver is Tag
                // Stripped super call on `this`: retarget at the direct superclass.
                val isStrippedSuper = !thisDone && method.name == "<init>" && receiver is ThisRef &&
                    insn.opcode == Opcodes.INVOKESPECIAL && insn.name == "<init>" &&
                    insn.owner != className && insn.owner != superName
                if (isStrippedSuper) {
                    report?.add("$className.${method.name}${method.desc}: super -> $superName${insn.desc}")
                    insn.owner = superName
                    repaired++
                    if (!hasCtor(superName, insn.desc)) needCtor(superName, insn.desc)
                    thisDone = true
                } else {
                    if (!isInitPair) {
                        for ((i, a) in args.withIndex()) {
                            if (a is Tag) {
                                fixValue(a, argTypes[i].descriptor, "arg$i of ${insn.owner}.${insn.name}")
                            }
                        }
                        if (receiver is Tag) {
                            fixValue(receiver, "L${insn.owner};", "receiver of ${insn.owner}.${insn.name}")
                        }
                    }
                    if (receiver is ThisRef && insn.name == "<init>") thisDone = true
                }
                if (Type.getReturnType(insn.desc) != Type.VOID_TYPE) push(null)
            }
            Opcodes.PUTFIELD, Opcodes.PUTSTATIC -> {
                insn as FieldInsnNode
                val value = pop()
                val receiver = if (insn.opcode == Opcodes.PUTFIELD) pop() else null
                if (value is Tag) fixValue(value, insn.desc, "field ${insn.owner}.${insn.name}")
                if (receiver is Tag) fixValue(receiver, "L${insn.owner};", "receiver of ${insn.owner}.${insn.name}")
            }
            Opcodes.GETFIELD -> {
                val receiver = pop()
                if (receiver is Tag) {
                    insn as FieldInsnNode
                    fixValue(receiver, "L${insn.owner};", "receiver of field read")
                }
                push(null)
            }
            Opcodes.GETSTATIC -> push(null)
            Opcodes.CHECKCAST -> {
                insn as TypeInsnNode
                val v = pop()
                if (v is Tag) {
                    val desc = if (insn.desc.startsWith("[")) insn.desc else "L${insn.desc};"
                    fixValue(v, desc, "checkcast")
                }
                push(v)
            }
            Opcodes.ARETURN -> {
                val v = pop()
                if (v is Tag) fixValue(v, method.desc.substringAfter(')'), "return")
            }
            Opcodes.ATHROW -> {
                if (pop() is Tag) unhandled += "$className.${method.name}: tagged value thrown"
            }
            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.RETURN -> {
                if (insn.opcode != Opcodes.RETURN) pop()
            }
            Opcodes.IFNONNULL, Opcodes.IFNULL -> {
                if (pop() is Tag) unhandled += "$className.${method.name}: tagged value in branch"
            }
            Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> {
                val a = pop()
                val b = pop()
                if (a is Tag || b is Tag) unhandled += "$className.${method.name}: tagged value in branch"
            }
            else -> {
                val op = insn.opcode
                when {
                    op in Opcodes.ICONST_M1..Opcodes.SIPUSH || op == Opcodes.LDC || op == Opcodes.ACONST_NULL -> push(null)
                    op in Opcodes.IALOAD..Opcodes.SALOAD || op == Opcodes.AALOAD -> {
                        pop()
                        pop()
                        push(null)
                    }
                    op in Opcodes.IASTORE..Opcodes.SASTORE || op == Opcodes.AASTORE -> {
                        val v = pop()
                        pop()
                        pop()
                        if (v is Tag) unhandled += "$className.${method.name}: tagged value into array"
                    }
                    op == Opcodes.ANEWARRAY || op == Opcodes.NEWARRAY -> {
                        pop() // count
                        push(null)
                    }
                    op == Opcodes.MULTIANEWARRAY -> {
                        insn as MultiANewArrayInsnNode
                        repeat(insn.dims) { pop() }
                        push(null)
                    }
                    op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH -> {
                        if (pop() is Tag) unhandled += "$className.${method.name}: tagged switch key"
                    }
                    op in Opcodes.IADD..Opcodes.DCMPG || op == Opcodes.INEG || op == Opcodes.GOTO -> {
                        // Pure numeric/control ops: tags only ride references,
                        // so these cannot touch tracked values.
                    }
                    op == Opcodes.ARRAYLENGTH || op == Opcodes.MONITORENTER || op == Opcodes.MONITOREXIT -> {
                        if (pop() is Tag) unhandled += "$className.${method.name}: tagged monitor/array $op"
                    }
                    op == Opcodes.INSTANCEOF -> {
                        pop()
                        push(null)
                    }
                    else -> {
                        if (stack.any { it is Tag } || locals.values.any { it is Tag }) {
                            unhandled += "$className.${method.name}: untracked stack op $op"
                            stack.clear()
                            locals.clear()
                        }
                    }
                }
            }
        }
        insn = insn.next
    }
    return repaired
}

internal class SafeClassWriter(flags: Int, private val loader: ClassLoader) : ClassWriter(flags) {
    override fun getCommonSuperClass(type1: String, type2: String): String {
        return try {
            super.getCommonSuperClass(type1, type2)
        } catch (_: Exception) {
            try {
                val c1 = Class.forName(type1.replace('/', '.'), false, loader)
                val c2 = Class.forName(type2.replace('/', '.'), false, loader)
                when {
                    c1.isAssignableFrom(c2) -> type1
                    c2.isAssignableFrom(c1) -> type2
                    else -> "java/lang/Object"
                }
            } catch (_: Exception) {
                "java/lang/Object"
            }
        }
    }
}
