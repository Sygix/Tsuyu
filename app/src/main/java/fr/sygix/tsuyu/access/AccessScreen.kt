/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import fr.sygix.tsuyu.R

@Composable
fun AccessScreen(ui: AccessUi, confirm: (Boolean) -> Unit, revoke: (Int) -> Unit, retry: () -> Unit, back: () -> Unit) {
    val presentation = ui.presentation
    val ids = presentation.controls.map { it.id }
    val requesters = remember(ids) { ids.associateWith { FocusRequester() } }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xff94d8df))) {
        CompositionLocalProvider(LocalContentColor provides Color(0xffc6d1df)) {
        Column(Modifier.fillMaxSize().background(Color(0xff10161e)).padding(56.dp, 40.dp).verticalScroll(rememberScrollState()).testTag("access-screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(presentation.title), fontSize = 32.sp, modifier = Modifier.testTag("access-title"))
            Text(stringResource(presentation.body), fontSize = 20.sp, modifier = Modifier.testTag("access-body"))
            if (ui.deliveryFailures.isNotEmpty()) {
                Text(stringResource(R.string.access_delivery_error, ui.deliveryFailures.joinToString { it.name }), fontSize = 20.sp, modifier = Modifier.testTag("access-delivery-error"))
            }
            if (ui.permission) ui.rows.forEach { row -> key(row.uid) { AppNames(row) } }
            val actions: @Composable () -> Unit = { presentation.controls.forEach { control -> key(control.id) {
                val id = control.id
                val row = control.row
                Column(Modifier.testTag("group-$id"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row != null) AppNames(row)
                Button(onClick = {
                    when (control.action) {
                        AccessAction.DENY -> confirm(false)
                        AccessAction.ALLOW -> confirm(true)
                        AccessAction.RETRY -> retry()
                        AccessAction.BACK -> back()
                        AccessAction.REVOKE -> revoke(requireNotNull(row).uid)
                    }
                }, modifier = Modifier.testTag(id).focusRequester(requesters.getValue(id)).focusProperties {
                    up = requesters[control.up] ?: FocusRequester.Cancel
                    down = requesters[control.down] ?: FocusRequester.Cancel
                    left = requesters[control.left] ?: FocusRequester.Cancel
                    right = requesters[control.right] ?: FocusRequester.Cancel
                }) { Text(stringResource(control.label, control.name), fontSize = 20.sp) }
                }
            } } }
            if (ui.permission) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { actions() }
            else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
        }
        LaunchedEffect(ids, presentation.focus) { requesters.getValue(presentation.focus).requestFocus(FocusDirection.Enter) }
        }
    }
}

@Composable
private fun AppNames(row: AccessRow) {
    row.apps.forEach { app -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp))
        Text(app.name, fontSize = 22.sp)
    } }
    if (row.apps.size > 1) Text(stringResource(R.string.access_group), fontSize = 20.sp, modifier = Modifier.testTag("access-group"))
}
