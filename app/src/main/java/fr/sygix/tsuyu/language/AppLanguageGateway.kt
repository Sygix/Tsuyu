/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

interface AppLanguageGateway {
    fun current(): LanguageState
    fun select(language: AppLanguage?)
}

class AndroidAppLanguageGateway(context: Context) : AppLanguageGateway {
    private val manager = context.getSystemService(LocaleManager::class.java)

    override fun current() = LanguageState(
        selected = manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.let(AppLanguage::of),
        system = manager.systemLocales.let { locales -> AppLanguage.resolve(List(locales.size()) { locales[it] }) },
    )

    override fun select(language: AppLanguage?) {
        manager.applicationLocales = language?.let { LocaleList.forLanguageTags(it.tag) } ?: LocaleList.getEmptyLocaleList()
    }
}
