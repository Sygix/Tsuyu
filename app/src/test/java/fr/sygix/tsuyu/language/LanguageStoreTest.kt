/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class LanguageStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun everyLanguageAndMigrationMarkerSurviveClosingAndReopeningStorage() = runTest {
        val file = temporary.newFile("language.preferences_pb")
        var job = Job()
        fun open() = LanguageStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(coroutineContext + job), produceFile = { file },
        ))
        try {
            var store = open()
            assertEquals(StoredLanguage(), store.read())
            for (language in AppLanguage.entries) {
                store.select(language)
                job.cancelAndJoin()
                job = Job()
                store = open()
                assertEquals(language, store.read().selected)
            }
            store.migrated()
            store.select(null)
            job.cancelAndJoin()
            job = Job()
            store = open()
            assertEquals(StoredLanguage(migrated = true), store.read())
        } finally { job.cancelAndJoin() }
    }
}
