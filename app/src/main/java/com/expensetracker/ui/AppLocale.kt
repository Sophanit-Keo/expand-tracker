package com.expensetracker.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/** Languages the app is translated into. */
enum class AppLanguage(val tag: String?) {
    /** Follow the phone's language. */
    SYSTEM(null),
    ENGLISH("en"),
    KHMER("km"),
}

/**
 * The language the app is shown in. Screens read it from their Configuration; ViewModels that build
 * labels (month and weekday names) combine with [current] so a language switch updates them too.
 */
object AppLocale {
    val current = MutableStateFlow<Locale>(Locale.getDefault())

    fun selected(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: return AppLanguage.SYSTEM
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SYSTEM
    }

    /** Switches the app language; Android recreates the screen in the new language. */
    fun select(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList(),
        )
    }

    fun isKhmer(locale: Locale): Boolean = locale.language == "km"
}
