package dareader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private data class PersistedWindow(
    val width: Float,
    val height: Float,
    val x: Float?,
    val y: Float?,
)
private fun num(key: String, text: String): Float? =
    Regex("\"$key\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").find(text)?.groupValues?.get(1)?.toFloatOrNull()

private fun encodeWindow(p: PersistedWindow): String = buildString {
    append("{\"width\":${p.width},\"height\":${p.height},")
    append("\"x\":${p.x?.toString() ?: "null"},\"y\":${p.y?.toString() ?: "null"}}")
}

private fun decodeWindow(text: String): PersistedWindow? {
    val width = num("width", text) ?: return null
    val height = num("height", text) ?: return null
    if (width <= 0 || height <= 0) return null
    return PersistedWindow(width, height, num("x", text), num("y", text))
}

private fun windowFile(): Path = defaultDataDir().resolve("window.json")

private fun loadPersisted(): PersistedWindow? = runCatching {
    val file = windowFile()
    if (!Files.isRegularFile(file)) return null
    decodeWindow(Files.readString(file))
}.getOrNull()

/**
 * Window state with a 1200x800 default, restoring persisted size/position
 * from `dataDir/window.json` when present. Corrupt files are ignored.
 */
@Composable
fun rememberDareaderWindowState(): WindowState {
    val persisted = remember { loadPersisted() }
    val size = if (persisted != null && persisted.width > 0 && persisted.height > 0) {
        DpSize(persisted.width.dp, persisted.height.dp)
    } else {
        DpSize(1200.dp, 800.dp)
    }
    val position = if (persisted?.x != null && persisted.y != null) {
        WindowPosition(persisted.x.dp, persisted.y.dp)
    } else {
        WindowPosition.PlatformDefault
    }
    return rememberWindowState(size = size, position = position)
}

/**
 * Persists [state] size/position to `dataDir/window.json` (atomic tmp+move).
 * Never throws; call on window close.
 */
fun saveWindowState(state: WindowState) {
    runCatching {
        val pos = state.position
        val (x, y) = when (pos) {
            is WindowPosition.Absolute -> pos.x.value to pos.y.value
            else -> null to null
        }
        val persisted = PersistedWindow(
            width = state.size.width.value,
            height = state.size.height.value,
            x = x,
            y = y,
        )
        val file = windowFile()
        Files.createDirectories(file.parent)
        val tmp = file.resolveSibling("window.json.tmp")
        Files.writeString(tmp, encodeWindow(persisted))
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
