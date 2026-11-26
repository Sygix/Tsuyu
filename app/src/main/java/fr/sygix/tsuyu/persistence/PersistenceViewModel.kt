/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.sygix.tsuyu.R
import fr.sygix.tsuyu.ipc.Preparation
import fr.sygix.tsuyu.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import fr.sygix.tsuyu.start.Phase
import fr.sygix.tsuyu.start.StartState

class PersistenceViewModel(private val controller: PersistenceController) : ViewModel() {
    private val expiry = MutableStateFlow(false)
    private var returnFocus: String? = null
    private var renewal: kotlinx.coroutines.Job? = null
    val ui = combine(controller.state, expiry, controller.startState) { state, optional, start -> present(state, optional, returnFocus, start) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, present(controller.state.value, false, returnFocus, controller.startState.value))
    fun refresh(showProgress: Boolean = false) {
        viewModelScope.launch { controller.refresh(showProgress) }
    }
    fun settingsFailed() = controller.settingsFailed()
    fun act(action: Action) {
        when (action) {
            Action.ENABLE -> viewModelScope.launch { controller.enable() }
            Action.CANCEL -> { renewal?.cancel(); controller.cancelStart() }
            Action.RENEW -> if (renewal?.isActive != true) {
                returnFocus = "persistence-renew"
                renewal = viewModelScope.launch { controller.renewAuthorization() }
            }
            Action.DISABLE_PERSISTENCE -> {
                returnFocus = null
                viewModelScope.launch { controller.disable() }
            }
            Action.REAUTHORIZE -> viewModelScope.launch { controller.retryAuthorization() }
            Action.REFRESH -> refresh(showProgress = true)
            Action.EXPIRY -> {
                returnFocus = "persistence-expiry"
                expiry.value = true
            }
            Action.DISABLE_EXPIRY -> viewModelScope.launch { controller.disableExpiry() }
            Action.BACK -> expiry.value = false
            else -> Unit
        }
    }
    val optional get() = expiry.value
    companion object {
        fun present(state: PersistenceUi, optional: Boolean, focus: String?, start: StartState = StartState()): PersistenceScreenUi {
            val starting = !optional && start.phase in listOf(Phase.CONNECTING, Phase.AUTHORIZING, Phase.LAUNCHING)
            val renewing = !optional && state.renewing
            val controls = if (optional) expiryControls(state) else if (starting || renewing) buildList {
                add(Control("persistence-cancel", R.string.cancel, Action.CANCEL))
                if (starting && start.phase == Phase.AUTHORIZING) add(Control("persistence-reauthorize", R.string.retry_authorization, Action.REAUTHORIZE))
            } else persistenceControls(state)
            val body = if (optional) when {
                state.error -> R.string.expiry_error
                state.expiry == 0L -> R.string.expiry_disabled
                else -> R.string.expiry_body
            } else when {
                renewing -> R.string.auth_body
                starting && start.phase == Phase.AUTHORIZING -> R.string.auth_body
                starting -> R.string.starting_body
                state.settingsError -> R.string.error_settings
                state.loading -> R.string.checking_body
                state.error -> R.string.persistence_error
                state.setup && !start.developer -> R.string.setup_developer_body
                state.setup -> R.string.setup_debug_body
                !state.enabled -> R.string.persistence_disclosure
                state.keyRefresh == KeyRefreshOutcome.AUTHORIZATION_REQUIRED -> R.string.persistence_key_required
                state.keyRefresh == KeyRefreshOutcome.FAILED -> R.string.persistence_key_refresh_failed
                state.outcome == RestartOutcome.RUNNING && start.phase == Phase.RUNNING -> R.string.persistence_started
                state.ready -> R.string.persistence_ready
                state.unsupported -> R.string.persistence_standard
                else -> R.string.persistence_unverified
            }
            val wanted = if (optional) "expiry-keep" else FocusPolicy.restore(focus ?: controls.first().id, controls.map { it.id })
            return PersistenceScreenUi(
                screen = if (optional) Screen.EXPIRY else Screen.PERSISTENCE,
                title = when {
                    optional -> R.string.expiry_title
                    renewing || starting && start.phase == Phase.AUTHORIZING -> R.string.auth_title
                    state.setup && !state.loading && !state.error && !state.settingsError -> if (start.developer) R.string.setup_debug_title else R.string.setup_developer_title
                    else -> R.string.persistence_title
                },
                body = body,
                controls = controls,
                focus = wanted,
                details = if (optional) PersistenceDetails.Expiry(state.expiry)
                else PersistenceDetails.Modes(mode(state.uid), mode(state.pkg)),
                busy = state.loading || starting,
            )
        }

        private fun mode(value: Preparation) = when (value) {
            Preparation.READY -> R.string.mode_allowed
            Preparation.UNSUPPORTED -> R.string.mode_unsupported
            Preparation.DENIED -> R.string.mode_denied
            else -> R.string.mode_unknown
        }

        private fun expiryControls(state: PersistenceUi): List<Control> = buildList {
            add(Control("expiry-keep", R.string.expiry_keep, Action.BACK))
            if (!state.loading && state.expiry != 0L) {
                add(Control("expiry-disable", R.string.expiry_disable, Action.DISABLE_EXPIRY))
            }
            add(Control("expiry-refresh", R.string.refresh, Action.REFRESH))
        }

        private fun persistenceControls(state: PersistenceUi): List<Control> {
            val label = when {
                state.setup -> R.string.open_settings
                state.enabled || state.error -> R.string.persistence_repair
                else -> R.string.enable
            }
            val renew = state.enabled && !state.setup &&
                state.keyRefresh in listOf(KeyRefreshOutcome.AUTHORIZATION_REQUIRED, KeyRefreshOutcome.FAILED)
            return buildList {
                if (renew) add(Control("persistence-renew", R.string.persistence_renew, Action.RENEW))
                add(Control("persistence-enable", label, if (state.setup) Action.SETTINGS else Action.ENABLE))
                add(Control("persistence-expiry", R.string.expiry_title, Action.EXPIRY))
                add(Control("persistence-refresh", R.string.refresh, Action.REFRESH))
                if (state.enabled) add(Control("persistence-disable", R.string.persistence_disable, Action.DISABLE_PERSISTENCE))
                add(Control("persistence-back", R.string.return_home, Action.BACK))
            }
        }
    }
}
