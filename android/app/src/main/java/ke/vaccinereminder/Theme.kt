package ke.vaccinereminder

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1D3),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF3D5A80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E3FF),
    onSecondaryContainer = Color(0xFF0E1D33),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6FAF7),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF6FAF7),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5F1),
    surfaceContainer = Color(0xFFEAEFEB),
    surfaceContainerHigh = Color(0xFFE4EAE5),
    surfaceContainerHighest = Color(0xFFDFE4DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DDBB0),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005236),
    onPrimaryContainer = Color(0xFFB4F1D3),
    secondary = Color(0xFFA7C8FF),
    onSecondary = Color(0xFF0A305F),
    secondaryContainer = Color(0xFF254469),
    onSecondaryContainer = Color(0xFFD5E3FF),
    tertiary = Color(0xFFFFB960),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

// Light or dark colours, chosen in Settings. SYSTEM follows the phone's own dark mode setting.
enum class ThemeMode(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    @Composable
    fun isDark(): Boolean = when (this) {
        SYSTEM -> isSystemInDarkTheme()
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

@Immutable
data class StatusColors(val container: Color, val content: Color)

// Colours for a dose status ("given", "due", "overdue", "upcoming"), readable in light and dark mode.
@Composable
fun statusColors(status: String): StatusColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (status) {
        "given" -> if (dark) StatusColors(Color(0xFF0F3D2B), Color(0xFF9FE5C3)) else StatusColors(Color(0xFFD7F0E3), Color(0xFF0B5A3E))
        "due" -> if (dark) StatusColors(Color(0xFF4A3500), Color(0xFFFFD68A)) else StatusColors(Color(0xFFFFE8B3), Color(0xFF7A5200))
        "overdue" -> if (dark) StatusColors(Color(0xFF5C1712), Color(0xFFFFB4AB)) else StatusColors(Color(0xFFF9D4D1), Color(0xFF8C1D18))
        else -> if (dark) StatusColors(Color(0xFF2B3340), Color(0xFFC4CEDC)) else StatusColors(Color(0xFFE3E8EF), Color(0xFF3D4A5C))
    }
}

// Colours for a vaccination verification status: grey before anything is sent, orange while a
// health worker has not reviewed it, green once verified and red when rejected.
@Composable
fun verificationColors(status: String): StatusColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (status) {
        "pending" -> if (dark) StatusColors(Color(0xFF5A2D00), Color(0xFFFFB77C)) else StatusColors(Color(0xFFFFDDC1), Color(0xFF8A3D00))
        "verified" -> statusColors("given")
        "rejected" -> statusColors("overdue")
        else -> statusColors("upcoming")
    }
}
