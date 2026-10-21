package com.motionloop.app.feature.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.motionloop.app.domain.model.AppLinks
import com.motionloop.app.ui.components.SectionTitle
import com.motionloop.app.ui.components.SimpleScreen
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors

private data class Tip(val icon: ImageVector, val title: String, val body: String)

private val tips = listOf(
    Tip(MlIcons.Motion, "Motion arrows", "Drag in the direction things should flow. A longer arrow moves faster. Motion fades out away from your arrows."),
    Tip(MlIcons.Anchor, "Anchors", "Tap to pin points that must stay still, such as rocks next to a waterfall or the edge of a building."),
    Tip(MlIcons.Freeze, "Freeze brush", "Paint over areas that must not move at all, like people or trees. Use Unfreeze to bring motion back."),
    Tip(MlIcons.Loop, "Loop or bounce", "Loop flows endlessly in one direction. Bounce plays forward and back, good for hair or flags."),
    Tip(MlIcons.Gallery, "Sky", "Detect the sky, fix it by tapping or painting, then pick a new animated sky or your own photo."),
    Tip(MlIcons.Eye, "Adjust and overlays", "Grade the colors, then add snow, rain, sparkles or light leaks. Everything loops seamlessly."),
    Tip(MlIcons.Export, "Export", "Save as video or GIF in the length and size you need, then share it straight from the app."),
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    SimpleScreen("Help", onBack) {
        SectionTitle("How the tools work")
        tips.forEach { tip ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MlColors.Surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(tip.icon, contentDescription = null, tint = MlColors.Mint, modifier = Modifier.size(28.dp))
                Column {
                    Text(tip.title, style = MaterialTheme.typography.titleMedium)
                    Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = MlColors.TextSecondary)
                }
            }
        }
        SectionTitle("Still stuck?")
        Text(
            "Write to ${AppLinks.SUPPORT_EMAIL} and describe what you tried. Screenshots help.",
            style = MaterialTheme.typography.bodyMedium,
            color = MlColors.TextSecondary,
        )
    }
}
