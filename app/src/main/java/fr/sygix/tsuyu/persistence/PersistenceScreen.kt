/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.sygix.tsuyu.ui.theme.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import fr.sygix.tsuyu.R
import fr.sygix.tsuyu.ui.Action
import fr.sygix.tsuyu.ui.Control
import fr.sygix.tsuyu.ui.Screen
import fr.sygix.tsuyu.ui.ScreenUi
import fr.sygix.tsuyu.ui.StateScreen

sealed interface PersistenceDetails {
    data class Modes(val uid: Int, val pkg: Int) : PersistenceDetails
    data class Expiry(val milliseconds: Long?) : PersistenceDetails
}

data class PersistenceScreenUi(
    override val screen: Screen,
    override val title: Int,
    override val body: Int,
    override val controls: List<Control>,
    override val focus: String,
    val details: PersistenceDetails,
    override val busy: Boolean = false,
) : ScreenUi

@Composable
fun PersistenceScreen(ui: PersistenceScreenUi, onAction: (Action) -> Unit) {
    StateScreen(ui, onAction) {
        val details = ui.details
        if (details is PersistenceDetails.Expiry && details.milliseconds == null) return@StateScreen
        SupportingPanel {
            Text(stringResource(R.string.technical_details), fontSize = TsuyuType.supporting, color = TsuyuColors.text)
            when (details) {
                is PersistenceDetails.Modes -> Text(
                    stringResource(R.string.persistence_modes, stringResource(details.uid), stringResource(details.pkg)),
                    fontSize = TsuyuType.supporting, color = TsuyuColors.secondary, modifier = Modifier.testTag("persistence-modes"),
                )
                is PersistenceDetails.Expiry -> Text(
                    stringResource(R.string.expiry_value, requireNotNull(details.milliseconds)),
                    fontSize = TsuyuType.supporting, color = TsuyuColors.secondary, modifier = Modifier.testTag("expiry-value"),
                )
            }
        }
    }
}
