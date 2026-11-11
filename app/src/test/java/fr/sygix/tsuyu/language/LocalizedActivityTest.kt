/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.os.Bundle
import android.os.Looper
import fr.sygix.tsuyu.R
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33, 34], qualifiers = "w960dp-h540dp-xhdpi")
class LocalizedActivityTest {
    class EntryActivity : LocalizedActivity() {
        val loaded = CompletableDeferred<LanguageState>()
        override fun languageGateway() = object : AppLanguageGateway {
            override suspend fun current() = loaded.await()
            override suspend fun select(language: AppLanguage?) = Unit
        }
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setLocalizedContent { }
        }
    }

    @Test fun confirmedPreferenceLocalizesActivityResourcesBeforeContent() {
        val controller = Robolectric.buildActivity(EntryActivity::class.java).create().start().resume()
        try {
            val activity = controller.get()
            activity.loaded.complete(LanguageState(AppLanguage.FRENCH))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("fr", activity.resources.configuration.locales[0].language)
            assertEquals("Langue", activity.getString(R.string.language_title))
            assertFalse(activity.isFinishing)
        } finally { controller.pause().stop().destroy() }
    }
}
