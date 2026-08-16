package com.myaccounts.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection

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

private const val NavyPrimary = 0xFF173A5E.toInt()
private const val NavyOnPrimary = 0xFFFFFFFF.toInt()
private const val NavyPrimaryContainer = 0xFFD5E3F4.toInt()
private const val NavyOnPrimaryContainer = 0xFF001C33.toInt()

private const val GreenSecondary = 0xFF2E7D5B.toInt()
private const val GreenOnSecondary = 0xFFFFFFFF.toInt()
private const val GreenSecondaryContainer = 0xFFBCECCF.toInt()
private const val GreenOnSecondaryContainer = 0xFF002113.toInt()

private const val GreenTertiary = 0xFF3F7D4A.toInt()
private const val GreenOnTertiary = 0xFFFFFFFF.toInt()
private const val GreenTertiaryContainer = 0xFFC2EAC1.toInt()
private const val GreenOnTertiaryContainer = 0xFF002109.toInt()

private const val RedError = 0xFFB3261E.toInt()
private const val RedOnError = 0xFFFFFFFF.toInt()
private const val RedErrorContainer = 0xFFF9DEDC.toInt()
private const val RedOnErrorContainer = 0xFF410E0B.toInt()

private const val White = 0xFFFFFFFF.toInt()
private const val LightBackground = 0xFFF8F9FC.toInt()
private const val LightOnBackground = 0xFF191C20.toInt()
private const val LightSurface = 0xFFF8F9FC.toInt()
private const val LightOnSurface = 0xFF191C20.toInt()
private const val LightSurfaceVariant = 0xFFE1E5EC.toInt()
private const val LightOnSurfaceVariant = 0xFF43474E.toInt()

private const val NavyPrimaryDark = 0xFFA8C7E8.toInt()
private const val NavyOnPrimaryDark = 0xFF073353.toInt()
private const val NavyPrimaryContainerDark = 0xFF164A72.toInt()
private const val NavyOnPrimaryContainerDark = 0xFFD5E3F4.toInt()

private const val GreenSecondaryDark = 0xFFA0D6B7.toInt()
private const val GreenOnSecondaryDark = 0xFF083821.toInt()
private const val GreenSecondaryContainerDark = 0xFF155C3E.toInt()
private const val GreenOnSecondaryContainerDark = 0xFFBCECCF.toInt()

private const val GreenTertiaryDark = 0xFFA6D6A4.toInt()
private const val GreenOnTertiaryDark = 0xFF113716.toInt()
private const val GreenTertiaryContainerDark = 0xFF285E2D.toInt()
private const val GreenOnTertiaryContainerDark = 0xFFC2EAC1.toInt()

private const val RedErrorDark = 0xFFFFB4AB.toInt()
private const val RedOnErrorDark = 0xFF690005.toInt()
private const val RedErrorContainerDark = 0xFF93000A.toInt()
private const val RedOnErrorContainerDark = 0xFFFFDAD6.toInt()

private const val DarkBackground = 0xFF101418.toInt()
private const val DarkOnBackground = 0xFFE1E2E6.toInt()
private const val DarkSurface = 0xFF101418.toInt()
private const val DarkOnSurface = 0xFFE1E2E6.toInt()
private const val DarkSurfaceVariant = 0xFF43474E.toInt()
private const val DarkOnSurfaceVariant = 0xFFC3C7CF.toInt()
