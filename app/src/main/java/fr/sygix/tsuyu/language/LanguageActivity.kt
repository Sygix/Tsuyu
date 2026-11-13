/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class LanguageActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLocalizedContent {
            BackHandler { finish() }
            LanguageScreen(languageModel.ui.collectAsStateWithLifecycle().value, languageModel::select, languageModel::refresh)
        }
    }

}
