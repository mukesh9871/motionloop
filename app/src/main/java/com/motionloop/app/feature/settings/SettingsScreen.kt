package com.motionloop.app.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motionloop.app.BuildConfig
import com.motionloop.app.domain.model.AppLinks
import com.motionloop.app.domain.model.ExportResolution
import com.motionloop.app.ui.components.MenuRow
import com.motionloop.app.ui.components.SectionTitle
import com.motionloop.app.ui.components.SimpleScreen
import com.motionloop.app.ui.theme.MlColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenLicenses: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SimpleScreen("Settings", onBack) {
        SectionTitle("MotionLoop Pro")
        MenuRow(
            title = if (isPro) "Pro is active" else "Get MotionLoop Pro",
            subtitle = if (isPro) "Thank you for supporting MotionLoop." else "See what Pro adds.",
            onClick = onOpenPro,
        )

        SectionTitle("Export")
        Text("Default video quality", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ExportResolution.HD, ExportResolution.FULL_HD).forEach { r ->
                val active = state.defaultResolution == r
                Text(
                    r.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MlColors.TextPrimary else MlColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) MlColors.Violet else MlColors.SurfaceHigh)
                        .selectable(selected = active, role = Role.RadioButton) { vm.setDefaultResolution(r) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        SectionTitle("Editor")
        SwitchRow("Keep screen on while editing", null, state.keepScreenOn, vm::setKeepScreenOn)

        if (BuildConfig.DEBUG) {
            SectionTitle("Developer")
            SwitchRow("Simulate Pro", "Debug builds only. Unlocks Pro features for testing.", state.simulatePro, vm::setSimulatePro)
        }

        SectionTitle("About")
        MenuRow("Open source licenses and photo credits", onClick = onOpenLicenses)
        MenuRow("Privacy policy", onClick = { openUrl(context, AppLinks.PRIVACY) })
        MenuRow("Terms of use", onClick = { openUrl(context, AppLinks.TERMS) })
        MenuRow("Version", subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}

/** The whole row toggles, so TalkBack reads the title together with the on/off state. */
@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MlColors.TextPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MlColors.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = MlColors.Violet))
    }
}

/** Opens a link in the browser. Returns false when no app can open it. */
fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (e: ActivityNotFoundException) {
    false
}
