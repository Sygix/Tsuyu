/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.sygix.tsuyu.R
import fr.sygix.tsuyu.start.*
import fr.sygix.tsuyu.persistence.PersistenceUi
import fr.sygix.tsuyu.persistence.RestartOutcome
import fr.sygix.tsuyu.persistence.KeyRefreshOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin

enum class Screen { HOME, SETUP, HELP, DETAILS, PERSISTENCE, EXPIRY, UPDATES }
enum class Action { ENABLE, START, CANCEL, REAUTHORIZE, SETTINGS, HELP, DETAILS, BACK, REFRESH, APPS, PERSISTENCE, EXPIRY, DISABLE_EXPIRY, RETURN, UPDATES, UPDATE, CHECK, AUTO, PRERELEASES, LANGUAGE }
data class Control(val id: String, val label: Int, val action: Action, val args: List<Any> = emptyList(), val enabled: Boolean = true)
interface ScreenUi {
    val screen: Screen
    val title: Int
    val body: Int
    val controls: List<Control>
    val focus: String
    val titleArgs: List<Any> get() = emptyList()
    val bodyArgs: List<Any> get() = emptyList()
    val busy: Boolean get() = false
}
data class HomeUi(
    override val screen: Screen,
    override val title: Int,
    override val body: Int,
    override val controls: List<Control>,
    override val focus: String,
    val persistenceStatus: Int? = null,
    val persistenceAttention: Boolean = false,
    val notice: String? = null,
    val updateBlocked: Boolean = false,
    override val busy: Boolean = false,
) : ScreenUi
private data class Navigation(val screen: Screen = Screen.HOME, val returnFocus: String = "action-main")

object FocusPolicy {
    fun restore(wanted: String, controls: List<String>): String = if (wanted in controls) wanted else controls.first()
}

