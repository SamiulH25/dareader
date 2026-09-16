package dareader.ext.load

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import java.nio.file.Files
import java.nio.file.Path

/**
 * Converts an extension APK's Dalvik bytecode to JVM classes, mirroring
 * Suwayomi PackageTools.dex2jar (same flags).
 */
fun dexToJar(apk: Path): Path {
    val jar = Files.createTempFile("dareader-ext-", ".jar")
    try {
        val reader = MultiDexFileReader.open(Files.readAllBytes(apk))
        val handler = BaksmaliBaseDexExceptionHandler()
        Dex2jar.from(reader)
            .withExceptionHandler(handler)
            .reUseReg(false)
            .topoLogicalSort()
            .skipDebug(true)
            .optimizeSynchronized(false)
            .printIR(false)
            .noCode(false)
            .skipExceptions(false)
            .computeFrames(true)
            .to(jar)
        if (handler.hasException()) {
            val err = Files.createTempFile("dareader-ext-", "-error.txt")
            handler.dump(err, emptyArray())
            throw IllegalStateException("dex2jar errors, see $err")
        }
        val report = mutableListOf<String>()
        fixR8ConstructorSites(jar, report)
        fixMangledExternalRefs(jar)
        report.forEach { println("repair: $it") }
        return jar
    } catch (e: Exception) {
        Files.deleteIfExists(jar)
        throw e
    }
}
