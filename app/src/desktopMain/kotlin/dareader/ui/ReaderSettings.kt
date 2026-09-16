package dareader.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** How the reader lays pages out. */
enum class ReaderMode(val label: String) {
    /** Continuous vertical strip, pages fit the width. */
    WEBTOON("Webtoon"),

    /** One page per screen, the whole page visible, swipe horizontally. */
    PAGED("Paged"),

    /** One page per screen, scaled to fill the height, swipe horizontally. */
    FIT("Fit height"),
}

/**
 * Reader preferences at `dataDir/reader.json`. Missing or corrupt files fall
 * back to the defaults (webtoon, auto-advance on); writes are atomic and
 * never throw. Manual JSON, same no-surprises contract as [ThemeMode].
 */
object ReaderSettings {
    private val json = Json { ignoreUnknownKeys = true }

    private val _mode = MutableStateFlow(ReaderMode.WEBTOON)
    val mode: StateFlow<ReaderMode> = _mode.asStateFlow()

    private val _autoAdvance = MutableStateFlow(true)
    val autoAdvance: StateFlow<Boolean> = _autoAdvance.asStateFlow()

    init {
        load()?.let { (mode, autoAdvance) ->
            _mode.value = mode
            _autoAdvance.value = autoAdvance
        }
    }

    fun setMode(mode: ReaderMode) {
        _mode.value = mode
        persist()
    }

    fun setAutoAdvance(enabled: Boolean) {
        _autoAdvance.value = enabled
        persist()
    }

    private fun readerFile() = defaultDataDir().resolve("reader.json")

    private fun load(): Pair<ReaderMode, Boolean>? =
        runCatching {
            val file = readerFile()
            if (!Files.isRegularFile(file)) return null
            val obj = json.parseToJsonElement(Files.readString(file)).jsonObject
            val mode =
                obj["mode"]?.jsonPrimitive?.contentOrNull
                    ?.let { name -> ReaderMode.entries.firstOrNull { it.name == name } }
                    ?: ReaderMode.WEBTOON
            val autoAdvance = obj["autoAdvance"]?.jsonPrimitive?.booleanOrNull ?: true
            mode to autoAdvance
        }.getOrNull()

    private fun persist() {
        runCatching {
            val file = readerFile()
            Files.createDirectories(file.parent)
            val text = """{"mode":"${_mode.value.name}","autoAdvance":${_autoAdvance.value}}"""
            val tmp = file.resolveSibling("reader.json.tmp")
            Files.writeString(tmp, text)
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
