package eu.kanade.tachiyomi.network

import dareader.ext.android.StoreContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression coverage for cookie persistence across process restarts: a fresh
 * store instance must reload what a previous instance persisted.
 */
class PersistentCookieStoreTest {
    private val context = StoreContext()
    private val url = "https://example.com/".toHttpUrl()

    private fun clearStore() {
        context.getSharedPreferences("cookie_store", 0).edit().clear().commit()
    }

    @BeforeTest
    fun reset() = clearStore()

    @AfterTest
    fun cleanup() = clearStore()

    private fun persistentCookie(
        name: String,
        value: String,
        domain: String,
    ): Cookie =
        Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path("/")
            .expiresAt(System.currentTimeMillis() + 60_000)
            .build()

    @Test
    fun `dotted domain cookies survive a store reload`() {
        PersistentCookieStore(context).addAll(url, listOf(persistentCookie("session", "abc123", "example.com")))

        val restored = PersistentCookieStore(context).get(url)

        assertEquals(listOf("session" to "abc123"), restored.map { it.name to it.value })
    }

    @Test
    fun `dot-less host cookies survive a store reload`() {
        val localhost = "http://localhost/".toHttpUrl()
        PersistentCookieStore(context).addAll(localhost, listOf(persistentCookie("k", "v", "localhost")))

        val restored = PersistentCookieStore(context).get(localhost)

        assertEquals(listOf("k"), restored.map { it.name })
    }
}
