package com.motionloop.app.feature.editor.components

import com.motionloop.app.ui.components.TileRow
import com.motionloop.app.ui.components.EffectTile
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.motionloop.app.domain.model.Atmosphere
import com.motionloop.app.domain.model.BlendMode
import com.motionloop.app.domain.model.LightLeak
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.domain.model.ParticleType
import com.motionloop.app.feature.editor.EditorTool
import com.motionloop.app.ui.components.Pill
import com.motionloop.app.ui.theme.MlColors

private fun ParticleType.label() = when (this) {
    ParticleType.NONE -> "None"
    ParticleType.SPARKLE -> "Sparkle"
    ParticleType.SNOW -> "Snow"
    ParticleType.RAIN -> "Rain"
    ParticleType.BOKEH -> "Bokeh"
    ParticleType.EMBERS -> "Embers"
    ParticleType.BUBBLES -> "Bubbles"
    ParticleType.HEARTS -> "Hearts"
    ParticleType.FIREFLIES -> "Fireflies"
    ParticleType.SMOKE -> "Smoke"
}

private fun LightLeak.label() = when (this) {
    LightLeak.NONE -> "None"
    LightLeak.WARM -> "Warm"
    LightLeak.ROSE -> "Rose"
    LightLeak.AURORA -> "Aurora"
}

private fun Atmosphere.label() = when (this) {
    Atmosphere.NONE -> "None"
    Atmosphere.FOG -> "Fog"
    Atmosphere.GRAIN -> "Film grain"
}

private fun BlendMode.label() = when (this) {
    BlendMode.NORMAL -> "Normal"
    BlendMode.ADD -> "Add"
    BlendMode.SCREEN -> "Screen"
    BlendMode.OVERLAY -> "Overlay"
    BlendMode.MULTIPLY -> "Multiply"
    BlendMode.SOFT_LIGHT -> "Soft light"
}

@Composable
fun OverlayPanel(
    settings: OverlaySettings,
    tool: EditorTool,
    onChange: ((OverlaySettings) -> OverlaySettings) -> Unit,
    onTool: (EditorTool) -> Unit,
    onClearArea: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Capped and scrollable so the photo keeps most of the screen.
    Column(
        modifier
            .fillMaxWidth(),
    ) {
        SectionLabel("Particles")
        TileRow {
            ParticleType.entries.forEach { v ->
                EffectTile(v.label(), selected = settings.particles == v, preview = particlePreview(v), onClick = { onChange { it.copy(particles = v) } })
            }
        }
        if (settings.particles != ParticleType.NONE) {
            LabeledSlider("Amount", settings.density, 0.05f..1f, { v -> onChange { it.copy(density = v) } })
            LabeledSlider("Size", settings.size, 0f..1f, { v -> onChange { it.copy(size = v) } })
            SectionLabel("Speed")
            ChipRow(
                (OverlaySettings.MIN_SPEED..OverlaySettings.MAX_SPEED).toList(),
                settings.speed,
                { "${it}x" },
            ) { v -> onChange { it.copy(speed = v) } }
            // Paint where the particles show, like sparkles on one dress or smoke from one chimney.
            SectionLabel("Where")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Paint area", active = tool == EditorTool.OVERLAY_PAINT) { onTool(EditorTool.OVERLAY_PAINT) }
                Pill("Erase area", active = tool == EditorTool.OVERLAY_ERASE) { onTool(EditorTool.OVERLAY_ERASE) }
                if (settings.areaStrokes.isNotEmpty()) Pill("Everywhere", active = false, onClick = onClearArea)
            }
            Text(
                if (settings.hasArea) "Particles show only in the painted area." else "Particles fill the whole photo. Paint to limit them to an area.",
                style = MaterialTheme.typography.bodySmall,
                color = MlColors.TextSecondary,
            )
        }

        SectionLabel("Light leak")
        TileRow {
            LightLeak.entries.forEach { v ->
                EffectTile(v.label(), selected = settings.lightLeak == v, preview = leakPreview(v), onClick = { onChange { it.copy(lightLeak = v) } })
            }
        }
        if (settings.lightLeak != LightLeak.NONE) {
            LabeledSlider("Glow", settings.leakIntensity, 0f..1f, { v -> onChange { it.copy(leakIntensity = v) } })
        }

        SectionLabel("Atmosphere")
        TileRow {
            Atmosphere.entries.forEach { v ->
                EffectTile(v.label(), selected = settings.atmosphere == v, preview = atmospherePreview(v), onClick = { onChange { it.copy(atmosphere = v) } })
            }
        }
        if (settings.atmosphere != Atmosphere.NONE) {
            LabeledSlider("Strength", settings.atmosphereAmount, 0f..1f, { v -> onChange { it.copy(atmosphereAmount = v) } })
        }

        // hasContent, not isActive: at opacity 0 the slider must stay reachable to bring it back.
        if (settings.hasContent) {
            SectionLabel("Blend")
            ChipRow(BlendMode.entries, settings.blend, { it.label() }) { v -> onChange { it.copy(blend = v) } }
            LabeledSlider("Opacity", settings.opacity, 0f..1f, { v -> onChange { it.copy(opacity = v) } })
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MlColors.TextSecondary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Pill(label(option), active = active) { onSelect(option) }
        }
    }
}
