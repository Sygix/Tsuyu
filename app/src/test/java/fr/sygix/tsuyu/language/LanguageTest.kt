/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import fr.sygix.tsuyu.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageTest {
    @Test fun resolvesTheFirstSupportedSystemLocaleAndFallsBackToEnglish() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.resolve(listOf(Locale.JAPAN, Locale.CANADA_FRENCH, Locale.GERMANY)))
        assertEquals(AppLanguage.ENGLISH, LanguageState(system = AppLanguage.resolve(listOf(Locale.JAPAN))).effective)
        assertEquals(AppLanguage.ENGLISH, LanguageState().effective)
        assertEquals(AppLanguage.ITALIAN, LanguageState(AppLanguage.ITALIAN, AppLanguage.FRENCH).effective)
        assertEquals(AppLanguage.SPANISH, AppLanguage.of(Locale.forLanguageTag("es-MX")))
    }

    @Test fun nativePreferencePersistsAcrossGatewayInstancesAndCanReturnToSystem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(LocaleManager::class.java)
        val system = manager.systemLocales.toLanguageTags()
        val gateway = AndroidAppLanguageGateway(context)
        try {
            for (language in AppLanguage.entries) {
                gateway.select(language)
                assertEquals(language.tag, manager.applicationLocales.toLanguageTags())
                assertEquals(language, AndroidAppLanguageGateway(context).current().selected)
                assertEquals(system, manager.systemLocales.toLanguageTags())
            }
            gateway.select(null)
            assertTrue(manager.applicationLocales.isEmpty)
            assertNull(AndroidAppLanguageGateway(context).current().selected)
        } finally { manager.applicationLocales = LocaleList.getEmptyLocaleList() }
    }

    @Test fun everyLanguageHasLocalizedResourcesAndUnsupportedLocalesUseEnglish() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val titles = mapOf("fr-CA" to "Langue", "de-AT" to "Sprache", "es-MX" to "Idioma", "it-IT" to "Lingua", "en-GB" to "Language", "ja-JP" to "Language")
        for ((tag, title) in titles) {
            val config = android.content.res.Configuration(context.resources.configuration)
            config.setLocales(LocaleList.forLanguageTags(tag))
            assertEquals(title, context.createConfigurationContext(config).getString(R.string.language_title))
        }
    }

    @Test fun failedWritesDoNotPretendToChangeThePreferenceAndRetryReconciles() {
        var saved = LanguageState(system = AppLanguage.FRENCH)
        var fail = false
        val model = LanguageViewModel(object : AppLanguageGateway {
            override fun current() = saved
            override fun select(language: AppLanguage?) {
                if (fail) throw SecurityException("Denied")
                saved = saved.copy(selected = language)
            }
        })
        model.refresh()
        fail = true
        model.select(AppLanguage.GERMAN)
        assertTrue(model.ui.value.error)
        assertNull(model.ui.value.language?.selected)
        fail = false
        model.select(AppLanguage.GERMAN)
        assertFalse(model.ui.value.error)
        assertEquals(AppLanguage.GERMAN, model.ui.value.language?.selected)
        model.select(null)
        assertEquals(AppLanguage.FRENCH, model.ui.value.language?.effective)
    }
}
