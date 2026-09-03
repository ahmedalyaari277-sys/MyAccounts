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
    primaryContainer = Color(0xFFF5D7D9),
    onPrimaryContainer = Color(0xFF3B090C),
    secondary = Secondary,
    onSecondary = Color(0xFF2A210E),
    secondaryContainer = Color(0xFFF0E2BE),
    onSecondaryContainer = Color(0xFF241B08),
    tertiary = Info,
    onTertiary = White,
    tertiaryContainer = Color(0xFFD9E7F8),
    onTertiaryContainer = Color(0xFF071A2B),
    error = Error,
    onError = White,
    errorContainer = Color(0xFFFDE8E7),
    onErrorContainer = Color(0xFF410E0B),
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Border,
    outlineVariant = Border
)

private val DarkBackground = Color(0xFF121316)
private val DarkSurface = Color(0xFF1A1B1F)
private val DarkSurfaceVariant = Color(0xFF292B31)
private val DarkOnSurface = Color(0xFFE7E7EA)
private val DarkOnSurfaceVariant = Color(0xFFB8BBC3)
private val DarkBorder = Color(0xFF3A3C43)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8A0A4),
    onPrimary = Color(0xFF4A0B0F),
    primaryContainer = Color(0xFF70171C),
    onPrimaryContainer = Color(0xFFF5D7D9),
    secondary = Color(0xFFE2C98E),
    onSecondary = Color(0xFF3A2F15),
    secondaryContainer = Color(0xFF5B491F),
    onSecondaryContainer = Color(0xFFF0E2BE),
    tertiary = Color(0xFF9FC4EA),
    onTertiary = Color(0xFF0A2238),
    tertiaryContainer = Color(0xFF1E486B),
    onTertiaryContainer = Color(0xFFD9E7F8),
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

private val MyAccountsTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val MyAccountsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
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
