/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object TsuyuColors {
    val background = Color(0xFF10161E)
    val surface = Color(0xFF1E2936)
    val raised = Color(0xFF293645)
    val primary = Color(0xFFA8D5FF)
    val onPrimary = Color(0xFF003353)
    val text = Color(0xFFE6EDF5)
    val secondary = Color(0xFFC6D1DF)
    val outline = Color(0xFF667585)
    val error = Color(0xFFFFB4AB)
    val errorContainer = Color(0xFF493133)
}

object TsuyuSpacing {
    val horizontal = 48.dp
    val vertical = 27.dp
    val gutter = 48.dp
    val gap = 16.dp
    val actionGap = 12.dp
    val panelRadius = 12.dp
}

object TsuyuType {
    val title = 36.sp
    val body = 20.sp
    val supporting = 18.sp
    val action = 20.sp
    val brandHome = 24.sp
    val brandCompact = 18.sp
}

@Composable
fun TsuyuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TsuyuColors.primary,
            onPrimary = TsuyuColors.onPrimary,
            background = TsuyuColors.background,
            onBackground = TsuyuColors.text,
            surface = TsuyuColors.surface,
            onSurface = TsuyuColors.text,
            surfaceVariant = TsuyuColors.raised,
            onSurfaceVariant = TsuyuColors.secondary,
            border = TsuyuColors.outline,
            error = TsuyuColors.error,
            errorContainer = TsuyuColors.errorContainer,
            onErrorContainer = TsuyuColors.error,
        ),
        content = content,
    )
}
