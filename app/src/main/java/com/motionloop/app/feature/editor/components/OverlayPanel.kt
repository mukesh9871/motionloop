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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.motionloop.app.domain.model.BlendMode
import com.motionloop.app.domain.model.LightLeak
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.domain.model.ParticleType
import com.motionloop.app.ui.theme.MlColors

private fun ParticleType.label() = when (this) {
    ParticleType.NONE -> "None"
    ParticleType.SPARKLE -> "Sparkle"
    ParticleType.SNOW -> "Snow"
    ParticleType.RAIN -> "Rain"
    ParticleType.BOKEH -> "Bokeh"
    ParticleType.EMBERS -> "Embers"
}

private fun LightLeak.label() = when (this) {
    LightLeak.NONE -> "None"
    LightLeak.WARM -> "Warm"
    LightLeak.ROSE -> "Rose"
    LightLeak.AURORA -> "Aurora"
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
    onChange: ((OverlaySettings) -> OverlaySettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Capped and scrollable so the photo keeps most of the screen.
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SectionLabel("Particles")
        ChipRow(ParticleType.entries, settings.particles, { it.label() }) { v -> onChange { it.copy(particles = v) } }
        if (settings.particles != ParticleType.NONE) {
            LabeledSlider("Amount", settings.density, 0.05f..1f, { v -> onChange { it.copy(density = v) } })
            LabeledSlider("Size", settings.size, 0f..1f, { v -> onChange { it.copy(size = v) } })
            SectionLabel("Speed")
            ChipRow(
                (OverlaySettings.MIN_SPEED..OverlaySettings.MAX_SPEED).toList(),
                settings.speed,
                { "${it}x" },
            ) { v -> onChange { it.copy(speed = v) } }
        }

        SectionLabel("Light leak")
        ChipRow(LightLeak.entries, settings.lightLeak, { it.label() }) { v -> onChange { it.copy(lightLeak = v) } }
        if (settings.lightLeak != LightLeak.NONE) {
            LabeledSlider("Glow", settings.leakIntensity, 0f..1f, { v -> onChange { it.copy(leakIntensity = v) } })
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
            Text(
                label(option),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MlColors.TextPrimary else MlColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MlColors.Violet else MlColors.SurfaceHigh)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}
