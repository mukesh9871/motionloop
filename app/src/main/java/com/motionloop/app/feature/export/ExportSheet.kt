package com.motionloop.app.feature.export

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motionloop.app.data.media.MediaIntents
import com.motionloop.app.domain.model.ExportFormat
import com.motionloop.app.domain.model.ExportSettings
import com.motionloop.app.ui.theme.MlColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(onDismiss: () -> Unit, vm: ExportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = state.settings

    // Android 8 and 9 need the legacy storage permission to save into the gallery.
    val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start()
    }
    val startExport = {
        if (needsPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.start()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!state.running) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Export", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            Section("Format") {
                ExportFormat.entries.forEach { f ->
                    Chip(f.name, selected = settings.format == f, enabled = !state.running) { vm.setFormat(f) }
                }
            }
            val lengths = if (settings.format == ExportFormat.GIF) ExportSettings.GIF_LENGTHS else ExportSettings.VIDEO_LENGTHS
            Section("Length") {
                lengths.forEach { s ->
                    Chip("${s}s", selected = settings.lengthSeconds == s, enabled = !state.running) { vm.setLength(s) }
                }
            }
            if (settings.format == ExportFormat.MP4) {
                Section("Resolution") {
                    state.resolutions.forEach { r ->
                        Chip(r.label, selected = settings.resolution == r, enabled = !state.running) { vm.setResolution(r) }
                    }
                }
            } else {
                Text(
                    "GIF is ${ExportSettings.GIF_LONG_EDGE} px on the long edge to keep files small.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MlColors.TextSecondary,
                )
            }

            Spacer(Modifier.height(20.dp))
            when {
                state.running -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MlColors.Mint,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
                state.resultUri != null -> {
                    Text("Saved to your gallery.", color = MlColors.Mint)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val uri = state.resultUri
                                val mime = state.resultMime
                                if (uri != null && mime != null) MediaIntents.share(context, uri, mime)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MlColors.Violet),
                        ) { Text("Share") }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
                    }
                }
                else -> {
                    state.error?.let {
                        Text(it, color = MlColors.Coral, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = startExport,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MlColors.Mint, contentColor = MlColors.Background),
                    ) { Text("Export ${settings.format.name}") }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MlColors.TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MlColors.Violet,
            selectedLabelColor = MlColors.TextPrimary,
        ),
    )
}
