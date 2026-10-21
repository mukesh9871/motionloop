package com.motionloop.app.feature.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motionloop.app.domain.pro.ProFeature
import com.motionloop.app.domain.pro.ProPlan
import com.motionloop.app.domain.pro.ProPolicy
import com.motionloop.app.feature.settings.SettingsViewModel
import com.motionloop.app.ui.components.SimpleScreen
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors

@Composable
fun ProScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var plan by rememberSaveable { mutableStateOf(ProPlan.YEARLY) }

    SimpleScreen("MotionLoop Pro", onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(MlColors.Violet, MlColors.SurfaceHigh)))
                .padding(20.dp),
        ) {
            Icon(MlIcons.Pro, contentDescription = null, tint = MlColors.Mint, modifier = Modifier.size(36.dp))
            Text(
                if (isPro) "You have Pro" else "Go further with Pro",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Pro unlocks the advanced tools. Everything you already made stays yours either way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MlColors.TextPrimary.copy(alpha = 0.85f),
            )
        }

        // Only what the app can do today: no promises for features that are not built yet.
        ProFeature.entries.filter { it in ProPolicy.proOnly && it.available }.forEach { feature ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(MlIcons.Check, contentDescription = null, tint = MlColors.Mint, modifier = Modifier.size(20.dp))
                Column {
                    Text(feature.title, style = MaterialTheme.typography.titleMedium)
                    Text(feature.description, style = MaterialTheme.typography.bodySmall, color = MlColors.TextSecondary)
                }
            }
        }

        if (!isPro) {
            ProPlan.entries.forEach { p ->
                val selected = p == plan
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(2.dp, if (selected) MlColors.Mint else Color.Transparent, RoundedCornerShape(14.dp))
                        .background(MlColors.Surface)
                        .clickable { plan = p }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium)
                        Text(p.priceLabel, style = MaterialTheme.typography.bodySmall, color = MlColors.TextSecondary)
                    }
                    Text(p.note, style = MaterialTheme.typography.labelMedium, color = MlColors.Mint)
                }
            }
            Button(
                onClick = { vm.purchase(plan) },
                enabled = vm.purchasesAvailable,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MlColors.Mint, contentColor = MlColors.OnMint),
            ) { Text("Continue") }
            if (!vm.purchasesAvailable) {
                Text(
                    "Purchases open when MotionLoop launches on Google Play. Until then every feature is free.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MlColors.TextSecondary,
                )
            }
            OutlinedButton(onClick = vm::restore, modifier = Modifier.fillMaxWidth()) { Text("Restore purchases") }
        }
        message?.let {
            Text(it, color = MlColors.Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable(onClick = vm::dismissMessage))
        }
    }
}
