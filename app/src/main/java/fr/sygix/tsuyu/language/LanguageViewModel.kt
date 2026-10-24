/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LanguageUi(val language: LanguageState? = null, val error: Boolean = false)

class LanguageViewModel(private val gateway: AppLanguageGateway) : ViewModel() {
    private val mutable = MutableStateFlow(LanguageUi())
    val ui = mutable.asStateFlow()

    fun refresh() = perform { gateway.current() }

    fun select(language: AppLanguage?) = perform {
        gateway.select(language)
        gateway.current()
    }

    private fun perform(operation: () -> LanguageState) {
        try { mutable.value = LanguageUi(operation()) }
        catch (failure: Exception) {
            Log.e("Tsuyu", "Language preference unavailable", failure)
            mutable.value = mutable.value.copy(error = true)
        }
    }
}
