/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.sygix.tsuyu.R
import fr.sygix.tsuyu.ui.Action
import fr.sygix.tsuyu.ui.Control
import fr.sygix.tsuyu.ui.Screen
import fr.sygix.tsuyu.ui.ScreenUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UpdateScreenUi(
    override val title: Int,
    override val body: Int,
    override val controls: List<Control>,
    override val focus: String,
    override val titleArgs: List<Any> = emptyList(),
    override val bodyArgs: List<Any> = emptyList(),
    override val busy: Boolean = false,
    val checkedAt: Long? = null,
    val attention: Boolean = false,
    val notes: String? = null,
) : ScreenUi {
    override val screen = Screen.UPDATES
}

class UpdateViewModel(
    private val updates: UpdateController,
    running: Flow<Boolean>,
    private val prepared: suspend () -> Boolean,
) : ViewModel() {
    private val focus = MutableStateFlow<String?>(null)
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val preparation: SharedFlow<Unit> = events
    private var afterPreparation: Action? = null
    val ui = combine(updates.state, running, focus) { state, live, wanted -> present(state, live, wanted) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, present(UpdateUi(), false, null))

    fun act(action: Action) {
        focus.value = ids[action]
        viewModelScope.launch {
            when (action) {
                Action.CHECK -> updates.check(true)
                Action.UPDATE -> if (ready()) updates.update() else prepare(action)
                Action.AUTO -> if (updates.state.value.auto) updates.setAuto(false) else if (ready()) updates.setAuto(true) else prepare(action)
                Action.PRERELEASES -> updates.setPrereleases(!updates.state.value.prereleases)
                else -> Unit
            }
        }
    }

    fun resumed() {
        val pending = afterPreparation ?: return
        afterPreparation = null
        viewModelScope.launch {
            if (!ready()) return@launch
            when (pending) {
                Action.UPDATE -> updates.update()
                Action.AUTO -> updates.setAuto(true)
                else -> Unit
            }
        }
    }

    private fun prepare(action: Action) {
        afterPreparation = action
        events.tryEmit(Unit)
    }

    private suspend fun ready(): Boolean = try { prepared() } catch (cancelled: CancellationException) {
        if (cancelled is kotlinx.coroutines.TimeoutCancellationException) false else throw cancelled
    } catch (failure: Exception) { false }

    companion object {
        private val ids = mapOf(Action.UPDATE to "update-install", Action.CHECK to "update-check", Action.AUTO to "update-auto", Action.PRERELEASES to "update-prereleases")
        private val installCauses = setOf(UpdateCause.WITHDRAWN, UpdateCause.SIZE, UpdateCause.DIGEST, UpdateCause.PACKAGE, UpdateCause.VERSION, UpdateCause.CERTIFICATE, UpdateCause.INSTALLER, UpdateCause.SERVICE, UpdateCause.PREPARATION)

        fun cause(value: UpdateCause): Int = when (value) {
            UpdateCause.NETWORK -> R.string.update_cause_network
            UpdateCause.TIMEOUT -> R.string.update_cause_timeout
            UpdateCause.UNREADABLE -> R.string.update_cause_unreadable
            UpdateCause.LIMITED -> R.string.update_cause_limited
            UpdateCause.WITHDRAWN -> R.string.update_cause_withdrawn
            UpdateCause.SIZE -> R.string.update_cause_size
            UpdateCause.DIGEST -> R.string.update_cause_digest
            UpdateCause.PACKAGE -> R.string.update_cause_package
            UpdateCause.VERSION -> R.string.update_cause_version
            UpdateCause.CERTIFICATE -> R.string.update_cause_certificate
            UpdateCause.INSTALLER -> R.string.update_cause_installer
            UpdateCause.SERVICE -> R.string.update_start_service
            UpdateCause.PREPARATION -> R.string.update_cause_preparation
            UpdateCause.BUSY -> R.string.error_busy
        }

        fun present(state: UpdateUi, running: Boolean, wanted: String?): UpdateScreenUi {
            val offer = state.offer
            val phase = state.phase
            val busy = phase in listOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.INSTALLING)
            val failure = state.result?.let { result -> UpdateCause.entries.firstOrNull { it.name == result } }
            val installing = failure in installCauses
            var titleArgs: List<Any> = emptyList()
            var bodyArgs: List<Any> = emptyList()
            val (title, body) = when {
                phase == UpdatePhase.CHECKING -> R.string.update_checking_title to R.string.update_checking_body
                phase == UpdatePhase.DOWNLOADING && offer != null -> { titleArgs = listOf(offer.version); R.string.update_to_title to R.string.update_downloading_body }
                phase == UpdatePhase.INSTALLING && offer != null -> { titleArgs = listOf(offer.version); R.string.update_to_title to R.string.update_installing_body }
                phase == UpdatePhase.WAITING && offer != null -> { bodyArgs = listOf(offer.version); R.string.update_waiting_title to R.string.update_waiting_body }
                offer != null && failure != null && installing -> R.string.update_failed_title to cause(failure)
                offer != null && !running -> { titleArgs = listOf(offer.version); R.string.update_available_title to R.string.update_start_service }
                offer != null -> { titleArgs = listOf(offer.version); bodyArgs = listOf(state.installed); R.string.update_available_title to R.string.update_available_body }
                failure != null && !installing -> R.string.update_check_failed_title to cause(failure)
                failure != null -> R.string.update_failed_title to cause(failure)
                state.result == UpdateController.UP_TO_DATE -> { bodyArgs = listOf(state.installed); R.string.update_current_title to R.string.update_current_body }
                else -> { bodyArgs = listOf(state.installed); R.string.updates_title to R.string.update_never_body }
            }
            val idle = !busy
            val controls = listOfNotNull(
                offer?.let { Control("update-install", R.string.update_install, Action.UPDATE, listOf(it.version), enabled = idle && running) },
                Control("update-check", R.string.update_check, Action.CHECK, enabled = idle),
                Control("update-auto", R.string.update_auto, Action.AUTO, listOf(if (state.auto) R.string.toggle_on else R.string.toggle_off), enabled = phase != UpdatePhase.INSTALLING),
                Control("update-prereleases", R.string.update_prereleases, Action.PRERELEASES, listOf(if (state.prereleases) R.string.toggle_on else R.string.toggle_off), enabled = phase != UpdatePhase.INSTALLING),
                Control("update-back", R.string.update_back, Action.BACK),
            )
            val available = controls.filter { it.enabled }.map { it.id }
            val focus = when {
                wanted != null && wanted in available -> wanted
                wanted == null && "update-install" in available -> "update-install"
                "update-check" in available -> "update-check"
                else -> "update-back"
            }
            return UpdateScreenUi(
                title, body, controls, focus, titleArgs, bodyArgs, busy,
                checkedAt = state.checkedAt, attention = failure != null, notes = offer?.page,
            )
        }
    }
}
