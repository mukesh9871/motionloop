package com.motionloop.app.feature.editor

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.motionloop.app.domain.model.MotionPreset
import com.motionloop.app.feature.editor.components.ToolModeRow
import com.motionloop.app.ui.components.MlMotion
import com.motionloop.app.ui.components.Pill
import com.motionloop.app.ui.components.pressBounce
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import com.motionloop.app.render.tiles.LocalTileRenderer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.NPoint
import com.motionloop.app.domain.model.ParticleType
import com.motionloop.app.domain.model.SelectionMode
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.domain.pro.ProFeature
import com.motionloop.app.domain.pro.ProPolicy
import com.motionloop.app.feature.editor.components.AdjustPanel
import com.motionloop.app.feature.editor.components.CutoutPanel
import com.motionloop.app.feature.editor.components.DepthPanel
import com.motionloop.app.feature.editor.components.EffectsPanel
import com.motionloop.app.feature.editor.components.LabeledSlider
import com.motionloop.app.feature.editor.components.OverlayPanel
import com.motionloop.app.feature.editor.components.SkyPanel
import com.motionloop.app.feature.editor.components.SoundPanel
import com.motionloop.app.feature.editor.components.TextPanel
import com.motionloop.app.feature.editor.components.PlaybackBar
import com.motionloop.app.feature.editor.components.ToolBar
import com.motionloop.app.feature.editor.tools.EditorCanvas
import com.motionloop.app.feature.export.ExportSheet
import com.motionloop.app.render.editor.GlPreview
import com.motionloop.app.ui.components.horizontalScrollWithBar
import com.motionloop.app.ui.components.verticalScrollWithBar
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors

