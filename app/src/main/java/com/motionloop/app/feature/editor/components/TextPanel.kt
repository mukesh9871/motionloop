package com.motionloop.app.feature.editor.components

import com.motionloop.app.render.tiles.rememberTile
import com.motionloop.app.render.tiles.TileKey
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.motionloop.app.domain.model.BackgroundSettings
import com.motionloop.app.domain.model.TextAnimation
import com.motionloop.app.domain.model.TextFont
import com.motionloop.app.domain.model.TextLayer
import com.motionloop.app.feature.editor.EditorTool
import com.motionloop.app.ui.components.Pill
import com.motionloop.app.ui.theme.MlColors

@Composable
fun TextPanel(
    texts: List<TextLayer>,
    selected: TextLayer?,
    hasSubject: Boolean,
    tool: EditorTool,
    busy: Boolean,
    onAdd: () -> Unit,
    onSelect: (String?) -> Unit,
    onChange: ((TextLayer) -> TextLayer) -> Unit,
    onDelete: () -> Unit,
    onFindSubject: () -> Unit,
    onTool: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth(),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("+ Add text", active = false, onClick = onAdd)
            texts.forEach { t ->
                val label = t.text.lineSequence().firstOrNull().orEmpty().take(14).ifBlank { "Text" }
                Pill(label, active = t.id == selected?.id) { onSelect(t.id) }
            }
        }
        // Text behind the subject needs a good subject outline: fix it right here, no trip to Cutout.
        if (texts.isNotEmpty()) {
            Label("Subject")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (hasSubject) "Find again" else "Find subject", active = false, enabled = !busy, onClick = onFindSubject)
                subjectTools.forEach { (t, label) -> Pill(label, active = t == tool, enabled = !busy) { onTool(t) } }
            }
            Text(
                when {
                    tool == EditorTool.TEXT_MOVE -> "Drag a text to move it. Pick a subject tool to fix the outline."
                    tool == EditorTool.SUBJECT_ADD -> "Tap a part of the subject to add it."
                    tool == EditorTool.SUBJECT_REMOVE -> "Tap an area to remove it from the subject."
                    tool == EditorTool.SUBJECT_RESTORE -> "Paint over the subject to restore it."
                    else -> "Paint to remove areas from the subject."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MlColors.TextSecondary,
            )
        }
        if (selected == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (texts.isEmpty()) "Add a text, then drag it on the photo to place it." else "Tap a text on the photo or above to edit it.",
                style = MaterialTheme.typography.bodySmall,
                color = MlColors.TextSecondary,
            )
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        // Local text while typing: a field driven straight from the state flow lags a frame and
        // can drop characters during fast input. Truncated here, so the limit shows at once.
        var draft by remember(selected.id) { mutableStateOf(selected.text) }
        OutlinedTextField(
            value = draft,
            onValueChange = { v ->
                draft = v.take(TextLayer.MAX_LENGTH)
                onChange { it.copy(text = draft) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Text") },
            supportingText = { Text("${draft.length} / ${TextLayer.MAX_LENGTH}") },
            maxLines = 3,
        )
        Label("Font")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextFont.entries.forEach { f -> Pill(f.label, active = selected.font == f) { onChange { it.copy(font = f) } } }
        }
        Label("Color")
        Swatches(selected.color) { c -> onChange { it.copy(color = c) } }
        LabeledSlider("Size", selected.size, TextLayer.MIN_SIZE..TextLayer.MAX_SIZE, { v -> onChange { it.copy(size = v) } })
        LabeledSlider("Rotate", selected.rotation, -180f..180f, { v -> onChange { it.copy(rotation = v) } })
        LabeledSlider("Spacing", selected.letterSpacing, -0.05f..0.5f, { v -> onChange { it.copy(letterSpacing = v) } })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Shadow", active = selected.shadow) { onChange { it.copy(shadow = !it.shadow) } }
            Pill("Outline", active = selected.outline) { onChange { it.copy(outline = !it.outline) } }
            Pill("Behind subject", active = selected.behindSubject) { onChange { it.copy(behindSubject = !it.behindSubject) } }
        }
        if (selected.outline) {
            Label("Outline color")
            Swatches(selected.outlineColor) { c -> onChange { it.copy(outlineColor = c) } }
        }
        Label("Animation")
        TileRow {
            TextAnimation.entries.forEach { a ->
                val chosen = selected.animation == a
                EffectTile(
                    a.label,
                    selected = chosen,
                    rendered = rememberTile(TileKey.Text(a), animated = chosen),
                    preview = textAnimationPreview(a),
                    onClick = { onChange { it.copy(animation = a) } },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(
                "Delete text",
                style = MaterialTheme.typography.labelMedium,
                color = MlColors.Coral,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private val subjectTools = listOf(
    EditorTool.TEXT_MOVE to "Move text",
    EditorTool.SUBJECT_ADD to "Tap to add",
    EditorTool.SUBJECT_REMOVE to "Tap to remove",
    EditorTool.SUBJECT_RESTORE to "Restore",
    EditorTool.SUBJECT_ERASE to "Erase",
)

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MlColors.TextSecondary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Swatches(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BackgroundSettings.SWATCHES.forEach { argb ->
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(2.dp, if (argb == selected) MlColors.Mint else MlColors.SurfaceHigh, CircleShape)
                    .semantics { contentDescription = "Color ${"%06X".format(argb and 0xFFFFFF)}" }
                    .selectable(selected = argb == selected, role = Role.RadioButton) { onPick(argb) },
            )
        }
    }
}

