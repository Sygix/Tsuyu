/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.sygix.tsuyu.TsuyuGraph
import fr.sygix.tsuyu.ui.*

class PersistenceActivity : ComponentActivity() {
    private lateinit var model: PersistenceViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = TsuyuGraph.get(this)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PersistenceViewModel(graph.persistenceController) as T
            }
        })[PersistenceViewModel::class.java]
        setContent {
            val ui = model.ui.collectAsStateWithLifecycle().value
            BackHandler { back() }
            PersistenceScreen(ui) { action ->
                when (action) {
                    Action.BACK -> back()
                    Action.SETTINGS -> {
                        SettingsNavigation.open(
                            { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                            model::settingsFailed,
                        )
                    }
                    else -> model.act(action)
                }
            }
        }
    }
    private fun back() { if (model.optional) model.act(Action.BACK) else finish() }
    override fun onResume() { super.onResume(); model.refresh() }
}
