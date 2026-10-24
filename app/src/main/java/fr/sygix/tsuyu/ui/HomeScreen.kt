/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import fr.sygix.tsuyu.R
import fr.sygix.tsuyu.ui.theme.*

@Composable
fun HomeScreen(ui: HomeUi, onAction: (Action) -> Unit) {
    StateScreen(ui, onAction, home = true) {
        ui.notice?.let { StatusSurface(stringResource(R.string.update_installed, it), StatusKind.ACTIVE, Modifier.testTag("update-notice")) }
        if (ui.updateBlocked) StatusSurface(stringResource(R.string.update_blocked_home), StatusKind.ATTENTION, Modifier.testTag("update-blocked"))
        ui.persistenceStatus?.let {
            StatusSurface(stringResource(it), if (ui.persistenceAttention) StatusKind.ATTENTION else StatusKind.ACTIVE, Modifier.testTag("persistence-status"))
        }
    }
}

@Composable
fun StateScreen(ui: ScreenUi, onAction: (Action) -> Unit, home: Boolean = false, details: @Composable ColumnScope.() -> Unit = {}) {
    val focusable = ui.controls.filter { it.enabled }
    val requesters = remember(ui.controls.map { it.id }) { ui.controls.associate { it.id to FocusRequester() } }
    TsuyuTheme {
        Row(
            Modifier.fillMaxSize().background(TsuyuColors.background)
                .padding(horizontal = TsuyuSpacing.horizontal, vertical = TsuyuSpacing.vertical)
                .testTag("screen-${ui.screen.name.lowercase()}"),
        ) {
            Column(Modifier.weight(0.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(TsuyuSpacing.gap)) {
                BrandHeader(home)
                Spacer(Modifier.height(8.dp))
                Text(localized(ui.title, ui.titleArgs), fontSize = TsuyuType.title, color = TsuyuColors.text, modifier = Modifier.testTag("state-title"))
                Text(localized(ui.body, ui.bodyArgs), fontSize = TsuyuType.body, color = TsuyuColors.secondary, modifier = Modifier.testTag("state-body"))
                if (ui.busy) OperationProgress(Modifier.padding(vertical = 8.dp))
                details()
            }
            Spacer(Modifier.width(TsuyuSpacing.gutter))
            Column(
                Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TsuyuSpacing.actionGap, Alignment.CenterVertically),
            ) {
                ui.controls.forEach { control ->
                    key(control.id) {
                        val index = focusable.indexOf(control)
                        TvAction(
                            localized(control.label, control.args), { onAction(control.action) },
                            Modifier.fillMaxWidth().testTag(control.id)
                                .focusRequester(requesters.getValue(control.id))
                                .focusProperties {
                                    up = focusable.getOrNull(index - 1)?.let { requesters.getValue(it.id) } ?: FocusRequester.Cancel
                                    down = focusable.getOrNull(index + 1)?.let { requesters.getValue(it.id) } ?: FocusRequester.Cancel
                                    left = FocusRequester.Cancel; right = FocusRequester.Cancel
                                },
                            enabled = control.enabled,
                        )
                    }
                }
            }
        }
        LaunchedEffect(ui.screen, ui.focus, requesters, focusable.map { it.id }) {
            val target = requesters[ui.focus]?.takeIf { focusable.any { it.id == ui.focus } }
            if (target?.requestFocus(FocusDirection.Enter) != true) {
                focusable.firstOrNull()?.let { requesters.getValue(it.id).requestFocus(FocusDirection.Enter) }
            }
        }
    }
}

@Composable
fun localized(resource: Int, args: List<Any>): String =
    stringResource(resource, *args.map { if (it is Int) stringResource(it) else it }.toTypedArray())
