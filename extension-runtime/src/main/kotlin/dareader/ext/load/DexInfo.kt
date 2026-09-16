package dareader.ext.load

import com.googlecode.d2j.Field
import com.googlecode.d2j.Method
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.d2j.visitors.DexClassVisitor
import com.googlecode.d2j.visitors.DexCodeVisitor
import com.googlecode.d2j.visitors.DexFieldVisitor
import com.googlecode.d2j.visitors.DexFileVisitor
import com.googlecode.d2j.visitors.DexMethodVisitor
import java.nio.file.Files
import java.nio.file.Path

/** Dex-level inventory for translator debugging: classes, methods, access flags. */
fun dexInventory(apk: Path, filter: String): List<String> {
    val out = mutableListOf<String>()
    val reader = MultiDexFileReader.open(Files.readAllBytes(apk))
    reader.accept(
        object : DexFileVisitor() {
            override fun visit(
                access: Int,
                className: String,
                superClass: String,
                interfaceNames: Array<String>,
            ): DexClassVisitor? {
                if (!className.contains(filter)) return null
                out += "class $className super=$superClass access=$access"
                return object : DexClassVisitor() {
                    override fun visitMethod(access: Int, method: Method): DexMethodVisitor? {
                        out += "  method ${method.name}${method.desc} access=$access"
                        return null
                    }

                    override fun visitField(access: Int, field: Field, value: Any?): DexFieldVisitor? {
                        out += "  field ${field.name}:${field.type}"
                        return null
                    }
                }
            }
        },
    )
    return out
}

/** Dumps Dalvik ops for one method: <init> translation forensics. */
fun dexCode(apk: Path, className: String, methodName: String): List<String> {
    val out = mutableListOf<String>()
    val reader = MultiDexFileReader.open(Files.readAllBytes(apk))
    reader.accept(
        object : DexFileVisitor() {
            override fun visit(
                access: Int,
                name: String,
                superClass: String,
                interfaceNames: Array<String>,
            ): DexClassVisitor? {
                if (name != className) return null
                return object : DexClassVisitor() {
                    override fun visitMethod(access: Int, method: Method): DexMethodVisitor? {
                        if (method.name != methodName) return null
                        return object : DexMethodVisitor() {
                            override fun visitCode(): DexCodeVisitor {
                                return object : DexCodeVisitor() {
                                    override fun visitTypeStmt(
                                        op: com.googlecode.d2j.reader.Op,
                                        a: Int,
                                        b: Int,
                                        type: String,
                                    ) {
                                        out += "$op v$a v$b $type"
                                    }

                                    override fun visitMethodStmt(
                                        op: com.googlecode.d2j.reader.Op,
                                        args: IntArray,
                                        method: Method,
                                    ) {
                                        out += "$op ${args.toList()} ${method.owner}.${method.name}${method.desc}"
                                    }

                                    override fun visitFieldStmt(
                                        op: com.googlecode.d2j.reader.Op,
                                        from: Int,
                                        to: Int,
                                        field: Field,
                                    ) {
                                        out += "$op v$from v$to ${field.owner}.${field.name}:${field.type}"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
    return out
}
