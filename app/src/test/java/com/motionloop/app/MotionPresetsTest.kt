package com.motionloop.app

import com.motionloop.app.domain.model.MotionPaths
import com.motionloop.app.domain.model.MotionPreset
import com.motionloop.app.domain.model.MotionPresets
import com.motionloop.app.domain.model.TextAnimation
import com.motionloop.app.domain.model.TextLayer
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.NPoint
import com.motionloop.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MotionPresetsTest {

    private var next = 0
    private fun id() = "a${next++}"

    @Test
    fun everyPresetStaysInsideThePhotoOnAnyShape() {
        for (preset in MotionPreset.entries) {
            for (aspect in listOf(0.5f, 1f, 16f / 9f, 3f)) {
                val arrows = MotionPresets.arrows(preset, aspect, ::id)
                assertTrue("$preset has arrows", arrows.isNotEmpty())
                for (a in arrows) for (p in a.points) {
                    assertTrue("$preset x in range at $aspect", p.x in 0f..1f)
                    assertTrue("$preset y in range at $aspect", p.y in 0f..1f)
                }
            }
        }
    }

    @Test
    fun presetsFitTheFlowSegmentBudget() {
        // The flow shader takes 96 straight pieces: a preset must leave room for the person's own paths.
        for (preset in MotionPreset.entries) {
            val arrows = MotionPresets.arrows(preset, 1.5f, ::id)
            assertTrue(MotionPaths.segmentCount(arrows) <= 12)
        }
    }

    @Test
    fun directionsMatchTheirNames() {
        fun dir(preset: MotionPreset): Pair<Float, Float> {
            val a = MotionPresets.arrows(preset, 1.5f, ::id).first()
            return (a.end.x - a.start.x) to (a.end.y - a.start.y)
        }
        // Image y grows downward.
        assertTrue(dir(MotionPreset.WATERFALL).second > 0f)
        assertTrue(dir(MotionPreset.RISE).second < 0f)
        assertTrue(dir(MotionPreset.RIVER).first > 0f)
        assertTrue(dir(MotionPreset.CLOUDS).first > 0f)
    }

    @Test
    fun speedIsTheSameOnAnyPhotoShape() {
        // Length in image widths: a vertical arrow has the same speed on a tall and a wide photo.
        val tall = MotionPresets.arrows(MotionPreset.WATERFALL, 0.6f, ::id).first()
        val wide = MotionPresets.arrows(MotionPreset.WATERFALL, 1.8f, ::id).first()
        val lenTall = MotionPaths.length(tall.points, 0.6f)
        val lenWide = MotionPaths.length(wide.points, 1.8f)
        assertTrue(abs(lenTall - lenWide) < 1e-4f)
    }

    @Test
    fun idsAreUnique() {
        val arrows = MotionPresets.arrows(MotionPreset.CLOUDS, 1f, ::id)
        assertEquals(arrows.size, arrows.map { it.id }.toSet().size)
    }

    @Test
    fun stillProjectDoesNotAnimate() {
        val still = Project(id = "p", createdAt = 0, updatedAt = 0, imageWidth = 100, imageHeight = 100)
        assertFalse(still.animates)
        val withArrow = still.copy(arrows = listOf(MotionArrow("a", NPoint(0.2f, 0.2f), NPoint(0.4f, 0.2f))))
        assertTrue(withArrow.animates)
        val stillText = still.copy(texts = listOf(TextLayer(id = "t")))
        assertFalse(stillText.animates)
        val movingText = still.copy(texts = listOf(TextLayer(id = "t", animation = TextAnimation.FADE)))
        assertTrue(movingText.animates)
    }
}
