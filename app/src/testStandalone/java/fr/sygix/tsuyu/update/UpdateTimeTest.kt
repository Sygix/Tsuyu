/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class UpdateTimeTest {
    @Test fun datesFollowTheFirstSupportedUiLocaleOrEnglishFallback() {
        val timestamp = 1_792_872_000_000L
        fun expected(locale: Locale) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(timestamp))
        assertEquals(expected(Locale.ENGLISH), formatUpdateTime(timestamp, listOf(Locale.JAPAN)))
        assertEquals(expected(Locale.CANADA_FRENCH), formatUpdateTime(timestamp, listOf(Locale.JAPAN, Locale.CANADA_FRENCH, Locale.GERMANY)))
        assertEquals(expected(Locale.ENGLISH), formatUpdateTime(timestamp, emptyList()))
    }
}
