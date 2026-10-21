package com.motionloop.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.motionloop.app.ui.theme.MlColors

/** Draws a tile preview at loop time [t] in 0..1. Plain drawing, no composable calls. */
typealias TilePreview = DrawScope.(t: Float) -> Unit

/**
 * Square effect choice: a small drawn preview with the name under it. The selected tile has a
 * violet frame and its preview plays, so it is clear what is chosen and what it does.
 */
@Composable
fun EffectTile(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    preview: TilePreview,
) {
    val shape = RoundedCornerShape(14.dp)
    // Only the selected tile animates: a row of always-running previews costs battery for nothing.
    val t = if (selected) {
        val transition = rememberInfiniteTransition(label = "tile")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "t",
        )
        value
    } else {
        STILL_T
    }
    Column(
        modifier
            .width(TILE_SIZE)
            .alpha(if (enabled) 1f else 0.4f)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(TILE_SIZE)
                .clip(shape)
                .background(MlColors.SurfaceHigh)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) MlColors.Violet else MlColors.SurfaceHigh, shape),
        ) {
            Canvas(Modifier.size(TILE_SIZE)) { preview(t) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MlColors.TextPrimary else MlColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A horizontally scrolling row of tiles, with the scroll bar that shows there are more. */
@Composable
fun TileRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .horizontalScrollWithBar(rememberScrollState())
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

val TILE_SIZE = 64.dp

/** Where an unselected preview stands still: mid-motion, so it still shows what it does. */
private const val STILL_T = 0.35f

/** Background for previews that show an effect on a photo. Fixed colors: the tile is a picture. */
fun DrawScope.tileScene(skyTop: Color = Color(0xFF5B7BD5), skyLow: Color = Color(0xFFF4B58C)) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(skyTop, skyLow), endY = h * 0.62f))
    val hills = Path().apply {
        moveTo(0f, h * 0.62f)
        lineTo(0f, h * 0.45f); lineTo(w * 0.3f, h * 0.32f); lineTo(w * 0.55f, h * 0.5f)
        lineTo(w * 0.78f, h * 0.36f); lineTo(w, h * 0.48f); lineTo(w, h * 0.62f)
        close()
    }
    drawPath(hills, Color(0xFF2F3B66))
    drawRect(Color(0xFF2B6A92), topLeft = Offset(0f, h * 0.62f), size = Size(w, h * 0.38f))
}
