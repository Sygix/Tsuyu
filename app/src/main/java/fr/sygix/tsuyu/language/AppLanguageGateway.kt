/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

interface AppLanguageGateway {
    suspend fun current(): LanguageState
    suspend fun select(language: AppLanguage?)
}

class AndroidAppLanguageGateway(
    context: Context,
    private val store: LanguageStorage = LanguageStore(context),
) : AppLanguageGateway {
    private val platform = if (Build.VERSION.SDK_INT >= 33) PlatformLanguage(context) else null
    private val mutex = Mutex()

    override suspend fun current(): LanguageState = withTimeout(5_000) {
        mutex.withLock {
            migrate()
            val selected = if (platform == null) store.read().selected else platform.selected()
            val locales = platform?.system() ?: Resources.getSystem().configuration.locales
            LanguageState(selected, AppLanguage.resolve(List(locales.size()) { locales[it] }))
        }
    }

    override suspend fun select(language: AppLanguage?) = withTimeout(5_000) {
        mutex.withLock {
            migrate()
            if (platform == null) store.select(language) else platform.select(language)
        }
    }

    private suspend fun migrate() {
        if (platform == null) return
        val saved = store.read()
        if (saved.migrated) return
        if (platform.empty() && saved.selected != null) platform.select(saved.selected)
        store.migrated()
    }
}

@android.annotation.TargetApi(33)
private class PlatformLanguage(context: Context) {
    private val manager = requireNotNull(context.getSystemService(android.app.LocaleManager::class.java))
    suspend fun empty() = barrier.read { manager.applicationLocales.isEmpty }
    suspend fun selected() = barrier.read { manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.let(AppLanguage::of) }
    suspend fun system(): LocaleList = barrier.read { manager.systemLocales }
    suspend fun select(language: AppLanguage?) = barrier.write {
        manager.applicationLocales = language?.let { LocaleList.forLanguageTags(it.tag) }
            ?: LocaleList.getEmptyLocaleList()
    }
    companion object { private val barrier = LanguageWriteBarrier() }
}
