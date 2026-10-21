package com.motionloop.app.feature.home

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motionloop.app.data.media.MediaIntents
import com.motionloop.app.domain.model.AppLinks
import com.motionloop.app.feature.settings.openUrl
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors
import kotlinx.coroutines.launch

// combinedClickable was experimental in older Compose releases. The opt-in is harmless if it is stable now.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenPro: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showShare by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is HomeEvent.OpenEditor -> onOpenProject(event.projectId)
                is HomeEvent.ShareApk -> MediaIntents.share(context, event.uri, "application/vnd.android.package-archive")
            }
        }
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

    // Close first, then act: navigating mid-animation would come back to a half-open drawer.
    fun closeThen(action: () -> Unit) {
        scope.launch {
            drawer.close()
            action()
        }
    }

    // Back closes an open drawer instead of leaving the app.
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            AppDrawer(
                isPro = isPro,
                onPro = { closeThen(onOpenPro) },
                onHelp = { closeThen(onOpenHelp) },
                onShare = { closeThen { showShare = true } },
                onRate = {
                    closeThen { if (!openUrl(context, AppLinks.PLAY_STORE)) notice = "No app can open the store link." }
                },
                onContact = {
                    closeThen {
                        if (!MediaIntents.email(context, AppLinks.SUPPORT_EMAIL, "MotionLoop feedback")) {
                            notice = "No mail app found. Write to ${AppLinks.SUPPORT_EMAIL}."
                        }
                    }
                },
                onSettings = { closeThen(onOpenSettings) },
            )
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            // One scrolling grid for the whole page, so samples and projects scroll together.
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { drawer.open() } }) {
                                Icon(MlIcons.Menu, contentDescription = "Menu")
                            }
                            Text("MotionLoop", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                            if (!isPro) {
                                ProPill(onClick = onOpenPro)
                            }
                        }
                        Text(
                            "Bring a still photo to life",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                        Spacer(Modifier.height(20.dp))
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
                    }
                }

                if (state.demos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            Text("Try a sample", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.demos, key = { it.photo.asset }) { demo ->
                                    Column(
                                        Modifier
                                            .width(132.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { vm.openDemo(demo.photo) },
                                    ) {
                                        Image(
                                            bitmap = demo.thumbnail.asImageBitmap(),
                                            // The title below is read instead.
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(14.dp)),
                                        )
                                        Text(demo.photo.title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                                        Text(
                                            demo.photo.hint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MlColors.TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        if (state.recent.isNotEmpty()) {
                            Text("Recent", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Long-press a project to delete it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Pick a photo, draw arrows where things should flow, and export a seamless loop.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(state.recent, key = { it.id }) { p ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .semantics { contentDescription = "Saved project" }
                            .combinedClickable(
                                onClickLabel = "Open project",
                                onLongClickLabel = "Delete project",
                                onLongClick = { pendingDelete = p.id },
                                onClick = { onOpenProject(p.id) },
                            ),
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
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
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

            if (showShare) {
                AlertDialog(
                    onDismissRequest = { showShare = false },
                    title = { Text("Share MotionLoop") },
                    text = {
                        Text(
                            if (vm.canShareApk) {
                                "Send a link to the app, or the app file itself for people who install it directly."
                            } else {
                                "Send a link to the app."
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showShare = false
                            MediaIntents.shareText(context, "Turn your photos into living loops with MotionLoop: ${AppLinks.PLAY_STORE}")
                        }) { Text("Share link") }
                    },
                    dismissButton = {
                        if (vm.canShareApk) {
                            TextButton(onClick = {
                                showShare = false
                                vm.shareApk()
                            }) { Text("Share app file") }
                        }
                    },
                )
            }

            if (state.importing) {
                Box(
                    Modifier.fillMaxSize().background(MlColors.Background.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            (state.error ?: notice)?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        Text(
                            "OK",
                            modifier = Modifier
                                .clickable {
                                    vm.dismissError()
                                    notice = null
                                }
                                .padding(8.dp),
                        )
                    },
                ) { Text(msg) }
            }
        }
    }
}

@Composable
private fun ProPill(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(MlColors.Violet, MlColors.Mint)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MlIcons.Pro, contentDescription = null, tint = MlColors.TextPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Pro", style = MaterialTheme.typography.labelMedium, color = MlColors.TextPrimary)
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
