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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var model: HomeViewModel
    private var observer: AutoCloseable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = TsuyuGraph.get(this)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                graph.scope.launch {
                    graph.updates.load()
                    if (!graph.updates.state.value.auto) graph.updates.check(false)
                }
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(graph.coordinator, graph.compatibility, graph.persistenceController.state, graph.updates.notice, graph.updates.state.map { it.blocked }) as T
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
        if (action == Action.LANGUAGE) {
            model.act(action)
            startActivity(Intent(this, fr.sygix.tsuyu.language.LanguageActivity::class.java))
            return
        }
        if (action == Action.PERSISTENCE) {
            openPersistence(false)
            return
        }
        if (action == Action.START || action == Action.ENABLE) {
            lifecycleScope.launch {
                when (graph.persistenceController.startRoute()) {
                    fr.sygix.tsuyu.persistence.StartRoute.START -> model.act(action)
                    fr.sygix.tsuyu.persistence.StartRoute.DISCLOSURE,
                    fr.sygix.tsuyu.persistence.StartRoute.ERROR -> openPersistence(true)
                }
            }
            return
        }
        if (action == Action.RETURN) {
            finish()
            return
        }
        if (action == Action.UPDATES) {
            model.act(action)
            startActivity(Intent(this, fr.sygix.tsuyu.update.UpdateActivity::class.java))
            return
        }
        if (action == Action.APPS) {
            model.act(action)
            startActivity(Intent(this, fr.sygix.tsuyu.access.AccessActivity::class.java))
            return
        }
        if (action == Action.SETTINGS) {
            SettingsNavigation.open(
                { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                model::settingsFailed,
            )
        } else model.act(action)
    }
    private fun openPersistence(fromMain: Boolean) {
        model.persistenceEntry(fromMain)
        startActivity(Intent(this, fr.sygix.tsuyu.persistence.PersistenceActivity::class.java))
    }
    override fun onStart() {
        super.onStart()
        try { observer = TsuyuGraph.get(this).settings.observe { model.resume() } }
        catch (unavailable: Exception) { model.settingsFailed() }
    }
    private fun resolveCaller(): String? {
        val name = launchedFromPackage?.takeIf { it != packageName } ?: return null
        return try { packageManager.getApplicationInfo(name, 0).loadLabel(packageManager).toString() }
        catch (missing: android.content.pm.PackageManager.NameNotFoundException) { null }
    }
    override fun onResume() { super.onResume(); model.caller(resolveCaller()); model.resume(); lifecycleScope.launch { TsuyuGraph.get(this@MainActivity).persistenceController.refresh() } }
    override fun onStop() {
        TsuyuGraph.get(this).let { graph -> graph.scope.launch { graph.updates.consumeNotice() } }
        try { observer?.close() } catch (failure: Exception) { model.settingsFailed() }
        observer = null
        super.onStop()
    }
}
