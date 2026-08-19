package com.myaccounts.app.security

import android.content.Context
import java.security.MessageDigest

class AppSecurityManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun isProtectionEnabled(): Boolean =
        preferences.getBoolean(KEY_PROTECTION_ENABLED, false)

    fun setProtectionEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
    }

    fun hasPin(): Boolean =
        !preferences.getString(KEY_PIN_HASH, null).isNullOrBlank()

    fun hasRecoveryEmail(): Boolean =
        !preferences.getString(KEY_RECOVERY_EMAIL, null).isNullOrBlank()

    fun saveCredentials(pin: String, email: String) {
        preferences.edit()
            .putString(KEY_PIN_HASH, hash(pin))
            .putString(KEY_RECOVERY_EMAIL, email.trim().lowercase())
            .apply()
    }

    fun verifyPin(pin: String): Boolean =
        hasPin() && preferences.getString(KEY_PIN_HASH, null) == hash(pin)

    fun verifyRecoveryEmail(email: String): Boolean =
        hasRecoveryEmail() &&
            preferences.getString(KEY_RECOVERY_EMAIL, null) == email.trim().lowercase()

    fun resetPin(pin: String) {
        preferences.edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun recoveryEmail(): String =
        preferences.getString(KEY_RECOVERY_EMAIL, "") ?: ""

    /**
     * Marks that an external activity (for example Android's document picker)
     * was launched from the app. Returning from that activity must not be
     * treated as leaving the app and must not trigger the app lock.
     */
    fun markExternalActivityPending() {
        preferences.edit().putBoolean(KEY_EXTERNAL_ACTIVITY_PENDING, true).apply()
    }

    fun clearExternalActivityPending() {
        preferences.edit().putBoolean(KEY_EXTERNAL_ACTIVITY_PENDING, false).apply()
    }

    fun isExternalActivityPending(): Boolean =
        preferences.getBoolean(KEY_EXTERNAL_ACTIVITY_PENDING, false)

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS_NAME = "myaccounts_app_security"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_RECOVERY_EMAIL = "recovery_email"
        private const val KEY_EXTERNAL_ACTIVITY_PENDING = "external_activity_pending"
    }
}
