package dareader.ext.di

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DareaderGraphTest {

    @Test
    fun `racing ensureCore calls yield a single NetworkHelper instance`() {
        val resolved =
            runBlocking {
                (1..16)
                    .map {
                        async(Dispatchers.Default) {
                            DareaderGraph.ensureCore()
                            DareaderGraph.get(NetworkHelper::class.java)
                        }
                    }.awaitAll()
            }

        assertTrue(
            resolved.all { it === resolved.first() },
            "ensureCore built ${resolved.distinct().size} NetworkHelper instances",
        )
    }
}
