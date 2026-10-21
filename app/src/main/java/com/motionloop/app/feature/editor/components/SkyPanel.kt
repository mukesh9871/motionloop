package com.motionloop.app.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.motionloop.app.domain.model.SkySettings
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.feature.editor.EditorTool
import com.motionloop.app.ui.components.Pill
import com.motionloop.app.ui.theme.MlColors

private fun SkyStyle.label() = when (this) {
    SkyStyle.CLEAR -> "Clear"
    SkyStyle.SUNSET -> "Sunset"
    SkyStyle.NIGHT -> "Night"
    SkyStyle.STORM -> "Storm"
    SkyStyle.AURORA -> "Aurora"
    SkyStyle.PHOTO -> "Photo"
}

private val skyTools = listOf(
    EditorTool.SKY_ADD to "Tap to add",
    EditorTool.SKY_REMOVE to "Tap to remove",
    EditorTool.SKY_PAINT to "Paint",
    EditorTool.SKY_ERASE to "Erase",
)

@Composable
fun SkyPanel(
    sky: SkySettings,
    tool: EditorTool,
    busy: Boolean,
    onDetect: () -> Unit,
    onTool: (EditorTool) -> Unit,
    onChange: ((SkySettings) -> SkySettings) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(if (sky.hasArea) "Find again" else "Find sky", active = true, enabled = !busy, onClick = onDetect)
            Spacer(Modifier.weight(1f))
            Text("Replace", style = MaterialTheme.typography.labelMedium, color = MlColors.TextSecondary)
            Switch(
                checked = sky.enabled,
                onCheckedChange = { on -> onChange { it.copy(enabled = on) } },
                enabled = sky.hasArea,
                colors = SwitchDefaults.colors(checkedTrackColor = MlColors.Violet),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (sky.hasArea) "Fix the sky area: tap a region, or paint by hand." else "Find the sky, or tap it to select it.",
            style = MaterialTheme.typography.bodySmall,
            color = MlColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            skyTools.forEach { (t, label) -> Pill(label, active = t == tool, enabled = !busy) { onTool(t) } }
        }

        Spacer(Modifier.height(10.dp))
        Text("New sky", style = MaterialTheme.typography.labelMedium, color = MlColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkyStyle.entries.forEach { style ->
                val isPhoto = style == SkyStyle.PHOTO
                val label = if (isPhoto && sky.photoRev != 0 && sky.style == SkyStyle.PHOTO) "Change photo" else style.label()
                Pill(label, active = sky.style == style, enabled = !busy) {
                    // The photo style needs a photo first. Tapping it again picks a different one.
                    if (isPhoto && (sky.photoRev == 0 || sky.style == SkyStyle.PHOTO)) {
                        onPickPhoto()
                    } else {
                        onChange { it.copy(style = style, enabled = it.hasArea) }
                    }
                }
            }
        }
        LabeledSlider("Drift", sky.drift, 0f..1f, { v -> onChange { it.copy(drift = v) } })
        LabeledSlider("Edge", sky.feather, 0f..1f, { v -> onChange { it.copy(feather = v) } })
        LabeledSlider("Blend", sky.harmonize, 0f..1f, { v -> onChange { it.copy(harmonize = v) } })
    }
}

