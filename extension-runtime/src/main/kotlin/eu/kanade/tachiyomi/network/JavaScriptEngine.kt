package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.util.lang.withIOContext
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

/**
 * Util for evaluating JavaScript in sources.
 *
 * Dareader adaptation: upstream uses QuickJS (whose JVM natives link LLVM
 * libc++, unavailable on desktop Linux); we back the identical API with
 * pure-Java Rhino so extensions link and run unchanged.
 */
@Suppress("UNUSED", "UNCHECKED_CAST")
class JavaScriptEngine(
    context: Context,
) {
    /**
     * Evaluate arbitrary JavaScript code and get the result as a primitive type
     * (e.g., String, Int).
     *
     * @since tachiyomix 1.4
     * @param script JavaScript to execute.
     * @return Result of JavaScript code as a primitive type.
     */
    suspend fun <T> evaluate(script: String): T =
        withIOContext {
            val rhino = RhinoContext.enter()
            try {
                rhino.optimizationLevel = -1
                val scope = rhino.initStandardObjects()
                val raw = rhino.evaluateString(scope, script, "dareader", 1, null)
                convertResult(raw, scope) as T
            } finally {
                RhinoContext.exit()
            }
        }

    /**
     * Converts a raw Rhino result to plain JVM values: unwraps Rhino host
     * objects via [RhinoContext.jsToJava], flattens lazy strings, coerces
     * numbers to the narrowest usable type (Int when integral, else Long or
     * Double), and maps JS arrays to lists. Without this a script returning
     * e.g. `1 + 1` yields a Rhino `Double` that a caller's `as Int` cast
     * would reject.
     */
    private fun convertResult(raw: Any?, scope: Scriptable): Any? {
        if (raw == null || raw is Undefined) return null
        val converted = RhinoContext.jsToJava(raw, Any::class.java)
        return when (converted) {
            null -> null
            is Boolean -> converted
            is CharSequence -> converted.toString()
            is Number -> coerceNumber(converted)
            is NativeArray -> {
                val len = converted.length.toInt()
                val out = ArrayList<Any?>(len)
                for (i in 0 until len) {
                    out.add(convertResult(converted.get(i, scope), scope))
                }
                out
            }
            else -> converted
        }
    }

    private fun coerceNumber(number: Number): Number {
        val d = number.toDouble()
        if (d.isNaN() || d.isInfinite()) return d
        val l = d.toLong()
        if (l.toDouble() != d) return d
        if (l >= Int.MIN_VALUE && l <= Int.MAX_VALUE) return l.toInt()
        return l
    }
}
