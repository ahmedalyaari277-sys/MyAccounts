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
     * Marks that the current process has an authenticated app session.
     * This state is deliberately process-local and is never persisted, so a
     * new process must authenticate again when protection is enabled.
     */
    fun markSessionUnlocked() {
        sessionUnlocked = true
    }

    fun isSessionUnlocked(): Boolean = sessionUnlocked

    /**
     * Marks that an external activity (for example Android's document picker,
     * camera, or a file viewer) was launched from the app. The flag is
     * process-local, but shared by all AppSecurityManager instances in the
     * same process. It is deliberately not persisted, so a process restart
     * can never use it to bypass the app lock.
     */
    fun markExternalActivityPending() {
        externalActivityPending = true
    }

    fun clearExternalActivityPending() {
        externalActivityPending = false
    }

    fun isExternalActivityPending(): Boolean = externalActivityPending

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

        @Volatile
        private var sessionUnlocked = false

        @Volatile
        private var externalActivityPending = false
    }
}