@Composable
fun EditorScreen(onBack: () -> Unit, onOpenPro: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Runs the action, or opens the Pro page when the feature is locked for this user.
    val gated: (ProFeature, () -> Unit) -> Unit = { feature, action ->
        if (ProPolicy.isLocked(feature, isPro)) {
            vm.setPlaying(false)
            onOpenPro()
        } else {
            action()
        }
    }
    // Leaving the screen (Home, screen off, pickers, other pages) stops the preview and its sound.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    var showExport by rememberSaveable { mutableStateOf(false) }
    // The sheet is a dialog and does not pause the screen: the ViewModel pauses and holds autoplay meanwhile.
    LaunchedEffect(showExport) { vm.setExportOpen(showExport) }
    // .cube files have no registered MIME type, so accept any file and validate the contents.
    val pickCube = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importCube)
    }
    val pickSkyPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importSkyPhoto)
    }
    val touchPanel = state.panel == EditorPanel.ANIMATE || state.panel == EditorPanel.SKY ||
        state.panel == EditorPanel.CUTOUT || state.panel == EditorPanel.TEXT ||
        state.panel == EditorPanel.EFFECTS ||
        (state.panel == EditorPanel.OVERLAY && state.isOverlayBrush && state.project?.overlay?.particles != ParticleType.NONE)
    // Any audio file the device can decode.
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importAudio)
    }
    val pickBackgroundPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importBackgroundPhoto)
    }

    // Effect cards render on the shared reference photo.
    CompositionLocalProvider(LocalTileRenderer provides vm.tiles) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            TopBar(
                // A model run started from one history state must land on it: no undo meanwhile.
                canUndo = state.canUndo && state.busy == null,
                canRedo = state.canRedo && state.busy == null,
                exportEnabled = state.project != null && state.preview != null,
                onBack = onBack,
                onUndo = vm::undo,
                onRedo = vm::redo,
                onExport = {
                    vm.setPlaying(false)
                    showExport = true
                },
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val project = state.project
                when {
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> Text(state.error ?: "", color = MlColors.Coral)
                    // No rounded clip: clipping does not apply to a SurfaceView.
                    project != null -> Box(Modifier.aspectRatio(project.aspect)) {
                        // The GL view and the touch layer share the exact photo bounds.
                        GlPreview(scene = state.scene, modifier = Modifier.fillMaxSize())
                        EditorCanvas(
                            tool = state.tool,
                            arrows = state.visibleArrows,
                            anchors = project.anchors,
                            showGuides = !state.playing && !state.comparing && state.panel == EditorPanel.ANIMATE,
                            enabled = touchPanel && state.busy == null,
                            brushRadius = state.brushRadius,
                            highlight = state.selectedSticker
                                ?.takeIf { state.panel == EditorPanel.EFFECTS && !state.playing && !state.comparing }
                                ?.let { NPoint(it.x, it.y) to it.size / 2f },
                            showHint = state.panel == EditorPanel.ANIMATE && state.tool == EditorTool.ARROW &&
                                project.arrows.isEmpty() && state.liveArrow == null && !state.playing &&
                                !state.comparing && state.busy == null,
                            onDown = vm::pointerDown,
                            onMove = vm::pointerMove,
                            onUp = vm::pointerUp,
                            modifier = Modifier.fillMaxSize(),
                        )
                        state.busy?.let { label -> BusyBadge(label, Modifier.align(Alignment.BottomCenter)) }
                        // On the photo itself: the before and after is about the picture, and the top bar is full.
                        if (state.preview != null) {
                            CompareButton(
                                comparing = state.comparing,
                                onCompare = vm::setComparing,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                            )
                        }
                    }
                }
            }

            // Glass panel: lifted off the edges, rounded on top, a hairline of the accent along its rim.
            val panelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .clip(panelShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .border(1.dp, Brush.verticalGradient(listOf(MlColors.Violet.copy(alpha = 0.55f), Color.Transparent)), panelShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                val project = state.project
                PanelTabs(selected = state.panel, onSelect = vm::setPanel)
                Spacer(Modifier.height(6.dp))
                // A message belongs to the panel whose action produced it.
                state.message?.takeIf { state.messagePanel == null || state.messagePanel == state.panel }?.let { msg ->
                    Text(
                        msg,
                        color = MlColors.Coral,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = vm::dismissMessage)
                            .padding(vertical = 4.dp),
                    )
                }
                // One scroll area for every panel, with a visible bar when there is more below.
                AnimatedContent(
                    targetState = state.panel,
                    transitionSpec = {
                        // The size change is part of this transition: no second size animation outside.
                        ((fadeIn(MlMotion.gentle()) + slideInVertically(MlMotion.gentle()) { it / 8 }) togetherWith fadeOut(tween(120)))
                            .using(SizeTransform(clip = false) { _, _ -> MlMotion.gentle() })
                    },
                    label = "panel",
                ) { panel ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScrollWithBar(rememberScrollState()),
                    ) {
                        if (panel == EditorPanel.SOUND) {
                            if (project != null) {
                                SoundPanel(
                                    audio = project.audio,
                                    busy = state.busy != null,
                                    onChange = vm::setAudio,
                                    onPickFile = { gated(ProFeature.MUSIC) { pickAudio.launch(arrayOf("audio/*")) } },
                                )
                            }
                        } else if (panel == EditorPanel.TEXT) {
                            if (project != null) {
                                TextPanel(
                                    texts = project.texts,
                                    selected = state.selectedText,
                                    hasSubject = project.subject.hasArea,
                                    tool = state.tool,
                                    busy = state.busy != null,
                                    onAdd = vm::addText,
                                    onSelect = vm::selectText,
                                    onChange = vm::updateSelectedText,
                                    onDelete = vm::deleteSelectedText,
                                    onFindSubject = vm::detectSubject,
                                    onTool = vm::setTool,
                                )
                                if (state.isSubjectBrush) {
                                    BrushControls(state, vm)
                                }
                            }
                        } else if (panel == EditorPanel.CUTOUT) {
                            if (project != null) {
                                CutoutPanel(
                                    subject = project.subject,
                                    background = project.background,
                                    tool = state.tool,
                                    busy = state.busy != null,
                                    onDetect = vm::detectSubject,
                                    onTool = vm::setTool,
                                    onBackground = { change ->
                                        if (change(project.background).mode == BackgroundMode.PHOTO) {
                                            gated(ProFeature.BACKGROUND_PHOTO) { vm.setBackground(change) }
                                        } else {
                                            vm.setBackground(change)
                                        }
                                    },
                                    onFeather = vm::setSubjectFeather,
                                    onPickPhoto = {
                                        gated(ProFeature.BACKGROUND_PHOTO) {
                                            pickBackgroundPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    },
                                )
                                if (state.isSubjectBrush) {
                                    BrushControls(state, vm)
                                }
                            }
                        } else if (panel == EditorPanel.DEPTH) {
                            if (project != null) {
                                DepthPanel(
                                    settings = project.parallax,
                                    busy = state.busy != null,
                                    onCreateDepth = { gated(ProFeature.PARALLAX) { vm.createDepth() } },
                                    // Only turning 3D on (or changing it while on) is Pro. Turning it off always works.
                                    onChange = { change ->
                                        if (change(project.parallax).enabled) gated(ProFeature.PARALLAX) { vm.setParallax(change) } else vm.setParallax(change)
                                    },
                                )
                            }
                        } else if (panel == EditorPanel.SKY) {
                            if (project != null) {
                                SkyPanel(
                                    sky = project.sky,
                                    tool = state.tool,
                                    busy = state.busy != null,
                                    onDetect = vm::detectSky,
                                    onTool = vm::setTool,
                                    // Choosing the photo style again (after another style) must pass the gate too.
                                    onChange = { change ->
                                        val next = change(project.sky)
                                        if (next.style == SkyStyle.PHOTO && project.sky.style != SkyStyle.PHOTO) {
                                            gated(ProFeature.SKY_PHOTO) { vm.setSky(change) }
                                        } else {
                                            vm.setSky(change)
                                        }
                                    },
                                    onPickPhoto = {
                                        gated(ProFeature.SKY_PHOTO) {
                                            pickSkyPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    },
                                )
                                if (state.isSkyBrush) {
                                    BrushControls(state, vm)
                                }
                            }
                        } else if (panel == EditorPanel.OVERLAY) {
                            if (project != null) {
                                OverlayPanel(
                                    settings = project.overlay,
                                    tool = state.tool,
                                    onChange = vm::setOverlay,
                                    onTool = vm::setTool,
                                    onClearArea = vm::clearOverlayArea,
                                )
                                if (state.isOverlayBrush && project.overlay.particles != ParticleType.NONE) {
                                    BrushControls(state, vm)
                                }
                            }
                        } else if (panel == EditorPanel.EFFECTS) {
                            if (project != null) {
                                EffectsPanel(
                                    effects = project.effects,
                                    selected = state.selectedSticker,
                                    tool = state.tool,
                                    busy = state.busy != null,
                                    waterCount = project.selections.count { it.mode == SelectionMode.RIPPLE },
                                    onTool = vm::setTool,
                                    onChange = vm::setEffects,
                                    onAddSticker = vm::addSticker,
                                    onSelectSticker = vm::selectSticker,
                                    onChangeSticker = vm::updateSelectedSticker,
                                    onDeleteSticker = vm::deleteSelectedSticker,
                                    onClearDispersion = vm::clearDispersion,
                                    onClearWater = vm::clearWater,
                                )
                                if (state.isEffectBrush) {
                                    BrushControls(state, vm)
                                }
                            }
                        } else if (panel == EditorPanel.ADJUST) {
                            if (project != null) {
                                AdjustPanel(
                                    adjustments = project.adjustments,
                                    hasCustom = state.hasCustomLut,
                                    onChange = vm::setAdjustments,
                                    onSelectGrade = vm::selectGrade,
                                    onImportCube = { gated(ProFeature.CUSTOM_LUT) { pickCube.launch(arrayOf("*/*")) } },
                                    onReset = vm::resetAdjustments,
                                )
                            }
                        } else if (state.isBrush) {
                            ToolModeRow(state.tool, vm::setTool)
                            BrushControls(state, vm)
                        } else {
                            ToolModeRow(state.tool, vm::setTool)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    hintFor(state.tool),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MlColors.TextSecondary,
                                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                )
                                val selecting = state.tool == EditorTool.SELECT_FREEZE || state.tool == EditorTool.SELECT_ANIMATE
                                if (selecting && project?.selections?.any { it.mode != SelectionMode.RIPPLE } == true) {
                                    Text(
                                        "Clear",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MlColors.Mint,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = vm::clearSelections)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                            // A new photo: one tap gives a moving result to refine, instead of a blank start.
                            if (state.tool == EditorTool.ARROW && project?.arrows?.isEmpty() == true) {
                                QuickStart(onPick = vm::applyPreset)
                            }
                        }
                    }
                }
                if (project != null) {
                    PlaybackBar(
                        playing = state.playing,
                        speed = project.loop.speed,
                        mode = project.loop.mode,
                        onTogglePlay = vm::togglePlay,
                        onSpeed = vm::setSpeed,
                        onMode = vm::setLoopMode,
                    )
                }
                if (state.panel == EditorPanel.ANIMATE) {
                    Spacer(Modifier.height(8.dp))
                    ToolBar(selected = state.tool, onSelect = vm::setTool)
                }
            }
        }
    }

    if (showExport) {
        ExportSheet(
            onDismiss = { showExport = false },
            onOpenPro = {
                vm.setPlaying(false)
                onOpenPro()
            },
        )
    }
}

