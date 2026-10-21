package com.motionloop.app.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.pro.ProFeature
import com.motionloop.app.domain.pro.ProPolicy
import com.motionloop.app.feature.editor.components.AdjustPanel
import com.motionloop.app.feature.editor.components.CutoutPanel
import com.motionloop.app.feature.editor.components.DepthPanel
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
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors

@Composable
fun EditorScreen(onBack: () -> Unit, onOpenPro: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Runs the action, or opens the Pro page when the feature is locked for this user.
    val gated: (ProFeature, () -> Unit) -> Unit = { feature, action ->
        if (ProPolicy.isLocked(feature, isPro)) onOpenPro() else action()
    }
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    var showExport by rememberSaveable { mutableStateOf(false) }
    // .cube files have no registered MIME type, so accept any file and validate the contents.
    val pickCube = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importCube)
    }
    val pickSkyPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importSkyPhoto)
    }
    val touchPanel = state.panel == EditorPanel.ANIMATE || state.panel == EditorPanel.SKY ||
        state.panel == EditorPanel.CUTOUT || state.panel == EditorPanel.TEXT
    // Any audio file the device can decode.
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importAudio)
    }
    val pickBackgroundPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importBackgroundPhoto)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        TopBar(
            canUndo = state.canUndo,
            canRedo = state.canRedo,
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
                        showGuides = !state.playing && state.panel == EditorPanel.ANIMATE,
                        enabled = touchPanel && state.busy == null,
                        brushRadius = state.brushRadius,
                        onDown = vm::pointerDown,
                        onMove = vm::pointerMove,
                        onUp = vm::pointerUp,
                        modifier = Modifier.fillMaxSize(),
                    )
                    state.busy?.let { label -> BusyBadge(label, Modifier.align(Alignment.BottomCenter)) }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            val project = state.project
            PanelTabs(selected = state.panel, onSelect = vm::setPanel)
            Spacer(Modifier.height(6.dp))
            state.message?.let { msg ->
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
            if (state.panel == EditorPanel.SOUND) {
                if (project != null) {
                    SoundPanel(
                        audio = project.audio,
                        busy = state.busy != null,
                        onChange = vm::setAudio,
                        onPickFile = { gated(ProFeature.MUSIC) { pickAudio.launch(arrayOf("audio/*")) } },
                    )
                }
            } else if (state.panel == EditorPanel.TEXT) {
                if (project != null) {
                    TextPanel(
                        texts = project.texts,
                        selected = state.selectedText,
                        hasSubject = project.subject.hasArea,
                        onAdd = vm::addText,
                        onSelect = vm::selectText,
                        onChange = vm::updateSelectedText,
                        onDelete = vm::deleteSelectedText,
                    )
                }
            } else if (state.panel == EditorPanel.CUTOUT) {
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
                        LabeledSlider("Size", state.brushRadius, 0.01f..0.2f, vm::setBrushRadius)
                    }
                }
            } else if (state.panel == EditorPanel.DEPTH) {
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
            } else if (state.panel == EditorPanel.SKY) {
                if (project != null) {
                    SkyPanel(
                        sky = project.sky,
                        tool = state.tool,
                        busy = state.busy != null,
                        onDetect = vm::detectSky,
                        onTool = vm::setTool,
                        onChange = vm::setSky,
                        onPickPhoto = {
                            gated(ProFeature.SKY_PHOTO) {
                                pickSkyPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        },
                    )
                    if (state.isSkyBrush) {
                        LabeledSlider("Size", state.brushRadius, 0.01f..0.2f, vm::setBrushRadius)
                    }
                }
            } else if (state.panel == EditorPanel.OVERLAY) {
                if (project != null) {
                    OverlayPanel(settings = project.overlay, onChange = vm::setOverlay)
                }
            } else if (state.panel == EditorPanel.ADJUST) {
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
                LabeledSlider("Size", state.brushRadius, 0.01f..0.2f, vm::setBrushRadius)
                LabeledSlider("Softness", 1f - state.brushHardness, 0.05f..1f, { vm.setBrushHardness(1f - it) })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        hintFor(state.tool),
                        style = MaterialTheme.typography.bodySmall,
                        color = MlColors.TextSecondary,
                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    )
                    val selecting = state.tool == EditorTool.SELECT_FREEZE || state.tool == EditorTool.SELECT_ANIMATE
                    if (selecting && project?.selections?.isNotEmpty() == true) {
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

    if (showExport) {
        ExportSheet(onDismiss = { showExport = false }, onOpenPro = onOpenPro)
    }
}

@Composable
private fun PanelTabs(selected: EditorPanel, onSelect: (EditorPanel) -> Unit) {
    // Six panels do not fit narrow phones at a readable size: the row scrolls.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MlColors.SurfaceHigh)
            .horizontalScroll(rememberScrollState()),
    ) {
        EditorPanel.entries.forEach { panel ->
            val active = panel == selected
            Text(
                panel.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MlColors.TextPrimary else MlColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MlColors.Violet else Color.Transparent)
                    .clickable { onSelect(panel) }
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

private fun hintFor(tool: EditorTool) = when (tool) {
    EditorTool.ARROW -> "Drag to draw arrows. Longer arrows move faster."
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
        IconButton(onClick = onBack) { Icon(MlIcons.Back, contentDescription = "Back") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onUndo, enabled = canUndo) { Icon(MlIcons.Undo, contentDescription = "Undo") }
        IconButton(onClick = onRedo, enabled = canRedo) { Icon(MlIcons.Redo, contentDescription = "Redo") }
        Spacer(Modifier.width(4.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (exportEnabled) MlColors.Mint else MlColors.SurfaceHigh)
                .clickable(enabled = exportEnabled, onClick = onExport)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MlIcons.Export, contentDescription = null, tint = MlColors.Background, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export", color = MlColors.Background, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(8.dp))
    }
}
