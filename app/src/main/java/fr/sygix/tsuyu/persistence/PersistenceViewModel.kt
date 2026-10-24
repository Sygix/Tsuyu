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

class PersistenceViewModel(private val controller: PersistenceController) : ViewModel() {
    private val expiry = MutableStateFlow(false)
    private var returnFocus = "persistence-enable"
    val ui = combine(controller.state, expiry) { state, optional -> present(state, optional, returnFocus) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, present(PersistenceUi(), false, returnFocus))
    fun refresh() {
        viewModelScope.launch { controller.refresh() }
    }
    fun settingsFailed() = controller.settingsFailed()
    fun act(action: Action) {
        when (action) {
            Action.ENABLE -> viewModelScope.launch { controller.enable() }
            Action.REFRESH -> refresh()
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
        fun present(state: PersistenceUi, optional: Boolean, focus: String): PersistenceScreenUi {
            val controls = if (optional) expiryControls(state) else persistenceControls(state)
            val body = if (optional) when {
                state.error -> R.string.expiry_error
                state.expiry == 0L -> R.string.expiry_disabled
                else -> R.string.expiry_body
            } else when {
                state.settingsError -> R.string.error_settings
                state.loading -> R.string.checking_body
                state.error -> R.string.persistence_error
                !state.enabled -> R.string.persistence_disclosure
                state.ready -> R.string.persistence_ready
                state.unsupported -> R.string.persistence_standard
                else -> R.string.persistence_unverified
            }
            val wanted = if (optional) "expiry-keep" else FocusPolicy.restore(focus, controls.map { it.id })
            return PersistenceScreenUi(
                screen = if (optional) Screen.EXPIRY else Screen.PERSISTENCE,
                title = if (optional) R.string.expiry_title else R.string.persistence_title,
                body = body,
                controls = controls,
                focus = wanted,
                details = if (optional) PersistenceDetails.Expiry(state.expiry)
                else PersistenceDetails.Modes(mode(state.uid), mode(state.pkg)),
                busy = state.loading,
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
            return listOf(
                Control("persistence-enable", label, if (state.setup) Action.SETTINGS else Action.ENABLE),
                Control("persistence-expiry", R.string.expiry_title, Action.EXPIRY),
                Control("persistence-refresh", R.string.refresh, Action.REFRESH),
                Control("persistence-back", R.string.return_home, Action.BACK),
            )
        }
    }
}
