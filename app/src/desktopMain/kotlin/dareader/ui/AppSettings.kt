package dareader.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/** Menu label for "no language filter". */
internal const val ALL_LANGUAGES = "All languages"

/**
 * True when a source declaring [lang] belongs in a list filtered to
 * [preference]: a null preference shows everything, and language-agnostic
 * sources (`all`) stay visible under every preference.
 */
internal fun sourceMatchesLanguage(lang: String, preference: String?): Boolean =
    preference == null ||
        lang.equals(preference, ignoreCase = true) ||
        lang.equals("all", ignoreCase = true)

/**
 * App preferences at `dataDir/settings.json`. The source language filter
 * defaults to the system language so lists start relevant; choosing
 * "All languages" clears it. Missing or corrupt files fall back to the
 * defaults; writes are atomic and never throw.
 */
object AppSettings {
    private val json = Json { ignoreUnknownKeys = true }

    private val _language = MutableStateFlow(initialLanguage())
    val language: StateFlow<String?> = _language.asStateFlow()

    fun setLanguage(language: String?) {
        _language.value = language?.takeIf { it.isNotBlank() }
        persist()
    }

    /** Stored preference when configured once, else the system language. */
    private fun initialLanguage(): String? {
        val (configured, stored) = load()
        return if (configured) stored else systemLanguage()
    }

    private fun systemLanguage(): String? =
        Locale.getDefault().language
            .lowercase(Locale.ROOT)
            .takeIf { it.isNotBlank() && it != "und" }

    private fun load(): Pair<Boolean, String?> =
        runCatching {
            val file = settingsFile()
            if (!Files.isRegularFile(file)) return false to null
            val obj = json.parseToJsonElement(Files.readString(file)).jsonObject
            true to obj["language"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }.getOrDefault(false to null)

    private fun persist() {
        runCatching {
            val file = settingsFile()
            Files.createDirectories(file.parent)
            val value = _language.value?.let { "\"$it\"" } ?: "null"
            val tmp = file.resolveSibling("settings.json.tmp")
            Files.writeString(tmp, """{"language":$value}""")
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

private fun settingsFile() = defaultDataDir().resolve("settings.json")
