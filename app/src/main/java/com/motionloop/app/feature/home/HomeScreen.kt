package com.motionloop.app.feature.home

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
    onOpenContact: () -> Unit,
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
            // If Back or a second tap cancels the close, this coroutine stops here and the
            // superseded action is dropped: one tap, one navigation.
            drawer.close()
            action()
        }
    }

    // Back closes an open drawer instead of leaving the app.
    // Back while the drawer is open: ModalNavigationDrawer handles it itself, with predictive back.

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
                onContact = { closeThen(onOpenContact) },
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
            // One scrolling grid for the whole page, two columns, so samples and projects scroll together.
            LazyVerticalGrid(
                // Two columns on phones, more on tablets and unfolded foldables.
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { drawer.open() } }) {
                                Icon(MlIcons.Menu, contentDescription = "Menu", tint = MlColors.TextPrimary)
                            }
                            Wordmark(Modifier.weight(1f))
                            if (!isPro) {
                                ProPill(onClick = onOpenPro)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        CreateCard(
                            onGallery = {
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onCamera = {
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

                if (state.demos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("Try a sample", "Free photos to experiment with")
                    }
                    items(state.demos, key = { it.photo.asset }) { demo ->
                        SampleCard(demo, onClick = { vm.openDemo(demo.photo) })
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (state.recent.isNotEmpty()) {
                        SectionHeader("Your loops", "Long-press a project to delete it.")
                    } else {
                        Text(
                            "Pick a photo, draw paths where things should flow, and export a seamless loop.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MlColors.TextSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                itemsIndexed(state.recent, key = { _, p -> p.id }) { index, p ->
                    val label = "Saved project ${index + 1} of ${state.recent.size}"
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MlColors.SurfaceHigh)
                            .semantics { contentDescription = label }
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
                // Swallows touches: a tap on a project meanwhile would open a second editor.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MlColors.Background.copy(alpha = 0.6f))
                        .pointerInput(Unit) { }
                        .semantics { contentDescription = "Opening the photo" },
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            (state.error ?: notice)?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = {
                            vm.dismissError()
                            notice = null
                        }) { Text("OK") }
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
            .background(MlColors.Violet)
            .clickable(onClickLabel = "Open MotionLoop Pro", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MlIcons.Pro, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Pro", style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

/** "Motion" plus a violet "Loop": the only decoration in the header. */
@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("Motion")
            withStyle(SpanStyle(color = MlColors.Violet)) { append("Loop") }
        },
        style = MaterialTheme.typography.headlineMedium,
        color = MlColors.TextPrimary,
        modifier = modifier,
    )
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MlColors.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MlColors.TextSecondary)
    }
}

/** The one primary action: a calm surface with a thin gradient edge, two clear choices inside. */
@Composable
private fun CreateCard(onGallery: () -> Unit, onCamera: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(MlColors.Violet.copy(alpha = 0.22f), MlColors.Surface)))
            .border(1.dp, Brush.linearGradient(listOf(MlColors.Violet, MlColors.Arrow.copy(alpha = 0.6f))), shape)
            .padding(20.dp),
    ) {
        Text("Bring a still photo to life", style = MaterialTheme.typography.titleMedium, color = MlColors.TextPrimary)
        Text(
            "Draw where it flows. Export a seamless loop.",
            style = MaterialTheme.typography.bodySmall,
            color = MlColors.TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceButton("Gallery", MlIcons.Gallery, filled = true, modifier = Modifier.weight(1f), onClick = onGallery)
            SourceButton("Camera", MlIcons.Camera, filled = false, modifier = Modifier.weight(1f), onClick = onCamera)
        }
    }
}

@Composable
private fun SourceButton(label: String, icon: ImageVector, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val content = if (filled) Color.White else MlColors.TextPrimary
    Row(
        modifier
            .clip(shape)
            .background(if (filled) MlColors.Violet else Color.Transparent)
            .border(1.dp, if (filled) Color.Transparent else MlColors.TextSecondary.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = content)
    }
}

/** Photo card with the title on a dark scrim, readable on any photo and in both themes. */
@Composable
private fun SampleCard(demo: DemoThumb, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Open sample", onClick = onClick),
    ) {
        Image(
            bitmap = demo.thumbnail.asImageBitmap(),
            // The title on the card is read instead.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 12.dp),
        ) {
            Text(demo.photo.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                demo.photo.hint,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
