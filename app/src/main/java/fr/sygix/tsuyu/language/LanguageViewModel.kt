/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LanguageUi(val language: LanguageState? = null, val error: Boolean = false, val loading: Boolean = false, val uncertain: Boolean = false)

class LanguageViewModel(private val gateway: AppLanguageGateway) : ViewModel() {
    private val mutable = MutableStateFlow(LanguageUi())
    val ui = mutable.asStateFlow()

    fun refresh() = perform { gateway.current() }

    fun select(language: AppLanguage?) = perform(write = true) {
        gateway.select(language)
        gateway.current()
    }

    private fun perform(write: Boolean = false, operation: suspend () -> LanguageState) {
        if (mutable.value.loading || (write && mutable.value.uncertain)) return
        mutable.value = mutable.value.copy(loading = true, error = false)
        viewModelScope.launch {
            try { mutable.value = LanguageUi(operation()) }
            catch (failure: Exception) {
                if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                Log.e("Tsuyu", "Language preference unavailable", failure)
                mutable.value = mutable.value.copy(error = true, loading = false,
                    uncertain = write || mutable.value.uncertain || failure is LanguageWriteUncertain)
            }
        }
    }
}
