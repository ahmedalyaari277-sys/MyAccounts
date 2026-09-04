package com.myaccounts.app.data.currency

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Central source of truth for enabled currencies and their display names. */
object CurrencyCatalog {
    data class Definition(val code: String, val name: String)

    private const val PREFS_NAME = "myaccounts_currency_catalog"
    private const val KEY_DEFINITIONS = "definitions"
    private const val KEY_DEFAULT = "default_currency"
    private const val SEPARATOR = "|"

    private val builtIns = listOf(
        Definition("YER", "الريال اليمني"),
        Definition("SAR", "الريال السعودي"),
        Definition("USD", "الدولار الأمريكي")
    )

    private var initialized = false
    private lateinit var preferences: android.content.SharedPreferences

    var definitions: List<Definition> by mutableStateOf(builtIns)
        private set

    val codes: List<String> get() = definitions.map { it.code }

    fun initialize(context: Context) {
        if (initialized) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        definitions = readDefinitions()
        initialized = true
    }

    fun name(code: String): String = definitions.firstOrNull { it.code == code }?.name ?: code

    fun defaultCode(): String = preferences.getString(KEY_DEFAULT, builtIns.first().code) ?: builtIns.first().code

    fun setDefault(code: String) {
        if (definitions.any { it.code == code }) preferences.edit().putString(KEY_DEFAULT, code).apply()
    }

    fun add(code: String, name: String): Boolean {
        checkInitialized()
        val normalizedCode = code.trim().uppercase()
        val normalizedName = name.trim()
        if (!normalizedCode.matches(Regex("[A-Z0-9]{3,6}")) || normalizedName.isBlank()) return false
        if (definitions.any { it.code == normalizedCode }) return false
        definitions = definitions + Definition(normalizedCode, normalizedName)
        persist()
        return true
    }

    private fun readDefinitions(): List<Definition> {
        val raw = preferences.getString(KEY_DEFINITIONS, null) ?: return builtIns
        val stored = raw.split(SEPARATOR).mapNotNull { item ->
            val parts = item.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) Definition(parts[0], parts[1]) else null
        }
        return (builtIns + stored).distinctBy { it.code }
    }

    private fun persist() {
        val custom = definitions.filterNot { builtIns.any { builtIn -> builtIn.code == it.code } }
        val raw = custom.joinToString(SEPARATOR) { "${it.code}:${it.name}" }
        preferences.edit().putString(KEY_DEFINITIONS, raw).apply()
    }

    private fun checkInitialized() {
        check(initialized) { "CurrencyCatalog must be initialized before use" }
    }
}
