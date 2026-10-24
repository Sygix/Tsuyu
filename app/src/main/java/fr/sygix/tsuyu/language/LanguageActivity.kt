/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class LanguageActivity : ComponentActivity() {
    private lateinit var model: LanguageViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LanguageViewModel(AndroidAppLanguageGateway(applicationContext)) as T
            }
        })[LanguageViewModel::class.java]
        setContent {
            BackHandler { finish() }
            LanguageScreen(model.ui.collectAsStateWithLifecycle().value, model::select, model::refresh)
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}
