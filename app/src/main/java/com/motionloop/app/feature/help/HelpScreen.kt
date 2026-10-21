package com.motionloop.app.feature.help

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.motionloop.app.ui.components.SectionTitle
import com.motionloop.app.ui.components.SimpleScreen
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One help card. [id] also names an optional recorded demo: put `help/<id>.gif` (or `.webp`)
 * into the app assets and it replaces the drawn picture on Android 9 and later.
 */
private data class Topic(val id: String, val icon: ImageVector, val title: String, val body: String, val picture: Illus)

private data class Group(val title: String, val topics: List<Topic>)

private val groups = listOf(
    Group(
        "Animate",
        listOf(
            Topic("motion", MlIcons.Motion, "Motion path", "Draw the path things should follow, straight or curved. Pixels flow along it. A longer path moves faster. Motion fades out away from your paths.", Illus.PATH),
            Topic("anchor", MlIcons.Anchor, "Anchor", "Tap to pin points that must stay still, such as rocks next to a waterfall or the edge of a building.", Illus.ANCHOR),
            Topic("freeze", MlIcons.Freeze, "Freeze", "Paint over areas that must not move at all, like people or trees. Adjust the brush size and softness below the photo.", Illus.FREEZE),
            Topic("unfreeze", MlIcons.Unfreeze, "Unfreeze", "Paint over a frozen area to let it move again.", Illus.UNFREEZE),
            Topic("erase", MlIcons.Delete, "Erase", "Tap a motion path or an anchor to remove it.", Illus.ERASE),
            Topic("hold", MlIcons.Select, "Hold", "Tap an object, such as a person or a boat. The app finds its outline and keeps it still.", Illus.HOLD),
            Topic("only_this", MlIcons.Focus, "Only this", "Tap an object: only it moves, everything else holds still.", Illus.ONLY_THIS),
            Topic("speed", MlIcons.Loop, "Speed, loop and bounce", "The slider sets the speed. Loop flows endlessly in one direction. Bounce plays forward and back, good for hair or flags. Undo and Redo sit at the top.", Illus.SPEED),
        ),
    ),
    Group(
        "Sky",
        listOf(
            Topic("sky", MlIcons.Gallery, "Replace the sky", "Detect the sky, fix it by tapping or painting, then pick a new animated sky or your own photo. Drift moves the clouds, Edge softens the border, Blend matches the colors.", Illus.SKY),
        ),
    ),
    Group(
        "3D",
        listOf(
            Topic("depth", MlIcons.Eye, "3D camera", "Create the depth map once, then pick a camera move. Near things move more than far things, like a real camera.", Illus.DEPTH),
        ),
    ),
    Group(
        "Cutout",
        listOf(
            Topic("cutout", MlIcons.Select, "Remove the background", "Find the subject, fix its edges by tapping or painting, then choose a new background: transparent, a color, a gradient, a blur or a photo. Export a transparent PNG.", Illus.CUTOUT),
        ),
    ),
    Group(
        "Text",
        listOf(
            Topic("text", MlIcons.Pro, "Text behind the subject", "Add a text: it goes behind the subject on its own. If the outline is wrong, use the Subject tools in the Text panel to tap or paint it. Pick a font, color and animation.", Illus.TEXT),
        ),
    ),
    Group(
        "Sound",
        listOf(
            Topic("sound", MlIcons.Play, "Sound and beat", "Add an ambient sound or your own music. Beat sync makes the motion pulse with the beat. Videos keep the sound, set to the exact loop length.", Illus.SOUND),
        ),
    ),
    Group(
        "Adjust and overlay",
        listOf(
            Topic("adjust", MlIcons.Settings, "Adjust", "Brightness, contrast, saturation and warmth, plus color grades. Import your own .cube grade.", Illus.ADJUST),
            Topic("overlay", MlIcons.Star, "Overlay", "Add snow, rain, sparkles or light leaks. They loop as seamlessly as the photo.", Illus.OVERLAY),
        ),
    ),
    Group(
        "Export",
        listOf(
            Topic("export", MlIcons.Export, "Export and share", "Save a video, a GIF or a PNG in the length, size and shape you need (original, 1:1, 4:5 or 9:16), or set it as a live wallpaper.", Illus.EXPORT),
        ),
    ),
)

@Composable
fun HelpScreen(onBack: () -> Unit, onContact: () -> Unit) {
    SimpleScreen("Help", onBack) {
        groups.forEach { group ->
            SectionTitle(group.title)
            group.topics.forEach { TopicCard(it) }
        }
        SectionTitle("Still stuck?")
        Text(
            "Tell us what you tried. Screenshots help.",
            style = MaterialTheme.typography.bodyMedium,
            color = MlColors.TextSecondary,
        )
        Button(
            onClick = onContact,
            colors = ButtonDefaults.buttonColors(containerColor = MlColors.Violet),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Contact us") }
    }
}

@Composable
private fun TopicCard(topic: Topic) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MlColors.Surface),
    ) {
        val pictureModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
        val recorded = rememberRecordedDemo(topic.id)
        if (recorded != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            RecordedDemo(recorded, pictureModifier)
        } else {
            HelpIllustration(topic.picture, pictureModifier)
        }
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(topic.icon, contentDescription = null, tint = MlColors.Mint, modifier = Modifier.size(24.dp))
            Column {
                Text(topic.title, style = MaterialTheme.typography.titleMedium, color = MlColors.TextPrimary)
                Text(topic.body, style = MaterialTheme.typography.bodyMedium, color = MlColors.TextSecondary)
            }
        }
    }
}

/** Asset path of a recorded demo for [id], if one was added to `assets/help`. */
@Composable
private fun rememberRecordedDemo(id: String): String? {
    val context = LocalContext.current
    return remember(id) {
        val files = runCatching { context.assets.list("help")?.toSet() }.getOrNull().orEmpty()
        listOf("$id.gif", "$id.webp").firstOrNull { it in files }?.let { "help/$it" }
    }
}

@Composable
private fun RecordedDemo(assetPath: String, modifier: Modifier) {
    val context = LocalContext.current
    // Decoded off the main thread, started on it.
    val drawable by produceState<Drawable?>(null, assetPath) {
        value = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, assetPath)) }.getOrNull()
            } else {
                null
            }
        }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        modifier = modifier,
        update = { view ->
            val d = drawable
            if (view.drawable !== d) {
                view.setImageDrawable(d)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (d as? AnimatedImageDrawable)?.start()
            }
        },
    )
}
