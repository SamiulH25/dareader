package uy.kohesive.injekt.api

/**
 * Dareader-owned subset of the injekt-api surface that compiled extensions
 * link against. Resolution delegates to dareader's service graph.
 */
abstract class FullTypeReference<T> {
    val type: java.lang.reflect.Type =
        (javaClass.genericSuperclass as java.lang.reflect.ParameterizedType).actualTypeArguments[0]
}

interface InjektFactory {
    fun getInstance(type: java.lang.reflect.Type): Any
}

interface InjektScope : InjektFactory
