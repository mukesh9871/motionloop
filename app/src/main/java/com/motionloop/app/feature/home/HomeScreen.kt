package com.motionloop.app.feature.home

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motionloop.app.data.media.MediaIntents
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors

@Composable
fun HomeScreen(onOpenProject: (String) -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { if (it is HomeEvent.OpenEditor) onOpenProject(it.projectId) }
    }

    // Photo Picker needs no storage permission and is backported to API 26 by Play services.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importPhoto)
    }
    // The camera writes into our FileProvider Uri. Kept across rotation while the camera app is open.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.importPhoto(uri)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("MotionLoop", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Bring a still photo to life",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ImportCard(
                    title = "Gallery",
                    icon = MlIcons.Gallery,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                ImportCard(
                    title = "Camera",
                    icon = MlIcons.Camera,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val uri = MediaIntents.newCameraUri(context)
                        cameraUri = uri
                        try {
                            takePhoto.launch(uri)
                        } catch (ignored: ActivityNotFoundException) {
                            cameraUri = null
                        }
                    },
                )
            }
            Spacer(Modifier.height(28.dp))
            if (state.recent.isNotEmpty()) {
                Text("Recent", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Long-press a project to delete it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.recent, key = { it.id }) { p ->
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .pointerInput(p.id) {
                                    detectTapGestures(
                                        onTap = { onOpenProject(p.id) },
                                        onLongPress = { pendingDelete = p.id },
                                    )
                                },
                        ) {
                            p.thumbnail?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    "Pick a photo, draw arrows where things should flow, and export a seamless loop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        pendingDelete?.let { id ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete this project?") },
                text = { Text("The animation settings and the copied photo are removed. Your original photo is not touched.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.delete(id)
                        pendingDelete = null
                    }) { Text("Delete", color = MlColors.Coral) }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
            )
        }

        if (state.importing) {
            Box(
                Modifier.fillMaxSize().background(MlColors.Background.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }
        state.error?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { Text("OK", modifier = Modifier.clickable { vm.dismissError() }.padding(8.dp)) },
            ) { Text(msg) }
        }
    }
}

@Composable
private fun ImportCard(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(MlColors.Violet, MlColors.SurfaceHigh)))
            .clickable(onClick = onClick)
            .padding(20.dp)
            .fillMaxWidth(),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MlColors.TextPrimary)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
