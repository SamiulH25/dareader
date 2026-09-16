package dareader.ext.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoreIndexTest {

    @Test
    fun parsesRepoObjectArray() {
        val repos = parseRepos("""[{"name":"Keiyoushi","url":"https://example.com/index.json"}]""")
        assertEquals(listOf(RepoEntry("Keiyoushi", "https://example.com/index.json")), repos)
    }

    @Test
    fun parsesBareUrlArray() {
        val repos = parseRepos("""["https://example.com/index.json"]""")
        assertEquals(listOf(RepoEntry("", "https://example.com/index.json")), repos)
    }

    @Test
    fun parsesWrappedReposObject() {
        val repos = parseRepos("""{"repos":[{"name":"K","url":"https://example.com/i.json"}]}""")
        assertEquals(listOf(RepoEntry("K", "https://example.com/i.json")), repos)
    }

    private fun extension(jarUrl: String?) = NetworkExtensionStore.Extension(
        name = "Test",
        packageName = "com.example.test",
        resources = NetworkExtensionStore.Resources(
            apkUrl = "https://example.com/test.apk",
            jarUrl = jarUrl,
        ),
    )

    @Test
    fun directJarUrlPreferredWhenPresent() {
        assertEquals("https://example.com/test.jar", extension("https://example.com/test.jar").directJarUrl())
        assertNull(extension(null).directJarUrl())
        assertNull(extension("  ").directJarUrl())
    }
}
