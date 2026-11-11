/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()

    class GateProbeActivity : LocalizedActivity() {
        var gateBacks = 0
        override fun languageGateway() = object : AppLanguageGateway {
            override suspend fun current(): LanguageState = throw java.io.IOException("unreadable")
            override suspend fun select(language: AppLanguage?) = Unit
        }
        override fun onLanguageGateBack() { gateBacks++ }
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setLocalizedContent { }
        }
    }

    @Test fun corruptFileAndUnknownTagFallBackToTheSystemLanguage() = runTest {
        val file = temporary.newFile("language.preferences_pb").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val data = PreferenceDataStoreFactory.create(corruptionHandler = languageCorruption, scope = backgroundScope, produceFile = { file })
        val store = LanguageStore(data)
        assertNull(store.read().selected)
        data.edit { it[stringPreferencesKey("selected")] = "pt" }
        assertNull(store.read().selected)
        store.select(AppLanguage.GERMAN)
        assertEquals(AppLanguage.GERMAN, store.read().selected)
    }

    @Test fun unreadableLegacyStoreNeverBlocksThePlatformLanguage() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val broken = object : LanguageStorage {
            override suspend fun read(): StoredLanguage = throw java.io.IOException("unreadable")
            override suspend fun select(language: AppLanguage?) = throw java.io.IOException("unwritable")
            override suspend fun migrated() = throw java.io.IOException("unwritable")
        }
        val gateway = AndroidAppLanguageGateway(context, broken)
        assertTrue(runCatching { gateway.current() }.isSuccess)
    }

    @Test fun backOnTheLanguageGateIsHandledByTheActivity() {
        val controller = Robolectric.buildActivity(GateProbeActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        controller.get().onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, controller.get().gateBacks)
        assertFalse(controller.get().isFinishing)
    }
}
