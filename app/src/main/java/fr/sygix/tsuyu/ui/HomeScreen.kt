/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import fr.sygix.tsuyu.R

@Composable
fun HomeScreen(ui: HomeUi, onAction: (Action) -> Unit) {
    StateScreen(ui, onAction) {
        ui.persistenceStatus?.let {
            Text(stringResource(it), fontSize = 18.sp, color = Color(0xffc6d1df), modifier = Modifier.testTag("persistence-status"))
        }
    }
}

@Composable
fun StateScreen(ui: ScreenUi, onAction: (Action) -> Unit, details: @Composable () -> Unit) {
    val requesters = remember(ui.controls.map { it.id }) { ui.controls.associate { it.id to FocusRequester() } }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xff94d8df), background = Color(0xff10161e), surface = Color(0xff1e2936))) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 56.dp, vertical = 40.dp).testTag("screen-${ui.screen.name.lowercase()}")) {
            Text(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(ui.title), fontSize = 36.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.testTag("state-title"))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(ui.body), fontSize = 20.sp, color = Color(0xffc6d1df), modifier = Modifier.widthIn(max = 720.dp).testTag("state-body"))
            details()
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ui.controls.forEachIndexed { index, control ->
                    key(control.id) {
                        Button(onClick = { onAction(control.action) },
                            modifier = Modifier.widthIn(min = 260.dp).testTag(control.id)
                                .focusRequester(requesters.getValue(control.id))
                                .focusProperties {
                                    up = if (index > 0) requesters.getValue(ui.controls[index - 1].id) else FocusRequester.Cancel
                                    down = if (index < ui.controls.lastIndex) requesters.getValue(ui.controls[index + 1].id) else FocusRequester.Cancel
                                    left = FocusRequester.Cancel; right = FocusRequester.Cancel
                                },
                            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.primary),
                            scale = ButtonDefaults.scale(focusedScale = 1.03f)
                        ) { Text(stringResource(control.label), fontSize = 20.sp) }
                    }
                }
            }
        }
        LaunchedEffect(ui.screen, ui.focus, requesters) {
            if (!requesters.getValue(ui.focus).requestFocus(FocusDirection.Enter)) {
                requesters.getValue(ui.controls.first().id).requestFocus(FocusDirection.Enter)
            }
        }
    }
}
