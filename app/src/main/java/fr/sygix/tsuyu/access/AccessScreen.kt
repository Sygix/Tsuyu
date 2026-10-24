/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import fr.sygix.tsuyu.R
import fr.sygix.tsuyu.ui.theme.*

@Composable
fun AccessScreen(ui: AccessUi, confirm: (Boolean) -> Unit, revoke: (Int) -> Unit, retry: () -> Unit, back: () -> Unit, reset: (Int) -> Unit = {}) {
    val presentation = ui.presentation
    val ids = presentation.controls.map { it.id }
    val requesters = remember(ids) { ids.associateWith { FocusRequester() } }
    val action: @Composable (AccessControl, Modifier) -> Unit = { control, modifier ->
        val row = control.row
        TvAction(stringResource(control.label, control.name), {
            when (control.action) {
                AccessAction.DENY -> confirm(false)
                AccessAction.ALLOW -> confirm(true)
                AccessAction.RETRY -> retry()
                AccessAction.BACK -> back()
                AccessAction.REVOKE -> revoke(requireNotNull(row).uid)
                AccessAction.RESET -> reset(requireNotNull(row).uid)
            }
        }, modifier.testTag(control.id).focusRequester(requesters.getValue(control.id)).focusProperties {
            up = requesters[control.up] ?: FocusRequester.Cancel
            down = requesters[control.down] ?: FocusRequester.Cancel
            left = requesters[control.left] ?: FocusRequester.Cancel
            right = requesters[control.right] ?: FocusRequester.Cancel
        })
    }
    TsuyuTheme {
        Column(
            Modifier.fillMaxSize().background(TsuyuColors.background)
                .padding(horizontal = TsuyuSpacing.horizontal, vertical = TsuyuSpacing.vertical).testTag("access-screen"),
            verticalArrangement = Arrangement.spacedBy(TsuyuSpacing.gap),
        ) {
            BrandHeader(home = false)
            if (ui.permission) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = 704.dp).clip(RoundedCornerShape(TsuyuSpacing.panelRadius)).background(TsuyuColors.surface)
                            .padding(32.dp).verticalScroll(rememberScrollState()).testTag("access-request-surface"),
                        verticalArrangement = Arrangement.spacedBy(TsuyuSpacing.gap),
                    ) {
                        Text(stringResource(presentation.title), fontSize = TsuyuType.title, color = TsuyuColors.text, modifier = Modifier.testTag("access-title"))
                        ui.rows.forEach { row -> key(row.uid) { AppNames(row) } }
                        Text(stringResource(presentation.body), fontSize = TsuyuType.body, color = TsuyuColors.secondary, modifier = Modifier.testTag("access-body"))
                        DeliveryFailures(ui)
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            presentation.controls.forEach { control -> key(control.id) { action(control, Modifier.weight(1f)) } }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(TsuyuSpacing.gap)) {
                    Text(stringResource(presentation.title), fontSize = TsuyuType.title, color = TsuyuColors.text, modifier = Modifier.testTag("access-title"))
                    Text(stringResource(presentation.body), fontSize = TsuyuType.body, color = TsuyuColors.secondary, modifier = Modifier.testTag("access-body"))
                    if (ui.loading) OperationProgress(Modifier.widthIn(max = 480.dp))
                    DeliveryFailures(ui)
                    presentation.controls.forEach { control -> key(control.id) {
                        val row = control.row
                        if (row == null) action(control, Modifier.width(360.dp))
                        else Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(TsuyuSpacing.panelRadius)).background(TsuyuColors.surface).padding(16.dp).testTag("group-${control.id}"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TsuyuSpacing.gap),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppNames(row)
                                val (status, color) = when {
                                    row.refused -> R.string.access_status_refused to TsuyuColors.error
                                    row.cleanupPending -> R.string.access_status_cleanup to TsuyuColors.error
                                    else -> R.string.access_status_allowed to TsuyuColors.secondary
                                }
                                Text(stringResource(status), fontSize = TsuyuType.supporting, color = color, modifier = Modifier.testTag("status-${control.id}"))
                            }
                            action(control, Modifier.width(360.dp))
                        }
                    } }
                }
            }
        }
        LaunchedEffect(ids, presentation.focus) { requesters.getValue(presentation.focus).requestFocus(FocusDirection.Enter) }
    }
}

@Composable
private fun DeliveryFailures(ui: AccessUi) {
    if (ui.deliveryFailures.isNotEmpty()) {
        Text(stringResource(R.string.access_delivery_error, ui.deliveryFailures.joinToString { it.name }), fontSize = TsuyuType.supporting, color = TsuyuColors.error, modifier = Modifier.testTag("access-delivery-error"))
    }
}

@Composable
private fun AppNames(row: AccessRow) {
    row.apps.forEach { app ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
            Text(app.name, fontSize = TsuyuType.body, color = TsuyuColors.text)
        }
    }
    if (row.apps.size > 1) Text(stringResource(R.string.access_group), fontSize = TsuyuType.supporting, color = TsuyuColors.secondary, modifier = Modifier.testTag("access-group"))
}
