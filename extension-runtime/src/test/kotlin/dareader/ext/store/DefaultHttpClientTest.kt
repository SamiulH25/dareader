package dareader.ext.store

import kotlin.test.Test
import kotlin.test.assertSame

class DefaultHttpClientTest {

    @Test
    fun `default client is shared per process`() {
        assertSame(defaultHttpClient(), defaultHttpClient())
    }
}
