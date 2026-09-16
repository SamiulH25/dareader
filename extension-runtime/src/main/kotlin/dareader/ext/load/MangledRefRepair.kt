package dareader.ext.load

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val logger = KotlinLogging.logger {}

/**
 * Restores external references that dex2jar sanitizes.
 *
 * `-` is legal in JVM identifiers and used by Kotlin-mangled declarations
 * (OkHttp's `maxStale-LRDsOJo`, stdlib's `getInWholeMilliseconds-impl`),
 * but dex2jar rewrites `-` to `_` on translated call sites, producing
 * NoSuchMethodErrors against the real libraries. For every member reference
 * whose owner is outside the jar, if the exact reference does not resolve
 * on our classpath but a dashed variant does, the call site is rewritten.
 * Unresolvable references are left alone (JVM resolution is lazy).
 */
fun fixMangledExternalRefs(jar: Path) {
    FileSystems.newFileSystem(jar, null as ClassLoader?).use { fs ->
        val entries = Files.walk(fs.getPath("/"))
            .filter { !Files.isDirectory(it) }
            .filter { it.toString().endsWith(".class") }
            .toList()
        val classes = entries.associateWith { path ->
            ClassNode().also { ClassReader(Files.readAllBytes(path)).accept(it, 0) }
        }
        val internal = classes.values.map { it.name }.toSet()

        var fixed = 0
        for ((_, node) in classes) {
            for (method in node.methods) {
                var insn = method.instructions.first
                while (insn != null) {
                    when (insn) {
                        is MethodInsnNode -> {
                            val m = insn as MethodInsnNode
                            checkMethodRef(node.name, method.name, m.owner, m.name, m.desc, MethodKind.METHOD, internal) {
                                m.name = it
                                fixed++
                            }
                        }
                        is FieldInsnNode -> {
                            val f = insn as FieldInsnNode
                            checkFieldRef(node.name, method.name, f.owner, f.name, f.desc, internal) {
                                f.name = it
                                fixed++
                            }
                        }
                    }
                    insn = insn.next
                }
            }
        }

        if (fixed > 0) {
            println("repair: restored $fixed mangled external refs")
            for ((path, node) in classes) {
                val writer = SafeClassWriter(ClassWriter.COMPUTE_FRAMES, jar)
                node.accept(writer)
                Files.write(path, writer.toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
            }
        }
    }
}
private enum class MethodKind { METHOD, FIELD }

private fun resolves(owner: String, name: String, desc: String, kind: MethodKind): Boolean {
    return try {
        val cls = Class.forName(owner.replace('/', '.'), false, ExternalRefLoader.loader)
        when (kind) {
            MethodKind.METHOD -> cls.declaredMethods.any { it.name == name && Type.getMethodDescriptor(it) == desc } ||
                cls.methods.any { it.name == name && Type.getMethodDescriptor(it) == desc }
            MethodKind.FIELD -> cls.declaredFields.any { it.name == name && Type.getDescriptor(it.type) == desc } ||
                cls.fields.any { it.name == name && Type.getDescriptor(it.type) == desc }
        }
    } catch (_: Exception) {
        true // Not loadable here; JVM resolution is lazy, leave alone.
    }
}

private fun checkMethodRef(
    className: String,
    methodName: String,
    owner: String,
    name: String,
    desc: String,
    kind: MethodKind,
    internal: Set<String>,
    rename: (String) -> Unit,
): Boolean {
    if (owner in internal || '_' !in name || resolves(owner, name, desc, kind)) return false
    val restored = dashCandidates(name).firstOrNull { resolves(owner, it, desc, kind) } ?: return false
    logger.debug { "restoring $className.$methodName: $name -> $restored" }
    rename(restored)
    return true
}

/** Most likely dashed spellings first: full replace, then single sites. */
private fun dashCandidates(name: String): List<String> {
    val out = mutableListOf(name.replace('_', '-'))
    var idx = name.indexOf('_')
    while (idx >= 0) {
        out += name.substring(0, idx) + '-' + name.substring(idx + 1)
        idx = name.indexOf('_', idx + 1)
    }
    return out.distinct()
}

private fun checkFieldRef(
    className: String,
    methodName: String,
    owner: String,
    name: String,
    desc: String,
    internal: Set<String>,
    rename: (String) -> Unit,
): Boolean = checkMethodRef(className, methodName, owner, name, desc, MethodKind.FIELD, internal, rename)

private object ExternalRefLoader {
    val loader: ClassLoader = ExternalRefLoader::class.java.classLoader
}
