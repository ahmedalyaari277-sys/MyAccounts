package com.myaccounts.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection

private val NavyPrimary = Color(0xFF173A5E)
private val White = Color(0xFFFFFFFF)
private val NavyPrimaryContainer = Color(0xFFD5E3F4)
private val NavyOnPrimaryContainer = Color(0xFF001C33)

private val GreenSecondary = Color(0xFF2E7D5B)
private val GreenSecondaryContainer = Color(0xFFBCECCF)
private val GreenOnSecondaryContainer = Color(0xFF002113)
private val GreenTertiary = Color(0xFF3F7D4A)
private val GreenTertiaryContainer = Color(0xFFC2EAC1)
private val GreenOnTertiaryContainer = Color(0xFF002109)

private val RedError = Color(0xFFB3261E)
private val RedErrorContainer = Color(0xFFF9DEDC)
private val RedOnErrorContainer = Color(0xFF410E0B)

private val LightBackground = Color(0xFFF8F9FC)
private val LightOnBackground = Color(0xFF191C20)
private val LightSurface = Color(0xFFF8F9FC)
private val LightOnSurface = Color(0xFF191C20)
private val LightSurfaceVariant = Color(0xFFE1E5EC)
private val LightOnSurfaceVariant = Color(0xFF43474E)

private val NavyPrimaryDark = Color(0xFFA8C7E8)
private val NavyOnPrimaryDark = Color(0xFF073353)
private val NavyPrimaryContainerDark = Color(0xFF164A72)
private val NavyOnPrimaryContainerDark = Color(0xFFD5E3F4)

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
private val DarkOnBackground = Color(0xFFE1E2E6)
private val DarkSurface = Color(0xFF101418)
private val DarkOnSurface = Color(0xFFE1E2E6)
private val DarkSurfaceVariant = Color(0xFF43474E)
private val DarkOnSurfaceVariant = Color(0xFFC3C7CF)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = White,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = NavyOnPrimaryContainer,
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
    primary = NavyPrimaryDark,
    onPrimary = NavyOnPrimaryDark,
    primaryContainer = NavyPrimaryContainerDark,
    onPrimaryContainer = NavyOnPrimaryContainerDark,
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

@Composable
fun MyAccountsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}
