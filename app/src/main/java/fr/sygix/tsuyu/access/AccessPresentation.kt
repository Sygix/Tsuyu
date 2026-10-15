/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import fr.sygix.tsuyu.R

enum class AccessAction { DENY, ALLOW, RETRY, BACK, REVOKE }

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
                    AccessControl("revoke-${row.uid}", AccessAction.REVOKE, if (row.cleanupPending) R.string.access_finish_revoke else R.string.access_revoke_named, row, row.apps.joinToString { it.name })
                } + AccessControl("access-back", AccessAction.BACK, R.string.return_home)
            }
            val navigation = controls.mapIndexed { index, control ->
                val previous = controls.getOrNull(index - 1)?.id
                val next = controls.getOrNull(index + 1)?.id
                if (ui.permission) control.copy(left = previous, right = next)
                else control.copy(up = previous, down = next)
            }
            val focus = "revoke-${ui.focus}".takeIf { !ui.permission && controls.any { control -> control.id == it } }
                ?: controls.first().id
            return AccessPresentation(
                if (ui.permission) R.string.access_request else R.string.access_apps,
                when {
                    ui.error && ui.retryRevoke != null -> R.string.access_cleanup_error
                    ui.error -> R.string.access_error
                    ui.loading -> R.string.checking_body
                    ui.permission -> R.string.access_effect
                    ui.rows.isEmpty() -> R.string.access_empty
                    else -> R.string.access_revoke_effect
                },
                navigation,
                focus,
            )
        }
    }
}
