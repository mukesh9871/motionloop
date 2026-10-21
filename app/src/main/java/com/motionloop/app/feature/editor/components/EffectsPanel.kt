package com.motionloop.app.feature.editor.components

import androidx.compose.ui.semantics.Role
import com.motionloop.app.ui.components.TileRow
import com.motionloop.app.ui.components.EffectTile
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.motionloop.app.domain.model.EffectsSettings
import com.motionloop.app.domain.model.Sticker
import com.motionloop.app.domain.model.StickerKind
import com.motionloop.app.feature.editor.EditorTool
import com.motionloop.app.ui.components.Pill
import com.motionloop.app.ui.theme.MlColors

/** Stickers, dispersion and water ripples. */
@Composable
fun EffectsPanel(
    effects: EffectsSettings,
    selected: Sticker?,
    tool: EditorTool,
    busy: Boolean,
    waterCount: Int,
    onTool: (EditorTool) -> Unit,
    onChange: ((EffectsSettings) -> EffectsSettings) -> Unit,
    onAddSticker: (StickerKind) -> Unit,
    onSelectSticker: (String?) -> Unit,
    onChangeSticker: ((Sticker) -> Sticker) -> Unit,
    onDeleteSticker: () -> Unit,
    onClearDispersion: () -> Unit,
    onClearWater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Label("Stickers")
        // Each tap adds one more sticker of that kind.
        TileRow {
            StickerKind.entries.forEach { kind ->
                EffectTile(
                    kind.label,
                    selected = false,
                    role = Role.Button,
                    description = "Add ${kind.label.lowercase()} sticker",
                    preview = stickerPreview(kind),
                    onClick = { onAddSticker(kind) },
                )
            }
        }
        if (effects.stickers.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                effects.stickers.forEachIndexed { i, s ->
                    Pill("${s.kind.label} ${i + 1}", active = s.id == selected?.id) {
                        onSelectSticker(s.id)
                        onTool(EditorTool.STICKER_MOVE)
                    }
                }
            }
        }
        if (selected != null) {
            Hint("Drag the sticker on the photo to move it.")
            LabeledSlider(
                "Size",
                selected.size,
                Sticker.MIN_SIZE..Sticker.MAX_SIZE,
                { v -> onChangeSticker { it.copy(size = v) } },
            )
            LabeledSlider("Rotate", selected.rotation, -180f..180f, { v -> onChangeSticker { it.copy(rotation = v) } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextAction("Delete sticker", onClick = onDeleteSticker)
            }
        } else if (effects.stickers.isEmpty()) {
            Hint("Add a sticker, then drag it where you want it.")
        }

        Label("Dispersion")
        val dispersion = effects.dispersion
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Paint area", active = tool == EditorTool.DISPERSE_PAINT, enabled = !busy) { onTool(EditorTool.DISPERSE_PAINT) }
            Pill("Erase area", active = tool == EditorTool.DISPERSE_ERASE, enabled = !busy) { onTool(EditorTool.DISPERSE_ERASE) }
            if (dispersion.strokes.isNotEmpty()) Pill("Clear", active = false, enabled = !busy, onClick = onClearDispersion)
        }
        if (dispersion.strokes.isEmpty()) {
            Hint("Paint over a part of the photo: it breaks into flakes that drift away and return.")
        } else {
            LabeledSlider("Amount", dispersion.amount, 0f..1f, { v -> onChange { it.copy(dispersion = it.dispersion.copy(amount = v)) } })
            LabeledSlider("Distance", dispersion.distance, 0f..1f, { v -> onChange { it.copy(dispersion = it.dispersion.copy(distance = v)) } })
            LabeledSlider("Direction", dispersion.angle, -180f..180f, { v -> onChange { it.copy(dispersion = it.dispersion.copy(angle = v)) } })
        }

        Label("Water")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Tap water", active = tool == EditorTool.WATER, enabled = !busy) { onTool(EditorTool.WATER) }
            if (waterCount > 0) Pill("Clear", active = false, enabled = !busy, onClick = onClearWater)
        }
        if (waterCount == 0) {
            Hint("Tap a lake, river or sea: it ripples gently, with or without motion paths.")
        } else {
            LabeledSlider("Ripples", effects.ripple.strength, 0f..1f, { v -> onChange { it.copy(ripple = it.ripple.copy(strength = v)) } })
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MlColors.TextSecondary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MlColors.TextSecondary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MlColors.Coral,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
