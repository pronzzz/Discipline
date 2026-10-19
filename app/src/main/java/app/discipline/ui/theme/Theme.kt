package app.discipline.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * One UI-inspired tokens: neutral grey canvas with white rounded containers in light mode,
 * true black canvas with dark-grey containers in dark mode, and a vivid blue accent.
 */
@Immutable
data class OneUiColors(
    val background: Color,
    val container: Color,
    val containerHigh: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentContainer: Color,
    val danger: Color,
    val success: Color,
    val calories: Color,
    val carbs: Color,
    val protein: Color,
    val fat: Color,
    val water: Color,
    val steps: Color,
    val exercise: Color,
    val weight: Color,
    val track: Color,
    val isDark: Boolean,
)

private val Light = OneUiColors(
    background = Color(0xFFF4F4F6),
    container = Color(0xFFFFFFFF),
    containerHigh = Color(0xFFECECEF),
    divider = Color(0xFFE6E6E9),
    textPrimary = Color(0xFF111114),
    textSecondary = Color(0xFF6C6C72),
    textTertiary = Color(0xFF9A9AA0),
    accent = Color(0xFF0A7CFF),
    accentContainer = Color(0xFFE3EFFF),
    danger = Color(0xFFE5393B),
    success = Color(0xFF22A355),
    calories = Color(0xFF0A7CFF),
    carbs = Color(0xFF14B8A6),
    protein = Color(0xFFF59E0B),
    fat = Color(0xFF8B5CF6),
    water = Color(0xFF2E9BFF),
    steps = Color(0xFF22B455),
    exercise = Color(0xFFFF6A3D),
    weight = Color(0xFFEC4899),
    track = Color(0xFFEBEBEF),
    isDark = false,
)

private val Dark = OneUiColors(
    background = Color(0xFF000000),
    container = Color(0xFF171719),
    containerHigh = Color(0xFF242427),
    divider = Color(0xFF2A2A2E),
    textPrimary = Color(0xFFF7F7FA),
    textSecondary = Color(0xFF9C9CA3),
    textTertiary = Color(0xFF6B6B72),
    accent = Color(0xFF3D95FF),
    accentContainer = Color(0xFF14284A),
    danger = Color(0xFFFF5A5C),
    success = Color(0xFF34C46B),
    calories = Color(0xFF3D95FF),
    carbs = Color(0xFF2DD4BF),
    protein = Color(0xFFFBBF24),
    fat = Color(0xFFA78BFA),
    water = Color(0xFF52ADFF),
    steps = Color(0xFF3DD472),
    exercise = Color(0xFFFF7F57),
    weight = Color(0xFFF472B6),
    track = Color(0xFF2A2A2E),
    isDark = true,
)

val LocalOneUi = staticCompositionLocalOf { Light }

object OneUi {
    val colors: OneUiColors @Composable get() = LocalOneUi.current
    val ContainerShape = RoundedCornerShape(26.dp)
    val InnerShape = RoundedCornerShape(18.dp)
    val PillShape = RoundedCornerShape(50)
    val ScreenPadding = 12.dp
}

private val Sans = FontFamily.SansSerif

private val OneUiTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
)

@Composable
fun DisciplineTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = Color.White, primaryContainer = c.accentContainer, onPrimaryContainer = c.accent,
            secondary = c.accent, background = c.background, onBackground = c.textPrimary,
            surface = c.container, onSurface = c.textPrimary, surfaceVariant = c.containerHigh, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.container, surfaceContainerHigh = c.containerHigh, surfaceContainerHighest = c.containerHigh,
            surfaceContainerLow = c.container, surfaceContainerLowest = c.background,
            outline = c.divider, outlineVariant = c.divider, error = c.danger, inverseSurface = Color(0xFFE9E9EE), inverseOnSurface = Color(0xFF111114),
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = Color.White, primaryContainer = c.accentContainer, onPrimaryContainer = c.accent,
            secondary = c.accent, background = c.background, onBackground = c.textPrimary,
            surface = c.container, onSurface = c.textPrimary, surfaceVariant = c.containerHigh, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.container, surfaceContainerHigh = c.container, surfaceContainerHighest = c.containerHigh,
            surfaceContainerLow = c.container, surfaceContainerLowest = c.background,
            outline = c.divider, outlineVariant = c.divider, error = c.danger, inverseSurface = Color(0xFF2A2A2E), inverseOnSurface = Color.White,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalOneUi provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OneUiTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(26.dp),
                extraLarge = RoundedCornerShape(30.dp),
            ),
            content = content,
        )
    }
}
