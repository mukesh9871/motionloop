package com.motionloop.app.feature.contact

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.motionloop.app.BuildConfig
import com.motionloop.app.data.media.MediaIntents
import com.motionloop.app.domain.model.AppLinks
import com.motionloop.app.ui.components.Pill
import com.motionloop.app.ui.components.SimpleScreen
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors

private enum class Topic(val label: String) { FEEDBACK("Feedback"), PROBLEM("Report a problem"), IDEA("Feature idea") }

/**
 * Contact form. Without a server of our own, Send hands the finished message to the mail app,
 * already addressed and filled in, so the person only confirms it there.
 */
@Composable
fun ContactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var topic by rememberSaveable { mutableStateOf(Topic.FEEDBACK) }
    var name by rememberSaveable { mutableStateOf("") }
    var replyTo by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var includeDevice by rememberSaveable { mutableStateOf(true) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    val emailOk = replyTo.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(replyTo.trim()).matches()
    val canSend = message.trim().length >= MIN_MESSAGE && emailOk

    SimpleScreen("Contact us", onBack) {
        // Where the message goes, stated up front.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MlColors.Surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MlIcons.Mail, contentDescription = null, tint = MlColors.Mint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Your message goes to", style = MaterialTheme.typography.bodySmall, color = MlColors.TextSecondary)
                Text(AppLinks.SUPPORT_EMAIL, style = MaterialTheme.typography.titleMedium)
            }
        }

        Text("What is it about?", style = MaterialTheme.typography.labelMedium, color = MlColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Topic.entries.forEach { t -> Pill(t.label, active = topic == t) { topic = t } }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text("Your name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = replyTo,
            onValueChange = { replyTo = it.take(120) },
            label = { Text("Your email for a reply (optional)") },
            singleLine = true,
            isError = !emailOk,
            supportingText = if (!emailOk) ({ Text("This does not look like an email address.") }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(MAX_MESSAGE) },
            label = { Text("Message") },
            placeholder = { Text(if (topic == Topic.PROBLEM) "What did you do, and what went wrong?" else "Tell us what you think.") },
            minLines = 5,
            supportingText = { Text("${message.length} / $MAX_MESSAGE") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = includeDevice, role = Role.Checkbox, onValueChange = { includeDevice = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = includeDevice, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = MlColors.Violet))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Include phone details", style = MaterialTheme.typography.bodyLarge)
                Text(deviceDetails(), style = MaterialTheme.typography.bodySmall, color = MlColors.TextSecondary)
            }
        }
        Button(
            onClick = {
                val subject = "MotionLoop: ${topic.label}"
                val body = buildBody(name, replyTo, message, includeDevice)
                notice = if (MediaIntents.email(context, AppLinks.SUPPORT_EMAIL, subject, body)) {
                    null
                } else {
                    copyToClipboard(context, body)
                    "No mail app found. Your message is copied: paste it into an email to ${AppLinks.SUPPORT_EMAIL}."
                }
            },
            enabled = canSend,
            colors = ButtonDefaults.buttonColors(containerColor = MlColors.Violet),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Send") }
        Text(
            if (canSend) "Send opens your mail app with everything filled in. Tap send there." else "Write at least $MIN_MESSAGE characters.",
            style = MaterialTheme.typography.bodySmall,
            color = MlColors.TextSecondary,
        )
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MlColors.Coral) }
    }
}

private const val MIN_MESSAGE = 10
private const val MAX_MESSAGE = 2000

private fun deviceDetails() =
    "MotionLoop ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}"

private fun buildBody(name: String, replyTo: String, message: String, includeDevice: Boolean) = buildString {
    appendLine(message.trim())
    appendLine()
    if (name.isNotBlank()) appendLine("Name: ${name.trim()}")
    if (replyTo.isNotBlank()) appendLine("Reply to: ${replyTo.trim()}")
    if (includeDevice) appendLine(deviceDetails())
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("MotionLoop message", text))
}
