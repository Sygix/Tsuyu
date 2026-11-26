/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import fr.sygix.tsuyu.language.AppLanguage
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatUpdateTime(timestamp: Long, locales: List<Locale>): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, AppLanguage.resolveLocale(locales) ?: Locale.ENGLISH).format(Date(timestamp))