class HomeViewModel(
    private val coordinator: StartCoordinator,
    compatibility: StateFlow<Boolean> = MutableStateFlow(true),
    persistence: StateFlow<PersistenceUi> = MutableStateFlow(PersistenceUi()),
    notice: StateFlow<String?> = MutableStateFlow(null),
    updateBlocked: kotlinx.coroutines.flow.Flow<Boolean> = MutableStateFlow(false),
    private val recovered: suspend () -> Unit = {},
) : ViewModel() {
    private val navigation = MutableStateFlow(Navigation())
    private val caller = MutableStateFlow<String?>(null)
    private var startup: Job? = null
    private val extras = combine(caller, notice, updateBlocked) { label, version, blocked -> Triple(label, version, blocked) }
    val ui = combine(coordinator.state, navigation, compatibility, persistence, extras) { state, nav, compatible, preparation, (label, version, blocked) ->
        val settled = !preparation.loading && preparation.enabledKnown && !preparation.error && !preparation.outcome.failed &&
            preparation.enabled && (preparation.ready || preparation.unsupported)
        val returning = label.takeIf { settled }
        val presentation = present(state, nav.screen, nav.returnFocus, returning)
        val failed = preparation.outcome.failed || preparation.keyRefresh in listOf(KeyRefreshOutcome.AUTHORIZATION_REQUIRED, KeyRefreshOutcome.FAILED)
        val verified = !preparation.loading && !preparation.error
        val status = when {
            preparation.keyRefresh == KeyRefreshOutcome.AUTHORIZATION_REQUIRED -> R.string.persistence_key_required
            preparation.keyRefresh == KeyRefreshOutcome.FAILED -> R.string.persistence_key_refresh_failed
            preparation.outcome == RestartOutcome.AUTHENTICATION -> R.string.persistence_key_required
            failed -> R.string.persistence_boot_failed
            verified && preparation.enabled && preparation.outcome == RestartOutcome.RUNNING && state.phase == Phase.RUNNING -> R.string.persistence_home_started
            verified && preparation.ready && preparation.enabled -> R.string.persistence_home_ready
            verified && preparation.unsupported && preparation.enabled -> R.string.persistence_home_standard
            else -> R.string.persistence_home_unverified
        }
        val compatibilityError = !compatible && state.phase == Phase.RUNNING &&
            nav.screen in listOf(Screen.HOME, Screen.DETAILS)
        presentation.copy(
            body = if (compatibilityError) R.string.compatibility_error else presentation.body,
            persistenceStatus = status,
            persistenceAttention = failed,
            notice = version.takeIf { nav.screen == Screen.HOME },
            updateBlocked = blocked && nav.screen == Screen.HOME,
            focus = if (failed && !presentation.busy && nav.screen == Screen.HOME && nav.returnFocus == "action-main") "action-persistence" else presentation.focus,
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, present(StartState(), Screen.HOME, "action-main"))
    fun resume() {
        viewModelScope.launch {
            coordinator.refresh()
            if (navigation.value.screen == Screen.SETUP && coordinator.state.value.phase == Phase.STOPPED) start()
        }
    }
    fun died() {
        coordinator.died()
        if (navigation.value.screen == Screen.HOME && navigation.value.returnFocus != "action-language") navigation.value = navigation.value.copy(returnFocus = "action-main")
    }
    fun settingsFailed() = coordinator.settingsFailed()
    fun caller(label: String?) { caller.value = label }
    fun persistenceEntry(fromMain: Boolean) {
        cancelPending()
        navigation.value = navigation.value.copy(returnFocus = if (fromMain) "action-main" else "action-persistence")
    }
    fun cancelPending() { coordinator.cancel(); startup?.cancel() }
    fun back() {
        cancelPending()
        navigation.value = navigation.value.copy(screen = Screen.HOME)
        resume()
    }
    fun act(action: Action) {
        when (action) {
            Action.ENABLE -> navigation.value = Navigation(Screen.SETUP)
            Action.START -> start()
            Action.CANCEL -> { cancelPending(); navigation.value = Navigation(); resume() }
            Action.REAUTHORIZE -> viewModelScope.launch {
                startup?.cancelAndJoin()
                coordinator.cancelAndJoin()
                start()
            }
            Action.HELP -> { cancelPending(); navigation.value = Navigation(Screen.HELP, "action-help") }
            Action.DETAILS -> navigation.value = Navigation(Screen.DETAILS, "action-main")
            Action.BACK -> back()
            Action.REFRESH -> resume()
            Action.SETTINGS -> Unit
            Action.APPS -> navigation.value = navigation.value.copy(returnFocus = "action-apps")
            Action.PERSISTENCE -> navigation.value = navigation.value.copy(returnFocus = "action-persistence")
            Action.UPDATES -> navigation.value = navigation.value.copy(returnFocus = "action-updates")
            Action.LANGUAGE -> navigation.value = navigation.value.copy(returnFocus = "action-language")
            Action.EXPIRY, Action.DISABLE_EXPIRY, Action.RETURN, Action.UPDATE, Action.CHECK, Action.AUTO, Action.PRERELEASES -> Unit
        }
    }
    private fun start() {
        if (startup?.isActive == true) return
        navigation.value = navigation.value.copy(returnFocus = "action-main")
        startup = viewModelScope.launch {
            coordinator.start()
            if (coordinator.state.value.phase == Phase.RUNNING) {
                recovered()
                navigation.value = Navigation()
            }
        }
    }
    companion object {
        fun present(state: StartState, screen: Screen, restore: String, caller: String? = null): HomeUi {
            val main: Control
            val title: Int
            val body: Int
            when (screen) {
                Screen.HELP -> {
                    title = R.string.help_title; body = R.string.help_body
                    main = Control("action-back", R.string.return_home, Action.BACK)
                }
                Screen.DETAILS -> {
                    title = R.string.details_title
                    body = if (state.phase == Phase.RUNNING) R.string.details_running else R.string.details_stopped
                    main = Control("action-back", R.string.return_home, Action.BACK)
                }
                else -> when {
                    state.phase == Phase.RUNNING -> {
                        title = R.string.running_title; body = R.string.running_body
                        main = Control("action-main", R.string.details_title, Action.DETAILS)
                    }
                    state.phase in listOf(Phase.CONNECTING, Phase.AUTHORIZING, Phase.LAUNCHING) -> {
                        title = if (state.phase == Phase.AUTHORIZING) R.string.auth_title else R.string.starting_title
                        body = if (state.phase == Phase.AUTHORIZING) R.string.auth_body else R.string.starting_body
                        main = Control("action-cancel", R.string.cancel, Action.CANCEL)
                    }
                    state.phase == Phase.ERROR -> {
                        title = R.string.error_title
                        body = when (state.failure) {
                            Failure.SETTINGS -> R.string.error_settings
                            Failure.ENDPOINT, Failure.UNSUPPORTED -> R.string.error_endpoint
                            Failure.KEY -> R.string.error_key
                            Failure.AUTHENTICATION -> R.string.error_auth
                            Failure.TIMEOUT -> R.string.error_timeout
                            Failure.BUSY -> R.string.error_busy
                            else -> R.string.error_launch
                        }
                        main = Control("action-main", R.string.retry, if (state.failure == Failure.SETTINGS) Action.SETTINGS else Action.START)
                    }
                    state.phase == Phase.SETUP && state.debugging == Debugging.UNKNOWN -> {
                        title = R.string.setup_title; body = R.string.error_unknown_settings
                        main = Control("action-main", R.string.open_settings, Action.SETTINGS)
                    }
                    screen == Screen.SETUP && state.debugging != Debugging.ENABLED -> {
                        title = if (state.developer) R.string.setup_debug_title else R.string.setup_developer_title
                        body = if (state.developer) R.string.setup_debug_body else R.string.setup_developer_body
                        main = Control("action-settings", R.string.open_settings, Action.SETTINGS)
                    }
                    state.phase == Phase.SETUP -> {
                        title = R.string.setup_title; body = R.string.setup_body
                        main = Control("action-main", R.string.enable, Action.ENABLE)
                    }
                    state.phase == Phase.CHECKING -> {
                        title = R.string.checking_title; body = R.string.checking_body
                        main = Control("action-main", R.string.refresh, Action.REFRESH)
                    }
                    else -> {
                        title = R.string.stopped_title; body = R.string.stopped_body
                        main = Control("action-main", R.string.start, Action.START)
                    }
                }
            }
            val home = screen == Screen.HOME
            val returning = caller?.takeIf { home && state.phase == Phase.RUNNING }
            val controls = listOfNotNull(returning?.let { Control("action-return", R.string.return_to_caller, Action.RETURN, listOf(it)) }) + main +
                (if (state.phase == Phase.AUTHORIZING && screen in listOf(Screen.HOME, Screen.SETUP)) listOf(Control("action-reauthorize", R.string.retry_authorization, Action.REAUTHORIZE)) else emptyList()) +
                if (home) listOf(
                    Control("action-apps", R.string.access_apps, Action.APPS),
                    Control("action-updates", R.string.updates_title, Action.UPDATES),
                    Control("action-persistence", R.string.persistence_title, Action.PERSISTENCE),
                    Control("action-help", R.string.help_title, Action.HELP),
                    Control("action-language", R.string.language_title, Action.LANGUAGE),
                ) else emptyList()
            val wanted = when {
                !home -> main.id
                returning != null && restore == "action-main" -> "action-return"
                else -> restore
            }
            val busy = state.phase in listOf(Phase.CHECKING, Phase.CONNECTING, Phase.AUTHORIZING, Phase.LAUNCHING)
            return HomeUi(screen, title, body, controls, FocusPolicy.restore(wanted, controls.map { it.id }), busy = busy)
        }
    }
}
