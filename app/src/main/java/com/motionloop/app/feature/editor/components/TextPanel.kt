package com.motionloop.app.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.motionloop.app.ui.theme.MlColors

@Composable
fun TextPanel(
    texts: List<TextLayer>,
    selected: TextLayer?,
    hasSubject: Boolean,
    onAdd: () -> Unit,
    onSelect: (String?) -> Unit,
    onChange: ((TextLayer) -> TextLayer) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("+ Add text", active = false, onClick = onAdd)
            texts.forEach { t ->
                val label = t.text.lineSequence().firstOrNull().orEmpty().take(14).ifBlank { "Text" }
                Pill(label, active = t.id == selected?.id) { onSelect(t.id) }
            }
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
        OutlinedTextField(
            value = selected.text,
            onValueChange = { v -> onChange { it.copy(text = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Text") },
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
            Pill(
                "Behind subject",
                active = selected.behindSubject && hasSubject,
                enabled = hasSubject,
            ) { onChange { it.copy(behindSubject = !it.behindSubject) } }
        }
        if (!hasSubject) {
            Text(
                "To put text behind the subject, find the subject in Cutout first.",
                style = MaterialTheme.typography.bodySmall,
                color = MlColors.TextSecondary,
            )
        }
        if (selected.outline) {
            Label("Outline color")
            Swatches(selected.outlineColor) { c -> onChange { it.copy(outlineColor = c) } }
        }
        Label("Animation")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextAnimation.entries.forEach { a -> Pill(a.label, active = selected.animation == a) { onChange { it.copy(animation = a) } } }
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
                    .semantics { contentDescription = "Color swatch" }
                    .clickable { onPick(argb) },
            )
        }
    }
}

@Composable
private fun Pill(label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = when {
            !enabled -> MlColors.TextSecondary.copy(alpha = 0.5f)
            active -> MlColors.TextPrimary
            else -> MlColors.TextSecondary
        },
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) MlColors.Violet else MlColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