@Composable
private fun PanelTabs(selected: EditorPanel, onSelect: (EditorPanel) -> Unit) {
    val haptics = LocalHapticFeedback.current
    // Eight panels do not fit narrow phones at a readable size: the row scrolls, and its bar shows there is more.
    Row(
        Modifier
            .fillMaxWidth()
            // A modest radius: a full pill would clip the scroll bar's ends.
            .clip(RoundedCornerShape(16.dp))
            .background(MlColors.SurfaceHigh.copy(alpha = 0.7f))
            .selectableGroup()
            .horizontalScrollWithBar(rememberScrollState()),
    ) {
        EditorPanel.entries.forEach { panel ->
            val active = panel == selected
            // The highlight glides between tabs instead of jumping.
            val fill by animateColorAsState(if (active) MlColors.Violet else Color.Transparent, MlMotion.colorSpring(), label = "tabFill")
            val ink by animateColorAsState(if (active) Color.White else MlColors.TextSecondary, MlMotion.colorSpring(), label = "tabInk")
            Text(
                panel.label,
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(fill)
                    .selectable(selected = active, role = Role.Tab) {
                        if (!active) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(panel)
                    }
                    .heightIn(min = 44.dp)
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun BusyBadge(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(50))
            .background(MlColors.Background.copy(alpha = 0.8f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MlColors.Mint)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MlColors.TextPrimary)
    }
}

/** One-tap motion presets for a photo with no motion yet. */
@Composable
private fun QuickStart(onPick: (MotionPreset) -> Unit) {
    Text(
        "Or start with",
        style = MaterialTheme.typography.labelMedium,
        color = MlColors.TextSecondary,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScrollWithBar(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MotionPreset.entries.forEach { preset ->
            // The chip gives its own tick.
            Pill(preset.label, active = false) { onPick(preset) }
        }
    }
}

private fun hintFor(tool: EditorTool) = when (tool) {
    EditorTool.ARROW -> "Draw the path things should follow. Longer paths move faster."
    EditorTool.ANCHOR -> "Tap to pin points that must stay still."
    EditorTool.ERASE -> "Tap an arrow or anchor to remove it."
    EditorTool.SELECT_FREEZE -> "Tap an object to hold it still."
    EditorTool.SELECT_ANIMATE -> "Tap an object: only it will move, everything else holds still."
    else -> ""
}

@Composable
private fun TopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    exportEnabled: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) { Icon(MlIcons.Back, contentDescription = "Back", tint = MlColors.TextPrimary) }
        Spacer(Modifier.weight(1f))
        // Undo and redo in one framed group, labelled, so they read as buttons.
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MlColors.SurfaceHigh),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HistoryButton("Undo", MlIcons.Undo, canUndo, onUndo)
            HistoryButton("Redo", MlIcons.Redo, canRedo, onRedo)
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (exportEnabled) MlColors.Mint else MlColors.SurfaceHigh)
                .clickable(enabled = exportEnabled, onClick = onExport)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = if (exportEnabled) MlColors.OnMint else MlColors.TextSecondary
            Icon(MlIcons.Export, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export", color = ink, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(8.dp))
    }
}

