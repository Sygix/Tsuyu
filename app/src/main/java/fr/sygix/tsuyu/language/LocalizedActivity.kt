/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import fr.sygix.tsuyu.R
import fr.sygix.tsuyu.ui.theme.*
import kotlinx.coroutines.launch

abstract class LocalizedActivity : ComponentActivity() {
    protected lateinit var languageModel: LanguageViewModel
    private var localized: Context? = null

    override fun getResources(): Resources = localized?.resources ?: super.getResources()

    protected open fun languageGateway(): AppLanguageGateway = AndroidAppLanguageGateway(applicationContext)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LanguageViewModel(languageGateway()) as T
            }
        })[LanguageViewModel::class.java]
        lifecycleScope.launch {
            var applied: LanguageState? = null
            languageModel.ui.collect { ui ->
                val state = ui.language ?: return@collect
                val previous = applied
                if (previous != null && previous != state) {
                    recreate()
                    return@collect
                }
                val config = Configuration(baseContext.resources.configuration)
                config.setLocales(LocaleList.forLanguageTags(state.effective.tag))
                localized = baseContext.createConfigurationContext(config)
                applied = state
            }
        }
        languageModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        languageModel.refresh()
    }

    protected fun setLocalizedContent(content: @Composable () -> Unit) {
        setContent {
            val ui = languageModel.ui.collectAsStateWithLifecycle().value
            val state = ui.language
            if (state == null || ui.uncertain) {
                LanguageStartup(ui, languageModel::refresh) { finish() }
            } else {
                val context = remember(state, LocalConfiguration.current) {
                    val config = Configuration(baseContext.resources.configuration)
                    config.setLocales(LocaleList.forLanguageTags(state.effective.tag))
                    baseContext.createConfigurationContext(config)
                }
                CompositionLocalProvider(LocalContext provides context,
                    LocalConfiguration provides context.resources.configuration,
                    LocalResources provides context.resources) { content() }
            }
        }
    }
}

@Composable
private fun LanguageStartup(ui: LanguageUi, retry: () -> Unit, back: () -> Unit) {
    val focus = remember { FocusRequester() }
    BackHandler(onBack = back)
    TsuyuTheme {
        Column(Modifier.fillMaxSize().background(TsuyuColors.background)
            .padding(horizontal = TsuyuSpacing.horizontal, vertical = TsuyuSpacing.vertical)
            .testTag("language-startup"), verticalArrangement = Arrangement.spacedBy(TsuyuSpacing.gap)) {
            BrandHeader(false)
            if (ui.error) {
                StatusSurface(stringResource(if (ui.uncertain) R.string.language_uncertain else R.string.language_error), StatusKind.ATTENTION)
                TvAction(stringResource(R.string.retry), retry,
                    Modifier.focusRequester(focus).testTag("language-startup-retry"))
            } else OperationProgress()
        }
        LaunchedEffect(ui.error) { if (ui.error) focus.requestFocus(FocusDirection.Enter) }
    }
}
