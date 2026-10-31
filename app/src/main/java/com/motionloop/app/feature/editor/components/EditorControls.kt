package com.motionloop.app.feature.editor.components

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.motionloop.app.ui.components.MlMotion
import com.motionloop.app.ui.components.Pill
import com.motionloop.app.ui.components.pressBounce
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.motionloop.app.domain.model.LoopMode
import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.feature.editor.EditorTool
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors

/** A toolbar entry. [alt] is the second mode of the same tool, picked with [ToolModeRow]. */
private data class ToolSpec(val tool: EditorTool, val label: String, val icon: ImageVector, val alt: EditorTool? = null)

// Five entries fit a narrow phone without scrolling: paint and erase of the freeze mask, and the two
// kinds of tap-to-select, are one tool each with their mode chosen below it.
private val tools = listOf(
    ToolSpec(EditorTool.ARROW, "Motion", MlIcons.Motion),
    ToolSpec(EditorTool.ANCHOR, "Anchor", MlIcons.Anchor),
    ToolSpec(EditorTool.FREEZE, "Freeze", MlIcons.Freeze, alt = EditorTool.UNFREEZE),
    ToolSpec(EditorTool.SELECT_FREEZE, "Select", MlIcons.Select, alt = EditorTool.SELECT_ANIMATE),
    ToolSpec(EditorTool.ERASE, "Erase", MlIcons.Delete),
)

@Composable
fun ToolBar(selected: EditorTool, onSelect: (EditorTool) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val idleEdge = MlColors.SurfaceHigh
    val idleInk = MlColors.TextSecondary
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tools.forEach { spec ->
            val active = spec.tool == selected || spec.alt == selected
            val source = remember { MutableInteractionSource() }
            // The active tool is a filled violet tile, so the current mode is clear at a glance.
            val fill by animateColorAsState(if (active) MlColors.Violet else Color.Transparent, MlMotion.colorSpring(), label = "toolFill")
            val content by animateColorAsState(if (active) Color.White else idleInk, MlMotion.colorSpring(), label = "toolInk")
            Column(
                Modifier
                    .weight(1f)
                    .pressBounce(source)
                    .clip(RoundedCornerShape(14.dp))
                    .background(fill)
                    .border(1.dp, if (active) Color.Transparent else idleEdge, RoundedCornerShape(14.dp))
                    .selectable(selected = active, interactionSource = source, indication = LocalIndication.current, role = Role.Tab) {
                        // Re-tapping keeps the current mode (for example Unfreeze) instead of resetting it.
                        if (!active) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(spec.tool)
                        }
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(spec.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    spec.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The two modes of a combined tool (paint or erase the freeze mask, hold or isolate a region). Nothing for other tools. */
@Composable
fun ToolModeRow(selected: EditorTool, onSelect: (EditorTool) -> Unit, modifier: Modifier = Modifier) {
    val modes = when (selected) {
        EditorTool.FREEZE, EditorTool.UNFREEZE -> listOf(EditorTool.FREEZE to "Freeze", EditorTool.UNFREEZE to "Unfreeze")
        EditorTool.SELECT_FREEZE, EditorTool.SELECT_ANIMATE -> listOf(EditorTool.SELECT_FREEZE to "Hold still", EditorTool.SELECT_ANIMATE to "Only this moves")
        else -> return
    }
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        modes.forEach { (tool, label) -> Pill(label, active = tool == selected) { onSelect(tool) } }
    }
}

/**
 * Slider with its label and current value. With a [default], the thumb snaps to it with a light
 * tick, and tapping the label returns to it. Ranges around zero default to zero. [compact] puts
 * the value under the label, for rows that share their width with other controls.
 */
@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    default: Float? = if (range.start < 0f && range.endInclusive > 0f) 0f else null,
    format: (Float) -> String = { sliderValueText(it, range) },
    compact: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val snap = (range.endInclusive - range.start) * SNAP_FRACTION
    val changed = default != null && value != default
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val labelModifier = if (default != null) {
            Modifier.clickable(onClickLabel = "Reset $label") {
                if (value != default) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onChange(default)
                }
            }
        } else {
            Modifier
        }
        val valueColor = if (changed) MlColors.TextPrimary else MlColors.TextSecondary
        Column(Modifier.width(if (compact) 52.dp else 64.dp).then(labelModifier).padding(vertical = if (compact) 4.dp else 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MlColors.TextSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (compact) Text(format(value), style = MaterialTheme.typography.labelMedium, color = valueColor, maxLines = 1)
        }
        Slider(
            value = value,
            onValueChange = { v ->
                // A detent, not a dead zone: the thumb stops on the default when a drag crosses or reaches
                // it, and leaving it takes a small push. Values near the default stay reachable from the side.
                val next = when {
                    default == null -> v
                    value == default -> if (abs(v - default) < snap) default else v
                    (value - default) * (v - default) <= 0f -> default
                    else -> v
                }
                // One tick when the thumb lands on the default, not on every move while it stays there.
                if (default != null && next == default && value != default) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
                onChange(next)
            },
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = MlColors.Mint, activeTrackColor = MlColors.Violet),
        )
        if (!compact) {
            Text(
                format(value),
                style = MaterialTheme.typography.labelMedium,
                color = valueColor,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(40.dp),
            )
        }
    }
}

