package com.motionloop.app.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.motionloop.app.domain.model.CameraPath
import com.motionloop.app.domain.model.ParallaxSettings
import com.motionloop.app.ui.components.Pill
import com.motionloop.app.ui.theme.MlColors

private fun CameraPath.label() = when (this) {
    CameraPath.CIRCLE -> "Circle"
    CameraPath.SWAY -> "Sway"
    CameraPath.ZOOM -> "Zoom"
    CameraPath.TILT -> "Tilt"
    CameraPath.FLOAT -> "Float"
}

@Composable
fun DepthPanel(
    settings: ParallaxSettings,
    busy: Boolean,
    onCreateDepth: () -> Unit,
    onChange: ((ParallaxSettings) -> ParallaxSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (settings.depthRev == 0) {
            Text(
                "3D motion needs a depth map of the photo. It is made on your phone and takes a few seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = MlColors.TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Pill("Create depth map", active = true, enabled = !busy, onClick = onCreateDepth)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("3D camera", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.enabled,
                onCheckedChange = { on -> onChange { it.copy(enabled = on) } },
                colors = SwitchDefaults.colors(checkedTrackColor = MlColors.Violet),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CameraPath.entries.forEach { path ->
                Pill(path.label(), active = settings.path == path, enabled = !busy) {
                    onChange { it.copy(path = path, enabled = true) }
                }
            }
        }
        LabeledSlider("Amount", settings.amount, 0f..1f, { v -> onChange { it.copy(amount = v) } })
        LabeledSlider("Focus", settings.focus, 0f..1f, { v -> onChange { it.copy(focus = v) } })
        Text(
            "Focus picks the depth that stays still: low keeps the background still, high keeps the subject still.",
            style = MaterialTheme.typography.bodySmall,
            color = MlColors.TextSecondary,
        )
    }
}

