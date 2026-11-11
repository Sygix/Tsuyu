/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 32, 33, 34])
class LanguageCompatibilityTest {
    private class Storage(var value: StoredLanguage = StoredLanguage()) : LanguageStorage {
        var fail = false
        override suspend fun read() = value
        override suspend fun select(language: AppLanguage?) {
            if (fail) throw java.io.IOException("Unavailable")
            value = value.copy(selected = language)
        }
        override suspend fun migrated() { value = value.copy(migrated = true) }
    }

    @Test fun allChoicesPersistOnThePlatformSpecificPath() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = Storage()
        val gateway = AndroidAppLanguageGateway(context, store)
        for (language in AppLanguage.entries) {
            gateway.select(language)
            assertEquals(language, AndroidAppLanguageGateway(context, store).current().selected)
        }
        gateway.select(null)
        assertNull(gateway.current().selected)
    }

    @Test fun legacyFailureRetainsTheConfirmedChoice() = runBlocking {
        if (Build.VERSION.SDK_INT >= 33) return@runBlocking
        val store = Storage(StoredLanguage(AppLanguage.FRENCH))
        val gateway = AndroidAppLanguageGateway(ApplicationProvider.getApplicationContext(), store)
        store.fail = true
        try { gateway.select(AppLanguage.GERMAN); fail("Write should fail") }
        catch (expected: java.io.IOException) { assertEquals(AppLanguage.FRENCH, gateway.current().selected) }
    }

    @Test fun migrationRunsOnceAndNeverOverridesALaterSystemChoice() = runBlocking {
        if (Build.VERSION.SDK_INT < 33) return@runBlocking
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        val store = Storage(StoredLanguage(AppLanguage.ITALIAN))
        assertEquals(AppLanguage.ITALIAN, AndroidAppLanguageGateway(context, store).current().selected)
        assertTrue(store.value.migrated)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        assertNull(AndroidAppLanguageGateway(context, store).current().selected)
        assertEquals(AppLanguage.ITALIAN, store.value.selected)
    }

    @Test fun migrationKeepsAnExistingExplicitPlatformChoice() = runBlocking {
        if (Build.VERSION.SDK_INT < 33) return@runBlocking
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("de")
        val store = Storage(StoredLanguage(AppLanguage.ITALIAN))
        assertEquals(AppLanguage.GERMAN, AndroidAppLanguageGateway(context, store).current().selected)
        assertTrue(store.value.migrated)
    }
}
