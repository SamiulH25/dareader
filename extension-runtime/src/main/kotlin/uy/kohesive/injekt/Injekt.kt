package uy.kohesive.injekt

import dareader.ext.di.DareaderGraph

/**
 * Dareader-owned Injekt facade shim. Upstream extensions resolve singletons
 * through Injekt; we back it with [DareaderGraph] instead of Koin modules.
 */
object Injekt {
    fun <T : Any> get(cls: Class<T>): T = DareaderGraph.get(cls)

    inline fun <reified T : Any> get(): T = get(T::class.java)
}

/** Scope entry point used by generated `getInjekt().getInstance(type)` call sites. */
fun getInjekt(): uy.kohesive.injekt.api.InjektScope = DareaderScope

private object DareaderScope : uy.kohesive.injekt.api.InjektScope {
    override fun getInstance(type: java.lang.reflect.Type): Any {
        val raw = when (type) {
            is Class<*> -> type
            is java.lang.reflect.ParameterizedType -> type.rawType as Class<*>
            else -> error("unsupported injekt lookup type: $type")
        }
        return DareaderGraph.get(raw)
    }
}

/** Lazy delegate matching injekt-api's injectLazy() call sites. */
inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { Injekt.get<T>() }
