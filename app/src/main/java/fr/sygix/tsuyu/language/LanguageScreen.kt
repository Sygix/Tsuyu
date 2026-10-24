/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import fr.sygix.tsuyu.R
import fr.sygix.tsuyu.ui.theme.*

@Composable
fun LanguageScreen(ui: LanguageUi, onSelect: (AppLanguage?) -> Unit, onRetry: () -> Unit) {
    val choices = remember { listOf(null) + AppLanguage.entries }
    val requesters = remember { choices.map { FocusRequester() } }
    val retry = remember { FocusRequester() }
    TsuyuTheme {
        Row(Modifier.fillMaxSize().background(TsuyuColors.background)
            .padding(horizontal = TsuyuSpacing.horizontal, vertical = TsuyuSpacing.vertical)
            .testTag("screen-language")) {
            Column(Modifier.weight(0.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(TsuyuSpacing.gap)) {
                BrandHeader(false)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.language_title), fontSize = TsuyuType.title, color = TsuyuColors.text)
                Text(stringResource(R.string.language_body), fontSize = TsuyuType.body, color = TsuyuColors.secondary)
                ui.language?.let { state ->
                    val detail = state.system?.let { stringResource(R.string.language_system_value, it.nativeName) }
                        ?: stringResource(R.string.language_system_unsupported, AppLanguage.fallback.nativeName)
                    Text(detail, fontSize = TsuyuType.supporting, color = TsuyuColors.secondary, modifier = Modifier.testTag("language-system-detail"))
                }
                if (ui.error) {
                    StatusSurface(stringResource(R.string.language_error), StatusKind.ATTENTION, Modifier.testTag("language-error"))
                    TvAction(stringResource(R.string.retry), onRetry, Modifier.focusRequester(retry).testTag("language-retry"))
                }
            }
            Spacer(Modifier.width(TsuyuSpacing.gutter))
            Column(Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TsuyuSpacing.actionGap, Alignment.CenterVertically)) {
                choices.forEachIndexed { index, language ->
                    TvAction(
                        language?.nativeName ?: stringResource(R.string.language_system), { onSelect(language) },
                        Modifier.fillMaxWidth().testTag("language-${language?.tag ?: "system"}")
                            .semantics { selected = ui.language != null && ui.language.selected == language }
                            .focusRequester(requesters[index])
                            .focusProperties {
                                up = requesters.getOrNull(index - 1) ?: FocusRequester.Cancel
                                down = requesters.getOrNull(index + 1) ?: FocusRequester.Cancel
                                left = if (ui.error) retry else FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                        enabled = ui.language != null,
                        selected = ui.language != null && ui.language.selected == language,
                    )
                }
            }
        }
        LaunchedEffect(ui.language?.selected, ui.language != null) {
            if (ui.language != null) requesters[choices.indexOf(ui.language.selected)].requestFocus(FocusDirection.Enter)
        }
        LaunchedEffect(ui.error) {
            if (ui.error && ui.language == null) retry.requestFocus(FocusDirection.Enter)
        }
    }
}
