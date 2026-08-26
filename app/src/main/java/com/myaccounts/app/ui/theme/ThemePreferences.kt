package com.myaccounts.app.ui.theme

import android.content.Context

/** Stores the user's appearance choice independently from security settings. */
class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getAppearance(): AppearanceMode =
        when (preferences.getString(KEY_APPEARANCE, AppearanceMode.SYSTEM.name)) {
            AppearanceMode.LIGHT.name -> AppearanceMode.LIGHT
            AppearanceMode.DARK.name -> AppearanceMode.DARK
            else -> AppearanceMode.SYSTEM
        }

    fun setAppearance(mode: AppearanceMode) {
        preferences.edit().putString(KEY_APPEARANCE, mode.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "myaccounts_app_theme"
        private const val KEY_APPEARANCE = "appearance"
    }
}

enum class AppearanceMode {
    LIGHT,
    DARK,
    SYSTEM
}
