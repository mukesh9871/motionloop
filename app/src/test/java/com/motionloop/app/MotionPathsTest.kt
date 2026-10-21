package com.motionloop.app

import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.MotionPaths
import com.motionloop.app.domain.model.NPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MotionPathsTest {

    @Test
    fun straightDragBecomesTwoPoints() {
        val line = (0..50).map { NPoint(0.1f + it * 0.01f, 0.5f) }
        assertEquals(listOf(line.first(), line.last()), MotionPaths.simplify(line, aspect = 1f))
    }

    @Test
    fun curveKeepsItsShapeWithinTheCap() {
        // Half circle, finely sampled.
        val arc = (0..200).map { i ->
            val t = PI * i / 200
            NPoint(0.5f + 0.3f * cos(t).toFloat(), 0.5f + 0.3f * sin(t).toFloat())
        }
        val simple = MotionPaths.simplify(arc, aspect = 1f)
        assertTrue("${simple.size} points", simple.size in 4..MotionPaths.MAX_POINTS)
        assertEquals(arc.first(), simple.first())
        assertEquals(arc.last(), simple.last())
        // Length stays close to the true half circumference.
        assertEquals((PI * 0.3).toFloat(), MotionPaths.length(simple, 1f), 0.03f)
    }

    @Test
    fun resampleIsEvenAndKeepsEnds() {
        val pts = listOf(NPoint(0f, 0f), NPoint(1f, 0f))
        val r = MotionPaths.resample(pts, 1f, 5)
        assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f, 1f), r.map { it.x })
    }

    @Test
    fun segmentSpeedMatchesPathLength() {
        val straight = MotionArrow("a", NPoint(0f, 0f), NPoint(0.2f, 0f))
        assertEquals(1f, MotionPaths.segments(listOf(straight), 1f, 96).single().speed, 1e-5f)

        // Two equal pieces: each moves as fast as the whole path is long (2x its own length).
        val bent = MotionArrow("b", NPoint(0f, 0f), NPoint(0.1f, 0.1f), listOf(NPoint(0f, 0f), NPoint(0.1f, 0f), NPoint(0.1f, 0.1f)))
        val segs = MotionPaths.segments(listOf(bent), 1f, 96)
        assertEquals(2, segs.size)
        segs.forEach { assertEquals(2f, it.speed, 1e-5f) }
        assertEquals(3, MotionPaths.segmentCount(listOf(straight, bent)))
    }

    @Test
    fun eraseDistanceUsesTheWholePath() {
        val bent = MotionArrow("b", NPoint(0f, 0f), NPoint(0.5f, 0.5f), listOf(NPoint(0f, 0f), NPoint(0.5f, 0f), NPoint(0.5f, 0.5f)))
        // Near the corner, far from the straight chord between start and end.
        assertEquals(0f, MotionPaths.distanceTo(bent, NPoint(0.5f, 0f), 1f), 1e-6f)
    }
}
