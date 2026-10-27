package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The languages offered in Pengaturan → Bahasa.
 *
 * **Indonesia is the default and stays exactly as the app ships today** —
 * its strings are the hardcoded originals (mixed Indonesian/English is the
 * product voice, on purpose). Every other language is served by the
 * in-app dictionary (`ui/i18n/Strings.kt`) and falls back to the Indonesian
 * original wherever a key is not translated yet, so switching language never
 * breaks a screen.
 */
enum class AppLanguage(
    val code: String,
    val endonym: String,
    val labelId: String
) {
    /** Default — the app's current Indonesian copy, untouched. */
    ID("id", "Bahasa Indonesia", "Indonesia"),
    EN("en", "English", "Inggris"),
    MS("ms", "Bahasa Melayu", "Melayu"),
    JAWA("jv", "Basa Jawa", "Jawa"),
    SUNDA("su", "Basa Sunda", "Sunda");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ID
    }
}

/**
 * Persists the chosen app language and exposes it as a [StateFlow] so the
 * whole Compose tree recomposes the moment the pick changes. Kept in plain
 * SharedPreferences: the value is needed before any Compose state exists.
 */
class LanguageStore(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(load())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private fun load(): AppLanguage = AppLanguage.fromCode(prefs.getString(KEY, AppLanguage.ID.code))

    fun set(language: AppLanguage) {
        prefs.edit().putString(KEY, language.code).apply()
        _language.value = language
    }

    companion object {
        private const val NAME = "noxa_language"
        private const val KEY = "language"
    }
}
