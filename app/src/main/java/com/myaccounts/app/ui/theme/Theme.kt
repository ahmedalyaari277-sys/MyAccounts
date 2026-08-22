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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PrimaryBlue = Color(0xFF1565C0)
private val PrimaryBlueDark = Color(0xFF5B9CFF)
private val White = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFD9E8FF)
private val OnPrimaryContainer = Color(0xFF001B3D)

private val GreenSecondary = Color(0xFF2E7D5B)
private val GreenSecondaryContainer = Color(0xFFBCECCF)
private val GreenOnSecondaryContainer = Color(0xFF002113)
private val GreenTertiary = Color(0xFF3F7D4A)
private val GreenTertiaryContainer = Color(0xFFC2EAC1)
private val GreenOnTertiaryContainer = Color(0xFF002109)

private val RedError = Color(0xFFB3261E)
private val RedErrorContainer = Color(0xFFF9DEDC)
private val RedOnErrorContainer = Color(0xFF410E0B)

private val LightBackground = Color(0xFFF7F9FC)
private val LightOnBackground = Color(0xFF172033)
private val LightSurface = Color(0xFFFFFFFF)
private val LightOnSurface = Color(0xFF172033)
private val LightSurfaceVariant = Color(0xFFE9EEF5)
private val LightOnSurfaceVariant = Color(0xFF667085)

private val OnPrimaryDark = Color(0xFF00315F)
private val PrimaryContainerDark = Color(0xFF004A8F)
private val OnPrimaryContainerDark = Color(0xFFD9E8FF)

private val GreenSecondaryDark = Color(0xFFA0D6B7)
private val GreenOnSecondaryDark = Color(0xFF083821)
private val GreenSecondaryContainerDark = Color(0xFF155C3E)
private val GreenOnSecondaryContainerDark = Color(0xFFBCECCF)
private val GreenTertiaryDark = Color(0xFFA6D6A4)
private val GreenOnTertiaryDark = Color(0xFF113716)
private val GreenTertiaryContainerDark = Color(0xFF285E2D)
private val GreenOnTertiaryContainerDark = Color(0xFFC2EAC1)

private val RedErrorDark = Color(0xFFFFB4AB)
private val RedOnErrorDark = Color(0xFF690005)
private val RedErrorContainerDark = Color(0xFF93000A)
private val RedOnErrorContainerDark = Color(0xFFFFDAD6)

private val DarkBackground = Color(0xFF101418)
private val DarkOnBackground = Color(0xFFF1F5F9)
private val DarkSurface = Color(0xFF181D23)
private val DarkOnSurface = Color(0xFFF1F5F9)
private val DarkSurfaceVariant = Color(0xFF2B333D)
private val DarkOnSurfaceVariant = Color(0xFFAAB4C0)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = GreenSecondary,
    onSecondary = White,
    secondaryContainer = GreenSecondaryContainer,
    onSecondaryContainer = GreenOnSecondaryContainer,
    tertiary = GreenTertiary,
    onTertiary = White,
    tertiaryContainer = GreenTertiaryContainer,
    onTertiaryContainer = GreenOnTertiaryContainer,
    error = RedError,
    onError = White,
    errorContainer = RedErrorContainer,
    onErrorContainer = RedOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = GreenSecondaryDark,
    onSecondary = GreenOnSecondaryDark,
    secondaryContainer = GreenSecondaryContainerDark,
    onSecondaryContainer = GreenOnSecondaryContainerDark,
    tertiary = GreenTertiaryDark,
    onTertiary = GreenOnTertiaryDark,
    tertiaryContainer = GreenTertiaryContainerDark,
    onTertiaryContainer = GreenOnTertiaryContainerDark,
    error = RedErrorDark,
    onError = RedOnErrorDark,
    errorContainer = RedErrorContainerDark,
    onErrorContainer = RedOnErrorContainerDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

private val MyAccountsTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
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
        androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MyAccountsTypography,
            shapes = MyAccountsShapes,
            content = content
        )
    }
}
