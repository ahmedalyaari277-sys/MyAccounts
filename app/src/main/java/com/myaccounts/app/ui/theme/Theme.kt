package com.myaccounts.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val White = Color(0xFFFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = White,
    primaryContainer = PrimarySoft,
    onPrimaryContainer = PrimaryDeep,
    secondary = SecondaryDark,
    onSecondary = White,
    secondaryContainer = SecondarySoft,
    onSecondaryContainer = Color(0xFF34270F),
    tertiary = Info,
    onTertiary = White,
    tertiaryContainer = Color(0xFFE4EDF5),
    onTertiaryContainer = Color(0xFF172D40),
    error = Error,
    onError = White,
    errorContainer = Color(0xFFFCE9E7),
    onErrorContainer = Color(0xFF4A1110),
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = BorderStrong,
    outlineVariant = Border
)

private val DarkBackground = Color(0xFF110D0E)
private val DarkSurface = Color(0xFF1B1516)
private val DarkSurfaceVariant = Color(0xFF302527)
private val DarkOnSurface = Color(0xFFF0E7E8)
private val DarkOnSurfaceVariant = Color(0xFFC7BABC)
private val DarkBorder = Color(0xFF493B3E)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF0A5AD),
    onPrimary = Color(0xFF4A0D16),
    primaryContainer = Color(0xFF741A27),
    onPrimaryContainer = Color(0xFFFFDADF),
    secondary = Color(0xFFE1C17A),
    onSecondary = Color(0xFF3A2B0D),
    secondaryContainer = Color(0xFF5A461D),
    onSecondaryContainer = Color(0xFFFFEAC0),
    tertiary = Color(0xFFA9C9E7),
    onTertiary = Color(0xFF10283A),
    tertiaryContainer = Color(0xFF254B68),
    onTertiaryContainer = Color(0xFFD8EAFB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkBorder,
    outlineVariant = DarkBorder
)

// v2.0 type scale. Existing business/data labels keep their semantics;
// the scale only centralizes visual hierarchy.
private val MyAccountsTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val MyAccountsShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MyAccountsTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MyAccountsTypography,
            shapes = MyAccountsShapes,
            content = content
        )
    }
}
