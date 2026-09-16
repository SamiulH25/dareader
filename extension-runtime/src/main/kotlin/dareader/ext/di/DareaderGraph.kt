package dareader.ext.di

import java.util.concurrent.ConcurrentHashMap

/**
 * Dareader-owned service registry backing the uy.kohesive.injekt shim.
 * Extension-facing singletons (Application, NetworkHelper, Json) are
 * registered here at startup instead of via Koin modules.
 */
object DareaderGraph {
    val application = android.app.Application()

    private val instances = ConcurrentHashMap<Class<*>, Any>()

    init {
        register(android.app.Application::class.java, application)
        register(android.content.Context::class.java, application)
    }

    fun has(cls: Class<*>): Boolean = instances.containsKey(cls)

    fun <T : Any> register(cls: Class<T>, instance: T) {
        instances[cls] = instance
    }

    fun <T : Any> get(cls: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return instances[cls] as? T
            ?: error("DareaderGraph: no instance registered for ${cls.name}")
    }

    /** Registers NetworkHelper/Json once; every entry point needs these. */
    fun ensureCore() {
        instances.computeIfAbsent(eu.kanade.tachiyomi.network.NetworkHelper::class.java) {
            eu.kanade.tachiyomi.network.NetworkHelper(application)
        }
        instances.computeIfAbsent(kotlinx.serialization.json.Json::class.java) {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        }
    }
}
