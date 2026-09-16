package uy.kohesive.injekt.api

import uy.kohesive.injekt.Injekt

/** injekt-api's reified get(); delegates to the dareader Injekt shim. */
inline fun <reified T : Any> Injekt.get(): T = get(T::class.java)
