package dareader.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val LightPaper = Color(0xFFF5F4F0)
private val LightSurface = Color(0xFFFFFFFF)
private val LightInk = Color(0xFF191817)
private val Vermillion = Color(0xFFC22E1F)

private val DarkInk = Color(0xFF141311)
private val DarkSurface = Color(0xFF201F1E)
private val DarkPaper = Color(0xFFF2EFE9)
private val Ember = Color(0xFFFF6A54)

private val LightScheme = lightColorScheme(
    primary = Vermillion,
    onPrimary = Color.White,
    secondary = Vermillion,
    background = LightPaper,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
)

private val DarkScheme = darkColorScheme(
    primary = Ember,
    onPrimary = Color(0xFF1E0E0B),
    secondary = Ember,
    background = DarkInk,
    onBackground = DarkPaper,
    surface = DarkSurface,
    onSurface = DarkPaper,
)

private val DareaderTypography = Typography().copy(
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 20.sp,
    ),
)

private val DareaderShapes = Shapes(
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * Quiet-chrome manga-reader theme: neutral paper/ink surfaces in both schemes,
 * covers carry the color, one editorial accent (vermillion by light, ember by
 * dark).
 */
@Composable
fun DareaderTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = DareaderTypography,
        shapes = DareaderShapes,
        content = content,
    )
}

private fun themeFile() = defaultDataDir().resolve("theme.json")

private fun encodeTheme(dark: Boolean): String = "{\"dark\":$dark}"

private fun decodeTheme(text: String): Boolean? =
    Regex("\"dark\"\\s*:\\s*(true|false)").find(text)?.groupValues?.get(1)?.toBooleanStrictOrNull()

private fun loadTheme(): Boolean = runCatching {
    val file = themeFile()
    if (!Files.isRegularFile(file)) return false
    decodeTheme(Files.readString(file)) ?: false
}.getOrDefault(false)

/**
 * File-backed theme mode at `dataDir/theme.json` (manual codec, no parser
 * deps). Corrupt or missing files fall back to light. Never throws.
 */
object ThemeMode {
    private val _dark = MutableStateFlow(loadTheme())
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    fun toggle() = set(!_dark.value)

    fun set(dark: Boolean) {
        _dark.value = dark
        runCatching {
            val file = themeFile()
            Files.createDirectories(file.parent)
            val tmp = file.resolveSibling("theme.json.tmp")
            Files.writeString(tmp, encodeTheme(dark))
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: UnsupportedOperationException) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
