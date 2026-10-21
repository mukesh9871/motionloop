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
    fun degeneratePathsDoNotThrow() {
        val one = listOf(NPoint(0.5f, 0.5f))
        assertEquals(one, MotionPaths.simplify(one, 1f))
        val two = listOf(NPoint(0.1f, 0.1f), NPoint(0.2f, 0.2f))
        assertEquals(two, MotionPaths.simplify(two, 1f))
        // A tap without a drag: many identical points collapse to the two ends.
        val same = List(50) { NPoint(0.3f, 0.3f) }
        assertEquals(2, MotionPaths.simplify(same, 1f).size)
        assertEquals(listOf(same.first(), same.last()), MotionPaths.resample(same, 1f, 5))
        val still = MotionArrow("s", NPoint(0.3f, 0.3f), NPoint(0.3f, 0.3f))
        assertTrue(MotionPaths.segments(listOf(still), 1f, 96).isEmpty())
        assertEquals(1, MotionPaths.segmentCount(listOf(still)))
        val odd = listOf(NPoint(0f, 0f), NPoint(Float.NaN, 0.5f), NPoint(1f, 1f))
        MotionPaths.simplify(odd, 1f)
    }

    @Test
    fun distancesAreInImageWidths() {
        // On a 2:1 photo a vertical step of 0.5 of the height is 0.25 widths.
        val tall = MotionArrow("v", NPoint(0.5f, 0f), NPoint(0.5f, 0.5f))
        assertEquals(0.25f, MotionPaths.length(tall.points, 2f), 1e-6f)
        assertEquals(0.1f, MotionPaths.distanceTo(tall, NPoint(0.6f, 0.25f), 2f), 1e-6f)
    }

    @Test
    fun eraseDistanceUsesTheWholePath() {
        val bent = MotionArrow("b", NPoint(0f, 0f), NPoint(0.5f, 0.5f), listOf(NPoint(0f, 0f), NPoint(0.5f, 0f), NPoint(0.5f, 0.5f)))
        // Near the corner, far from the straight chord between start and end.
        assertEquals(0f, MotionPaths.distanceTo(bent, NPoint(0.5f, 0f), 1f), 1e-6f)
    }
}
