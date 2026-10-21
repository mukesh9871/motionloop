package com.motionloop.app.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
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
import com.motionloop.app.ui.components.horizontalScrollWithBar
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors

private data class ToolSpec(val tool: EditorTool, val label: String, val icon: ImageVector)

private val tools = listOf(
    ToolSpec(EditorTool.ARROW, "Motion", MlIcons.Motion),
    ToolSpec(EditorTool.ANCHOR, "Anchor", MlIcons.Anchor),
    ToolSpec(EditorTool.FREEZE, "Freeze", MlIcons.Freeze),
    ToolSpec(EditorTool.UNFREEZE, "Unfreeze", MlIcons.Unfreeze),
    ToolSpec(EditorTool.ERASE, "Erase", MlIcons.Delete),
    ToolSpec(EditorTool.SELECT_FREEZE, "Hold", MlIcons.Select),
    ToolSpec(EditorTool.SELECT_ANIMATE, "Only this", MlIcons.Focus),
)

@Composable
fun ToolBar(selected: EditorTool, onSelect: (EditorTool) -> Unit, modifier: Modifier = Modifier) {
    // Seven tools do not fit narrow phones: the row scrolls, and its bar shows there is more.
    Row(modifier.fillMaxWidth().horizontalScrollWithBar(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tools.forEach { spec ->
            val active = spec.tool == selected
            // The active tool is a filled violet tile, so the current mode is clear at a glance.
            val content = if (active) Color.White else MlColors.TextSecondary
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) MlColors.Violet else Color.Transparent)
                    .border(1.dp, if (active) Color.Transparent else MlColors.SurfaceHigh, RoundedCornerShape(14.dp))
                    .selectable(selected = active, role = Role.Tab) { onSelect(spec.tool) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(spec.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Text(spec.label, style = MaterialTheme.typography.labelMedium, color = content)
            }
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MlColors.TextSecondary,
            modifier = Modifier.width(64.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = MlColors.Mint, activeTrackColor = MlColors.Violet),
        )
    }
}

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
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (playing) MlIcons.Pause else MlIcons.Play,
            contentDescription = if (playing) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MlColors.Violet)
                .clickable(onClick = onTogglePlay)
                .padding(14.dp),
        )
        Spacer(Modifier.width(12.dp))
        LabeledSlider(
            label = "Speed",
            value = speed,
            range = LoopSettings.MIN_SPEED..LoopSettings.MAX_SPEED,
            onChange = onSpeed,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        ModeToggle(mode, onMode)
    }
}

@Composable
private fun ModeToggle(mode: LoopMode, onMode: (LoopMode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MlColors.SurfaceHigh),
    ) {
        listOf(LoopMode.CONTINUOUS to MlIcons.Loop, LoopMode.BOOMERANG to MlIcons.Boomerang).forEach { (m, icon) ->
            val active = m == mode
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MlColors.Violet else Color.Transparent)
                    .selectable(selected = active, role = Role.RadioButton) { onMode(m) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val content = if (active) Color.White else MlColors.TextPrimary
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
