/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.sygix.tsuyu.TsuyuGraph

class PermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra("request")
        val permission = token != null
        val graph = TsuyuGraph.get(this)
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AccessViewModel(applicationContext, { graph.broker.server.value }, token, ended = graph.requestEnded) as T
            }
        })[AccessViewModel::class.java]
        setContent {
            val ui = model.ui.collectAsStateWithLifecycle().value
            val back = { if (permission) model.confirm(false) else finish() }
            BackHandler { back() }
            AccessScreen(ui, permission, model::confirm, model::revoke, model::refresh, back)
            LaunchedEffect(ui.finished) { if (ui.finished) finish() }
        }
    }
}
