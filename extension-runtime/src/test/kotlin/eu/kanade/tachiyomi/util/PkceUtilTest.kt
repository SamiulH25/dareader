package eu.kanade.tachiyomi.util

import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PkceUtilTest {

    @Test
    fun `generated verifier is RFC 7636 shaped`() {
        val verifier = PkceUtil.generateCodeVerifier()

        assertTrue(verifier.length in 43..128, "unexpected verifier length ${verifier.length}")
        assertTrue(
            verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' },
            "verifier must stay base64url: $verifier",
        )
    }

    @Test
    fun `S256 challenge is the base64url SHA-256 of the verifier`() {
        val codes = PkceUtil.generateS256Codes()

        val expected =
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(codes.codeVerifier.toByteArray()))

        assertEquals(expected, codes.codeChallenge)
    }
}
