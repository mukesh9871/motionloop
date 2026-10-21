package com.motionloop.app.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motionloop.app.BuildConfig
import com.motionloop.app.domain.model.AppLinks
import com.motionloop.app.domain.model.ExportResolution
import com.motionloop.app.domain.model.ThemeMode
import com.motionloop.app.ui.components.MenuRow
import com.motionloop.app.ui.components.Pill
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

        SectionTitle("Appearance")
        Text("Theme", style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                val active = state.theme == mode
                Pill(mode.label, active = active, modifier = Modifier.semantics { role = Role.RadioButton; selected = active }) {
                    vm.setTheme(mode)
                }
            }
        }

        SectionTitle("Export")
        Text("Default video quality", style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportResolution.entries.forEach { r ->
                val active = state.defaultResolution == r
                Pill(r.label, active = active, modifier = Modifier.semantics { role = Role.RadioButton; selected = active }) {
                    vm.setDefaultResolution(r)
                }
            }
        }
        if (state.defaultResolution == ExportResolution.UHD) {
            Text(
                "4K is used where your phone can record it. Other phones export in 1080p.",
                style = MaterialTheme.typography.bodySmall,
                color = MlColors.TextSecondary,
            )
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
