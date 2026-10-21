package com.motionloop.app.feature.editor.components

import com.motionloop.app.ui.components.TileRow
import com.motionloop.app.ui.components.EffectTile
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.BackgroundSettings
import com.motionloop.app.domain.model.SubjectSettings
import com.motionloop.app.feature.editor.EditorTool
import com.motionloop.app.ui.components.Pill
import com.motionloop.app.ui.theme.MlColors

private fun BackgroundMode.label() = when (this) {
    BackgroundMode.NONE -> "Original"
    BackgroundMode.TRANSPARENT -> "Transparent"
    BackgroundMode.SOLID -> "Color"
    BackgroundMode.GRADIENT -> "Gradient"
    BackgroundMode.BLUR -> "Blur"
    BackgroundMode.PHOTO -> "Photo"
}

private val subjectTools = listOf(
    EditorTool.SUBJECT_ADD to "Tap to add",
    EditorTool.SUBJECT_REMOVE to "Tap to remove",
    EditorTool.SUBJECT_RESTORE to "Restore",
    EditorTool.SUBJECT_ERASE to "Erase",
)

@Composable
fun CutoutPanel(
    subject: SubjectSettings,
    background: BackgroundSettings,
    tool: EditorTool,
    busy: Boolean,
    onDetect: () -> Unit,
    onTool: (EditorTool) -> Unit,
    onBackground: ((BackgroundSettings) -> BackgroundSettings) -> Unit,
    onFeather: (Float) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth(),
    ) {
        Pill(if (subject.hasArea) "Find again" else "Find subject", active = true, enabled = !busy, onClick = onDetect)
        Spacer(Modifier.height(8.dp))
        Text(
            if (subject.hasArea) "Fix the edges: tap a region, or restore and erase by hand." else "Find the subject, or tap it to select it.",
            style = MaterialTheme.typography.bodySmall,
            color = MlColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subjectTools.forEach { (t, label) -> Pill(label, active = t == tool, enabled = !busy) { onTool(t) } }
        }
        LabeledSlider("Edge", subject.feather, 0f..1f, onFeather)

        Text("Background", style = MaterialTheme.typography.labelMedium, color = MlColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        TileRow {
            BackgroundMode.entries.forEach { mode ->
                val isPhoto = mode == BackgroundMode.PHOTO
                val label = if (isPhoto && background.mode == BackgroundMode.PHOTO) "Change" else mode.label()
                EffectTile(
                    label,
                    selected = background.mode == mode,
                    enabled = !busy && (subject.hasArea || mode == BackgroundMode.NONE),
                    description = if (isPhoto) (if (label == "Change") "Change background photo" else "Your own background photo") else null,
                    preview = backgroundPreview(mode),
                    onClick = {
                        // Photo needs a photo first. Tapping it again picks a different one.
                        if (isPhoto && (background.photoRev == 0 || background.mode == BackgroundMode.PHOTO)) {
                            onPickPhoto()
                        } else {
                            onBackground { it.copy(mode = mode) }
                        }
                    },
                )
            }
        }
        when (background.mode) {
            BackgroundMode.SOLID -> Swatches("Color", background.color1) { c -> onBackground { it.copy(color1 = c) } }
            BackgroundMode.GRADIENT -> {
                Swatches("Top", background.color1) { c -> onBackground { it.copy(color1 = c) } }
                Swatches("Bottom", background.color2) { c -> onBackground { it.copy(color2 = c) } }
            }
            BackgroundMode.BLUR -> LabeledSlider("Blur", background.blur, 0f..1f, { v -> onBackground { it.copy(blur = v) } })
            else -> Unit
        }
    }
}

@Composable
private fun Swatches(label: String, selected: Int, onPick: (Int) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = MlColors.TextSecondary)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BackgroundSettings.SWATCHES.forEach { argb ->
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(2.dp, if (argb == selected) MlColors.Mint else MlColors.SurfaceHigh, CircleShape)
                    .semantics { contentDescription = "Color ${"%06X".format(argb and 0xFFFFFF)}" }
                    .selectable(selected = argb == selected, role = Role.RadioButton) { onPick(argb) },
            )
        }
    }
}

