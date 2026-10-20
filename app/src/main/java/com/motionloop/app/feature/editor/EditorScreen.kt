package com.motionloop.app.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motionloop.app.feature.editor.components.AdjustPanel
import com.motionloop.app.feature.editor.components.LabeledSlider
import com.motionloop.app.feature.editor.components.PlaybackBar
import com.motionloop.app.feature.editor.components.ToolBar
import com.motionloop.app.feature.editor.tools.EditorCanvas
import com.motionloop.app.feature.export.ExportSheet
import com.motionloop.app.render.editor.GlPreview
import com.motionloop.app.ui.icons.MlIcons
import com.motionloop.app.ui.theme.MlColors

@Composable
fun EditorScreen(onBack: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showExport by rememberSaveable { mutableStateOf(false) }
    // .cube files have no registered MIME type, so accept any file and validate the contents.
    val pickCube = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importCube)
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
                        enabled = state.panel == EditorPanel.ANIMATE,
                        brushRadius = state.brushRadius,
                        onDown = vm::pointerDown,
                        onMove = vm::pointerMove,
                        onUp = vm::pointerUp,
                        modifier = Modifier.fillMaxSize(),
                    )
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
            if (state.panel == EditorPanel.ADJUST) {
                if (project != null) {
                    AdjustPanel(
                        adjustments = project.adjustments,
                        onChange = vm::setAdjustments,
                        onSelectGrade = vm::selectGrade,
                        onImportCube = { pickCube.launch(arrayOf("*/*")) },
                        onReset = vm::resetAdjustments,
                    )
                }
            } else if (state.isBrush) {
                LabeledSlider("Size", state.brushRadius, 0.01f..0.2f, vm::setBrushRadius)
                LabeledSlider("Softness", 1f - state.brushHardness, 0.05f..1f, { vm.setBrushHardness(1f - it) })
            } else {
                Text(
                    hintFor(state.tool),
                    style = MaterialTheme.typography.bodySmall,
                    color = MlColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
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
        ExportSheet(onDismiss = { showExport = false })
    }
}

@Composable
private fun PanelTabs(selected: EditorPanel, onSelect: (EditorPanel) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MlColors.SurfaceHigh),
    ) {
        EditorPanel.entries.forEach { panel ->
            val active = panel == selected
            Text(
                if (panel == EditorPanel.ANIMATE) "Animate" else "Adjust",
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MlColors.TextPrimary else MlColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MlColors.Violet else Color.Transparent)
                    .clickable { onSelect(panel) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

private fun hintFor(tool: EditorTool) = when (tool) {
    EditorTool.ARROW -> "Drag to draw arrows. Longer arrows move faster."
    EditorTool.ANCHOR -> "Tap to pin points that must stay still."
    EditorTool.ERASE -> "Tap an arrow or anchor to remove it."
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
