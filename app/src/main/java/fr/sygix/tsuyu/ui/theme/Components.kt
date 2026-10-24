/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import fr.sygix.tsuyu.R

enum class StatusKind { ACTIVE, NEUTRAL, ATTENTION }

@Composable
fun BrandHeader(home: Boolean, modifier: Modifier = Modifier) {
    val mark = if (home) 72.dp else 48.dp
    Row(modifier.testTag(if (home) "brand-signature" else "brand-header"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (home) 16.dp else 12.dp)) {
        Box(Modifier.size(mark).clip(RoundedCornerShape(mark / 4)).background(Color.Black)) {
            Image(
                painterResource(R.mipmap.ic_launcher_foreground), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.5f, scaleY = 1.5f),
            )
        }
        Text(stringResource(R.string.app_name), color = TsuyuColors.primary, fontSize = if (home) TsuyuType.brandHome else TsuyuType.brandCompact)
    }
}

@Composable
fun TvAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = TsuyuColors.surface, contentColor = TsuyuColors.text,
            focusedContainerColor = TsuyuColors.primary, focusedContentColor = TsuyuColors.onPrimary,
            pressedContainerColor = TsuyuColors.primary, pressedContentColor = TsuyuColors.onPrimary,
            disabledContainerColor = TsuyuColors.surface.copy(alpha = 0.5f), disabledContentColor = TsuyuColors.secondary.copy(alpha = 0.6f),
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.03f),
    ) { Text(label, fontSize = TsuyuType.action) }
}

@Composable
fun StatusSurface(text: String, kind: StatusKind, modifier: Modifier = Modifier) {
    val (container, content) = when (kind) {
        StatusKind.ATTENTION -> TsuyuColors.errorContainer to TsuyuColors.error
        StatusKind.ACTIVE -> TsuyuColors.surface to TsuyuColors.primary
        StatusKind.NEUTRAL -> TsuyuColors.surface to TsuyuColors.outline
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(TsuyuSpacing.panelRadius)).background(container).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(content))
        Text(text, color = if (kind == StatusKind.ATTENTION) TsuyuColors.error else TsuyuColors.secondary, fontSize = TsuyuType.supporting)
    }
}

@Composable
fun SupportingPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(TsuyuSpacing.panelRadius)).background(TsuyuColors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable
fun OperationProgress(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "progress")
    val offset by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "offset")
    BoxWithConstraints(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(TsuyuColors.raised).testTag("operation-progress")) {
        val segment = maxWidth * 0.35f
        Box(Modifier.width(segment).fillMaxHeight().offset(x = (maxWidth + segment) * offset - segment).background(TsuyuColors.primary))
    }
}
