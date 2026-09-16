package dareader.ext.load

import dareader.ext.di.DareaderGraph
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

/** Child-first loader so extension classes win; API types come from the parent. */
private class ChildFirstLoader(
    urls: Array<URL>,
    parent: ClassLoader,
) : URLClassLoader(urls, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            if (name.startsWith("java.") || name.startsWith("jdk.")) {
                return super.loadClass(name, resolve)
            }
            return try {
                findClass(name).also { if (resolve) resolveClass(it) }
            } catch (_: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }
    }
}

data class LoadedExtension(
    val pkgName: String,
    val mainClass: String,
    val sources: List<Source>,
    val close: () -> Unit,
)

/**
 * Instantiates an extension's Source (or multi-source SourceFactory) from a
 * converted jar and returns its sources. Registers shared singletons on first
 * use and auto-registers the sources into [GetSource] under [pkgName].
 *
 * Lifetime: the jar at [jar] is memory-mapped and lazily read by the
 * classloader, so it MUST outlive the returned handle. This function never
 * deletes it; the caller retains the [jar] path and deletes it only after
 * calling [LoadedExtension.close], which unregisters the sources from
 * [GetSource] and closes the classloader.
 */
fun loadExtensionSources(jar: Path, className: String, pkgName: String): LoadedExtension {
    DareaderGraph.ensureCore()

    val loader = ChildFirstLoader(arrayOf(jar.toUri().toURL()), ExtensionLoaderAnchor::class.java.classLoader)
    var registered = false
    try {
        val clazz = Class.forName(className, true, loader)
        val instance = clazz.getDeclaredConstructor().newInstance()
        val sources: List<Source> = when (instance) {
            is Source -> listOf(instance)
            is SourceFactory -> instance.createSources()
            else -> error("unknown extension entry: ${instance.javaClass}")
        }
        GetSource.register(pkgName, sources)
        registered = true
        var closed = false
        return LoadedExtension(pkgName, className, sources) {
            synchronized(loader) {
                if (closed) return@LoadedExtension
                closed = true
            }
            try {
                GetSource.unregister(pkgName)
            } finally {
                loader.close()
            }
        }
    } catch (e: Throwable) {
        if (registered) GetSource.unregister(pkgName)
        loader.close()
        throw e
    }
}

private object ExtensionLoaderAnchor
