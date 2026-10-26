package com.motionloop.app.feature.editor.components

import com.motionloop.app.render.tiles.rememberTile
import com.motionloop.app.render.tiles.TileKey
import androidx.compose.ui.semantics.Role
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
import com.motionloop.app.domain.audio.AmbientSound
import com.motionloop.app.domain.audio.AudioSettings
import com.motionloop.app.domain.audio.AudioSource
import com.motionloop.app.ui.components.Pill
import com.motionloop.app.ui.theme.MlColors

@Composable
fun SoundPanel(
    audio: AudioSettings,
    busy: Boolean,
    onChange: ((AudioSettings) -> AudioSettings) -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        TileRow {
            // Sound has no picture of its own: the photo, with the sound's motion drawn over it.
            val photo = rememberTile(TileKey.Photo)
            EffectTile(
                "None",
                selected = audio.source == AudioSource.NONE,
                enabled = !busy,
                rendered = photo,
                overlay = NoneOverlay,
                preview = NonePreview,
                onClick = { onChange { it.copy(source = AudioSource.NONE) } },
            )
            AmbientSound.entries.forEach { s ->
                EffectTile(
                    s.label,
                    selected = audio.source == AudioSource.AMBIENT && audio.ambient == s,
                    enabled = !busy,
                    rendered = photo,
                    overlay = soundOverlay(s),
                    preview = soundPreview(s),
                    onClick = { onChange { it.copy(source = AudioSource.AMBIENT, ambient = s) } },
                )
            }
            val fileActive = audio.source == AudioSource.FILE && audio.fileRev != 0
            EffectTile(
                if (fileActive) "Change" else "Your audio",
                selected = fileActive,
                enabled = !busy,
                role = Role.Button,
                description = if (fileActive) "Change your audio file" else "Pick your own audio",
                rendered = photo,
                overlay = PickOverlay,
                preview = PickPreview,
                onClick = onPickFile,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Built-in sounds are generated to loop exactly with the video. Press play to listen.",
            style = MaterialTheme.typography.bodySmall,
            color = MlColors.TextSecondary,
        )
        if (audio.hasSound) {
            LabeledSlider("Volume", audio.volume, 0f..1f, { v -> onChange { it.copy(volume = v) } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Beat sync", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Motion speeds up on every beat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MlColors.TextSecondary,
                    )
                }
                Switch(
                    checked = audio.beatSync,
                    onCheckedChange = { on -> onChange { it.copy(beatSync = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = MlColors.Violet),
                )
            }
            if (audio.beatSync) {
                LabeledSlider("Pulse", audio.beatAmount, 0f..1f, { v -> onChange { it.copy(beatAmount = v) } })
            }
        }
    }
}

