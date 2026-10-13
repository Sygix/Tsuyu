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
fun AccessScreen(ui: AccessUi, permission: Boolean, confirm: (Boolean) -> Unit, revoke: (Int) -> Unit, retry: () -> Unit, back: () -> Unit) {
    val ids = if (ui.loading || ui.error) listOf("access-back", "access-retry") else if (permission) listOf("access-deny", "access-allow") else ui.rows.map { "revoke-${it.uid}" } + "access-back"
    val requesters = remember(ids) { ids.associateWith { FocusRequester() } }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xff94d8df))) {
        Column(Modifier.fillMaxSize().background(Color(0xff10161e)).padding(56.dp, 40.dp).verticalScroll(rememberScrollState()).testTag("access-screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if (permission) R.string.access_request else R.string.access_apps), fontSize = 32.sp)
            Text(stringResource(if (ui.error) R.string.access_error else if (ui.loading) R.string.checking_body else if (permission) R.string.access_effect else if (ui.rows.isEmpty()) R.string.access_empty else R.string.access_revoke_effect), fontSize = 20.sp)
            ui.rows.forEach { row -> key(row.uid) {
                row.apps.forEach { app -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp))
                    Text(app.name, fontSize = 22.sp)
                } }
                if (row.apps.size > 1) Text(stringResource(R.string.access_group), fontSize = 20.sp, modifier = Modifier.testTag("access-group"))
            } }
            val actions: @Composable () -> Unit = { ids.forEachIndexed { index, id ->
                Button(onClick = {
                    when (id) {
                        "access-deny" -> confirm(false)
                        "access-allow" -> confirm(true)
                        "access-retry" -> retry()
                        "access-back" -> back()
                        else -> revoke(id.removePrefix("revoke-").toInt())
                    }
                }, modifier = Modifier.testTag(id).focusRequester(requesters.getValue(id)).focusProperties {
                    up = if (permission || index == 0) FocusRequester.Cancel else requesters.getValue(ids[index - 1])
                    down = if (permission || index == ids.lastIndex) FocusRequester.Cancel else requesters.getValue(ids[index + 1])
                    left = if (permission && index > 0) requesters.getValue(ids[index - 1]) else FocusRequester.Cancel
                    right = if (permission && index < ids.lastIndex) requesters.getValue(ids[index + 1]) else FocusRequester.Cancel
                }) { Text(stringResource(when (id) { "access-deny" -> R.string.access_deny; "access-allow" -> R.string.access_allow; "access-retry" -> R.string.retry; "access-back" -> R.string.return_home; else -> R.string.access_revoke }), fontSize = 20.sp) }
            } }
            if (permission) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { actions() }
            else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
        }
        LaunchedEffect(ids, ui.focus) { requesters[if (permission) ids.first() else "revoke-${ui.focus}" ]?.requestFocus(FocusDirection.Enter) ?: requesters.getValue(ids.first()).requestFocus(FocusDirection.Enter) }
    }
}
