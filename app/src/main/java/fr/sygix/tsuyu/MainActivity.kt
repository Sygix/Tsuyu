/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import fr.sygix.tsuyu.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var model: HomeViewModel
    private var observer: AutoCloseable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = TsuyuGraph.get(this)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(graph.coordinator, graph.compatibility, graph.persistenceController.state) as T
            }
        })[HomeViewModel::class.java]
        lifecycleScope.launch { graph.broker.server.collect { if (it == null) model.died() else model.resume() } }
        setContent {
            val ui = model.ui.collectAsStateWithLifecycle().value
            BackHandler { handleBack(ui.screen) }
            HomeScreen(ui) { action ->
                handleAction(action)
            }
        }
    }
    private fun handleBack(screen: Screen) {
        if (screen == Screen.HOME) { model.cancelPending(); finish() }
        else model.back()
    }
    private fun handleAction(action: Action) {
        val graph = TsuyuGraph.get(this)
        if (action == Action.PERSISTENCE || action in listOf(Action.START, Action.ENABLE) && !graph.persistenceController.state.value.enabled) {
            model.persistenceEntry(action != Action.PERSISTENCE)
            startActivity(Intent(this, fr.sygix.tsuyu.persistence.PersistenceActivity::class.java))
            return
        }
        if (action == Action.APPS) {
            model.act(action)
            startActivity(Intent(this, fr.sygix.tsuyu.access.PermissionActivity::class.java))
            return
        }
        if (action == Action.SETTINGS) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            catch (unavailable: Exception) { model.settingsFailed() }
        } else model.act(action)
    }
    override fun onStart() {
        super.onStart()
        try { observer = TsuyuGraph.get(this).settings.observe { model.resume() } }
        catch (unavailable: Exception) { model.settingsFailed() }
    }
    override fun onResume() { super.onResume(); model.resume(); lifecycleScope.launch { TsuyuGraph.get(this@MainActivity).persistenceController.refresh() } }
    override fun onStop() {
        try { observer?.close() } catch (failure: Exception) { model.settingsFailed() }
        observer = null
        super.onStop()
    }
}
