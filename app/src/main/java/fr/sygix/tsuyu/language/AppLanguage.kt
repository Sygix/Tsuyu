/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import java.util.Locale

enum class AppLanguage(val tag: String, val nativeName: String) {
    GERMAN("de", "Deutsch"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    ITALIAN("it", "Italiano");

    companion object {
        val fallback = ENGLISH
        fun of(locale: Locale): AppLanguage? = entries.firstOrNull { it.tag == locale.language }
        fun resolveLocale(locales: List<Locale>): Locale? = locales.firstOrNull { of(it) != null }
        fun resolve(locales: List<Locale>): AppLanguage? = resolveLocale(locales)?.let(::of)
    }
}

data class LanguageState(val selected: AppLanguage? = null, val system: AppLanguage? = null) {
    val effective: AppLanguage get() = selected ?: system ?: AppLanguage.fallback
}
