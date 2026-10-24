/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import fr.sygix.tsuyu.R

enum class AccessAction { DENY, ALLOW, RETRY, BACK, REVOKE, RESET }

data class AccessControl(
    val id: String,
    val action: AccessAction,
    val label: Int,
    val row: AccessRow? = null,
    val name: String = "",
    val up: String? = null,
    val down: String? = null,
    val left: String? = null,
    val right: String? = null,
)

data class AccessPresentation(val title: Int, val body: Int, val controls: List<AccessControl>, val focus: String) {
    companion object {
        fun id(row: AccessRow) = if (row.refused) "reset-${row.uid}" else "revoke-${row.uid}"
        fun from(ui: AccessUi): AccessPresentation {
            val controls = when {
                ui.loading || ui.error -> listOf(
                    AccessControl("access-back", AccessAction.BACK, R.string.return_home),
                    AccessControl("access-retry", AccessAction.RETRY, R.string.retry),
                )
                ui.permission -> listOf(
                    AccessControl("access-deny", AccessAction.DENY, R.string.access_deny),
                    AccessControl("access-allow", AccessAction.ALLOW, R.string.access_allow),
                )
                else -> ui.rows.map { row ->
                    val names = row.apps.joinToString { it.name }
                    when {
                        row.refused -> AccessControl(id(row), AccessAction.RESET, R.string.access_reset, row, names)
                        row.cleanupPending -> AccessControl(id(row), AccessAction.REVOKE, R.string.access_finish_revoke, row, names)
                        else -> AccessControl(id(row), AccessAction.REVOKE, R.string.access_revoke, row, names)
                    }
                } + AccessControl("access-back", AccessAction.BACK, R.string.return_home)
            }
            val navigation = controls.mapIndexed { index, control ->
                val previous = controls.getOrNull(index - 1)?.id
                val next = controls.getOrNull(index + 1)?.id
                if (ui.permission) control.copy(left = previous, right = next)
                else control.copy(up = previous, down = next)
            }
            val focus = ui.rows.firstOrNull { it.uid == ui.focus }?.let(::id)?.takeIf { !ui.permission && controls.any { control -> control.id == it } }
                ?: controls.first().id
            return AccessPresentation(
                if (ui.permission) R.string.access_request else R.string.access_apps,
                when {
                    ui.resetFailed != null -> R.string.access_reset_error
                    ui.error && ui.retryRevoke != null -> R.string.access_cleanup_error
                    ui.error -> R.string.access_error
                    ui.loading -> R.string.checking_body
                    ui.permission -> R.string.access_effect
                    ui.rows.isEmpty() -> R.string.access_empty
                    else -> R.string.access_list_effect
                },
                navigation,
                focus,
            )
        }
    }
}
