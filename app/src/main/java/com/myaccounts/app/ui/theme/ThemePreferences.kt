package com.myaccounts.app.ui.theme

import android.content.Context
import android.content.SharedPreferences

/** Stores only the visual appearance preference; it does not affect application data. */
class ThemePreferences(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getMode(): ThemeMode = when (preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)) {
        ThemeMode.LIGHT.name -> ThemeMode.LIGHT
        ThemeMode.DARK.name -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    fun setMode(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "myaccounts_theme_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
