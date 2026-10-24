/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import fr.sygix.tsuyu.R
import fr.sygix.tsuyu.TsuyuGraph
import fr.sygix.tsuyu.start.Phase
import fr.sygix.tsuyu.ui.Action
import fr.sygix.tsuyu.ui.StateScreen
import fr.sygix.tsuyu.ui.theme.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class UpdateActivity : ComponentActivity() {
    private lateinit var model: UpdateViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = TsuyuGraph.get(this)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return UpdateViewModel(graph.updates, graph.coordinator.state.map { it.phase == Phase.RUNNING }, graph.updateHost::prepared, graph.scope) as T
            }
        })[UpdateViewModel::class.java]
        lifecycleScope.launch { graph.updates.load() }
        lifecycleScope.launch { model.preparation.collect { startActivity(Intent(this@UpdateActivity, fr.sygix.tsuyu.persistence.PersistenceActivity::class.java)) } }
        setContent {
            val ui = model.ui.collectAsStateWithLifecycle().value
            BackHandler { finish() }
            UpdateScreen(ui) { action -> if (action == Action.BACK) finish() else model.act(action) }
        }
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { TsuyuGraph.get(this@UpdateActivity).coordinator.refresh() }
        model.resumed()
    }
}

@Composable
fun UpdateScreen(ui: UpdateScreenUi, onAction: (Action) -> Unit) {
    StateScreen(ui, onAction) {
        ui.checkedAt?.let { checked ->
            val locales = LocalConfiguration.current.locales
            val time = remember(checked, locales) { formatUpdateTime(checked, List(locales.size()) { locales[it] }) }
            StatusSurface(stringResource(R.string.update_checked_at, time), if (ui.attention) StatusKind.ATTENTION else StatusKind.NEUTRAL, Modifier.testTag("update-checked"))
        }
        ui.notes?.let { page ->
            val code = remember(page) { QrCode.bitmap(page, 200).asImageBitmap() }
            Row(Modifier.testTag("update-notes"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(code, contentDescription = null, modifier = Modifier.size(100.dp))
                Text(stringResource(R.string.update_notes), fontSize = TsuyuType.supporting, color = TsuyuColors.secondary)
            }
        }
    }
}
