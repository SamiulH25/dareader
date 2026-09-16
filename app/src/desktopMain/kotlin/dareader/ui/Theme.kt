package dareader.ui

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

/**
 * Material 3 dark-first palette (Tachiyomi/Mihon vocabulary): near-black
 * surfaces with tonal container steps, one periwinkle-blue accent, hairlines
 * on [ColorScheme.outlineVariant].
 */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFA8C7FA),
    onTertiary = Color(0xFF00315F),
    tertiaryContainer = Color(0xFF004A77),
    onTertiaryContainer = Color(0xFFD3E3FD),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0D0D0F),
    surfaceContainerLow = Color(0xFF1A1A1E),
    surfaceContainer = Color(0xFF1D1B20),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF44474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF2F5C8F),
    scrim = Color(0xFF000000),
)

/** Light counterpart: same accent family on cool off-white surfaces. */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF545F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF0B57D0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E3FD),
    onTertiaryContainer = Color(0xFF041E49),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEFF1F7),
    surfaceContainerHigh = Color(0xFFE5E8EF),
    surfaceContainerHighest = Color(0xFFDFE2E9),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC3C6CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFFA8C7FA),
    scrim = Color(0xFF000000),
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
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
        lineHeight = 16.sp,
    ),
)

private val DareaderShapes = Shapes(
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/** Hairline used between list rows and under app bars. */
@Composable
fun hairline() = MaterialTheme.colorScheme.outlineVariant

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
    if (!Files.isRegularFile(file)) return true
    decodeTheme(Files.readString(file)) ?: true
}.getOrDefault(true)

/**
 * File-backed theme mode at `dataDir/theme.json` (manual codec, no parser
 * deps). Dark is the default for fresh installs; corrupt or missing files
 * fall back to dark. Never throws.
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