/**
 * Hold to see the photo as imported, let go to see the result again. Accessibility services get a
 * tap action that switches between the two instead, since they cannot hold.
 */
@Composable
private fun CompareButton(comparing: Boolean, onCompare: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val latest by rememberUpdatedState(onCompare)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .pressBounce(source, pressedScale = 0.88f)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .semantics {
                contentDescription = if (comparing) "Showing the original photo" else "Hold to see the original photo"
                onClick(label = if (comparing) "Show the result" else "Show the original") {
                    latest(!comparing)
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        source.emit(press)
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        latest(true)
                        // finally: a cancelled gesture (the button leaving the screen) must not stick on the original.
                        val released = try {
                            tryAwaitRelease()
                        } finally {
                            latest(false)
                        }
                        source.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MlIcons.Eye, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HistoryButton(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) MlColors.TextPrimary else MlColors.TextSecondary.copy(alpha = 0.4f)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Brush size and softness, the same control wherever a brush tool is active. */
@Composable
private fun BrushControls(state: EditorUiState, vm: EditorViewModel) {
    LabeledSlider("Brush", state.brushRadius, 0.01f..0.2f, vm::setBrushRadius)
    LabeledSlider("Softness", 1f - state.brushHardness, 0.05f..1f, { vm.setBrushHardness(1f - it) })
}
