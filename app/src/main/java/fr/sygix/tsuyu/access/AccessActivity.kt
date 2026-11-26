/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.sygix.tsuyu.TsuyuGraph

class AccessActivity : ComponentActivity() {
    private lateinit var model: AccessViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { window.setHideOverlayWindows(true) }
            catch (denied: SecurityException) { android.util.Log.w("Tsuyu", "Overlay hiding unavailable on this firmware", denied) }
        }
        val token = intent.getStringExtra("request")
        val graph = TsuyuGraph.get(this)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AccessViewModel(applicationContext, { graph.broker.server.value }, token, ended = graph.requestEnded) as T
            }
        })[AccessViewModel::class.java]
        setContent {
            val ui = model.ui.collectAsStateWithLifecycle().value
            val back = model::back
            BackHandler { back() }
            AccessScreen(ui, model::confirm, model::revoke, model::refresh, back, model::reset, model::focused)
            LaunchedEffect(ui.finished) { if (ui.finished) finish() }
        }
    }
    override fun onResume() {
        super.onResume()
        model.resumed()
    }
}
