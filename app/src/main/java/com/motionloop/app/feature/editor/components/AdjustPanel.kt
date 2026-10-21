package com.motionloop.app.feature.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.ColorGrade
import com.motionloop.app.domain.model.ColorGrades
import com.motionloop.app.ui.theme.MlColors

/** Sample colors (shadow skin, foliage, sky, highlight) used to draw each grade's swatch. */
private val swatchSamples = listOf(
    floatArrayOf(0.35f, 0.22f, 0.18f),
    floatArrayOf(0.30f, 0.45f, 0.22f),
    floatArrayOf(0.45f, 0.62f, 0.85f),
    floatArrayOf(0.92f, 0.86f, 0.78f),
)

private fun FloatArray.toColor() = Color(this[0].coerceIn(0f, 1f), this[1].coerceIn(0f, 1f), this[2].coerceIn(0f, 1f))

@Composable
fun AdjustPanel(
    adjustments: Adjustments,
    hasCustom: Boolean,
    onChange: ((Adjustments) -> Adjustments) -> Unit,
    onSelectGrade: (String?) -> Unit,
    onImportCube: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read here: the swatch lambdas below are not composable.
    val mint = MlColors.Mint
    val high = MlColors.SurfaceHigh
    Column(modifier.fillMaxWidth()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GradeChip("None", selected = adjustments.gradeId == null, onClick = { onSelectGrade(null) }) {
                    swatchSamples.map { it.toColor() }
                }
            }
            items(ColorGrades.all, key = { it.id }) { grade ->
                GradeChip(grade.name, selected = adjustments.gradeId == grade.id, onClick = { onSelectGrade(grade.id) }) {
                    swatchColors(grade)
                }
            }
            if (hasCustom) {
                item {
                    GradeChip(
                        "Custom",
                        selected = adjustments.gradeId == Adjustments.CUSTOM_LUT_ID,
                        onClick = { onSelectGrade(Adjustments.CUSTOM_LUT_ID) },
                    ) { listOf(MlColors.Violet, mint, mint, MlColors.Violet) }
                }
            }
            item {
                // Importing again replaces the project's custom grade.
                GradeChip(if (hasCustom) "Replace .cube" else "Import .cube", selected = false, onClick = onImportCube) {
                    listOf(high, MlColors.Violet, mint, high)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (adjustments.gradeId != null) {
            LabeledSlider("Grade", adjustments.gradeIntensity, 0f..1f, { v -> onChange { it.copy(gradeIntensity = v) } })
        }
        LabeledSlider("Bright", adjustments.brightness, -1f..1f, { v -> onChange { it.copy(brightness = v) } })
        LabeledSlider("Contrast", adjustments.contrast, -1f..1f, { v -> onChange { it.copy(contrast = v) } })
        LabeledSlider("Saturate", adjustments.saturation, -1f..1f, { v -> onChange { it.copy(saturation = v) } })
        LabeledSlider("Warmth", adjustments.warmth, -1f..1f, { v -> onChange { it.copy(warmth = v) } })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "Reset",
                color = if (adjustments.isIdentity) MlColors.TextSecondary else MlColors.Mint,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !adjustments.isIdentity, onClick = onReset)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private fun swatchColors(grade: ColorGrade): List<Color> = swatchSamples.map { grade.transform(it.copyOf()).toColor() }

@Composable
private fun GradeChip(label: String, selected: Boolean, onClick: () -> Unit, colors: () -> List<Color>) {
    // Keyed on the colors too, so a theme switch refreshes the theme-colored swatches.
    val swatch = remember(label, colors) { colors() }
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MlColors.SurfaceHigh)
                .border(2.dp, if (selected) MlColors.Mint else Color.Transparent, RoundedCornerShape(10.dp)),
        ) {
            Canvas(Modifier.size(52.dp)) {
                val w = size.width / 2
                val h = size.height / 2
                swatch.forEachIndexed { i, c ->
                    drawRect(c, topLeft = Offset((i % 2) * w, (i / 2) * h), size = Size(w, h))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MlColors.Mint else MlColors.TextSecondary,
        )
    }
}
