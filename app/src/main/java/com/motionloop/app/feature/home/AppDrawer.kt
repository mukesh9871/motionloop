package com.motionloop.app.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motionloop.app.BuildConfig
import com.motionloop.app.ui.components.MenuRow
import com.motionloop.app.ui.components.verticalScrollWithBar
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors

@Composable
fun AppDrawer(
    isPro: Boolean,
    onPro: () -> Unit,
    onHelp: () -> Unit,
    onShare: () -> Unit,
    onRate: () -> Unit,
    onContact: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MlColors.Surface) {
        // Scrolls: landscape and large fonts push the last rows below the sheet.
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScrollWithBar(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MlIcons.Loop, contentDescription = null, tint = MlColors.Mint, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("MotionLoop", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (isPro) "Pro" else "Free",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isPro) MlColors.Mint else MlColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MlColors.SurfaceHigh)
            Spacer(Modifier.height(8.dp))
            MenuRow(if (isPro) "MotionLoop Pro" else "Get MotionLoop Pro", icon = MlIcons.Pro, onClick = onPro)
            MenuRow("Help", icon = MlIcons.Help, onClick = onHelp)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MlColors.SurfaceHigh)
            Spacer(Modifier.height(8.dp))
            MenuRow("Share MotionLoop", icon = MlIcons.Share, onClick = onShare)
            MenuRow("Rate MotionLoop", icon = MlIcons.Star, onClick = onRate)
            MenuRow("Contact us", icon = MlIcons.Mail, onClick = onContact)
            MenuRow("Settings", icon = MlIcons.Settings, onClick = onSettings)
            Spacer(Modifier.height(24.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MlColors.TextSecondary,
            )
        }
    }
}