/** Angles in whole degrees, for rotation and direction sliders. */
internal fun degreeText(value: Float): String = "${value.roundToInt()}\u00B0"

/** Signed percent for ranges around zero, otherwise percent of the range. */
internal fun sliderValueText(value: Float, range: ClosedFloatingPointRange<Float>): String {
    if (range.start < 0f && range.endInclusive > 0f) {
        val pct = (value / max(-range.start, range.endInclusive) * 100f).roundToInt()
        return if (pct > 0) "+$pct" else "$pct"
    }
    val span = range.endInclusive - range.start
    return if (span <= 0f) "0" else ((value - range.start) / span * 100f).roundToInt().toString()
}

/** How far past the default a drag must go before the thumb leaves it, as a share of the range. */
private const val SNAP_FRACTION = 0.01f

@Composable
fun PlaybackBar(
    playing: Boolean,
    speed: Float,
    mode: LoopMode,
    onTogglePlay: () -> Unit,
    onSpeed: (Float) -> Unit,
    onMode: (LoopMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(52.dp)
                .pressBounce(source, pressedScale = 0.9f)
                .clip(CircleShape)
                .background(MlColors.Violet)
                .clickable(interactionSource = source, indication = LocalIndication.current) {
                    haptics.performHapticFeedback(if (playing) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onTogglePlay()
                }
                // One name for the button: during the icon crossfade both icons exist for a moment.
                .semantics { contentDescription = if (playing) "Pause" else "Play" },
            contentAlignment = Alignment.Center,
        ) {
            // The icon turns over with a small pop instead of swapping.
            AnimatedContent(
                targetState = playing,
                transitionSpec = {
                    (scaleIn(MlMotion.bouncy(), initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
                },
                label = "playIcon",
            ) { isPlaying ->
                Icon(
                    if (isPlaying) MlIcons.Pause else MlIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        LabeledSlider(
            label = "Speed",
            value = speed,
            range = LoopSettings.MIN_SPEED..LoopSettings.MAX_SPEED,
            onChange = onSpeed,
            modifier = Modifier.weight(1f),
            default = 1f,
            format = { "${(it * 10f).roundToInt() / 10f}x" },
            // The play button and the loop toggle share this row: the track needs the width.
            compact = true,
        )
        Spacer(Modifier.width(8.dp))
        ModeToggle(mode, onMode)
    }
}

@Composable
private fun ModeToggle(mode: LoopMode, onMode: (LoopMode) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val idleInk = MlColors.TextPrimary
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MlColors.SurfaceHigh)
            .selectableGroup(),
    ) {
        listOf(LoopMode.CONTINUOUS to MlIcons.Loop, LoopMode.BOOMERANG to MlIcons.Boomerang).forEach { (m, icon) ->
            val active = m == mode
            val fill by animateColorAsState(if (active) MlColors.Violet else Color.Transparent, MlMotion.colorSpring(), label = "modeFill")
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(fill)
                    .selectable(selected = active, role = Role.RadioButton) {
                        if (!active) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onMode(m)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val content = if (active) Color.White else idleInk
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Text(
                    if (m == LoopMode.CONTINUOUS) "Loop" else "Bounce",
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
