package com.motionloop.app.domain.model

import kotlin.math.hypot
import kotlin.math.max

/** One straight piece of a motion path, ready for the flow shader. */
data class FlowSegment(val a: NPoint, val b: NPoint, val speed: Float)

/**
 * Free-hand motion paths: simplify what the finger drew, and split paths into straight
 * segments for the flow field. Distances are in image widths (y scaled by height / width),
 * like every other distance in the app.
 */
object MotionPaths {

    /** Points a single path keeps at most, so many paths still fit the shader's segment budget. */
    const val MAX_POINTS = 16

    /** Ramer-Douglas-Peucker in aspect space, then capped at [MAX_POINTS] by arc-length resampling. */
    fun simplify(points: List<NPoint>, aspect: Float, epsilon: Float = 0.006f): List<NPoint> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        rdp(points, 0, points.lastIndex, aspect, epsilon, keep)
        val simple = points.filterIndexed { i, _ -> keep[i] }
        return if (simple.size <= MAX_POINTS) simple else resample(simple, aspect, MAX_POINTS)
    }

    private fun rdp(p: List<NPoint>, from: Int, to: Int, aspect: Float, eps: Float, keep: BooleanArray) {
        if (to - from < 2) return
        var maxD = -1f
        var index = from
        for (i in from + 1 until to) {
            val d = distanceToSegment(p[i], p[from], p[to], aspect)
            if (d > maxD) {
                maxD = d
                index = i
            }
        }
        if (maxD > eps) {
            keep[index] = true
            rdp(p, from, index, aspect, eps, keep)
            rdp(p, index, to, aspect, eps, keep)
        }
    }

    /** [count] points evenly spaced along the polyline, including both ends. */
    fun resample(points: List<NPoint>, aspect: Float, count: Int): List<NPoint> {
        val total = length(points, aspect)
        if (total <= 0f || count < 2) return listOf(points.first(), points.last())
        val out = ArrayList<NPoint>(count)
        out += points.first()
        var seg = 0
        var segStart = 0f
        for (k in 1 until count - 1) {
            val target = total * k / (count - 1)
            while (seg < points.lastIndex - 1 && segStart + dist(points[seg], points[seg + 1], aspect) < target) {
                segStart += dist(points[seg], points[seg + 1], aspect)
                seg++
            }
            val a = points[seg]
            val b = points[seg + 1]
            val len = dist(a, b, aspect)
            val t = if (len > 0f) ((target - segStart) / len).coerceIn(0f, 1f) else 0f
            out += NPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        out += points.last()
        return out
    }

    fun length(points: List<NPoint>, aspect: Float): Float {
        var sum = 0f
        for (i in 1 until points.size) sum += dist(points[i - 1], points[i], aspect)
        return sum
    }

    /**
     * Straight pieces of every path. Each one points along its piece and is scaled so its
     * speed equals the whole path's length: a longer path moves faster, like a longer arrow.
     */
    fun segments(arrows: List<MotionArrow>, aspect: Float, max: Int): List<FlowSegment> {
        val out = ArrayList<FlowSegment>()
        for (arrow in arrows) {
            val pts = arrow.points
            val total = length(pts, aspect)
            for (i in 1 until pts.size) {
                if (out.size >= max) return out
                val len = dist(pts[i - 1], pts[i], aspect)
                if (len <= 1e-6f) continue
                out += FlowSegment(pts[i - 1], pts[i], total / len)
            }
        }
        return out
    }

    fun segmentCount(arrows: List<MotionArrow>): Int = arrows.sumOf { max(1, it.points.size - 1) }

    fun distanceTo(arrow: MotionArrow, p: NPoint, aspect: Float): Float {
        val pts = arrow.points
        var best = Float.MAX_VALUE
        for (i in 1 until pts.size) best = minOf(best, distanceToSegment(p, pts[i - 1], pts[i], aspect))
        return best
    }

    private fun dist(a: NPoint, b: NPoint, aspect: Float) = hypot(a.x - b.x, (a.y - b.y) / aspect)

    private fun distanceToSegment(p: NPoint, a: NPoint, b: NPoint, aspect: Float): Float {
        val abx = b.x - a.x
        val aby = (b.y - a.y) / aspect
        val apx = p.x - a.x
        val apy = (p.y - a.y) / aspect
        val lenSq = abx * abx + aby * aby
        val t = if (lenSq <= 1e-12f) 0f else ((apx * abx + apy * aby) / lenSq).coerceIn(0f, 1f)
        return hypot(apx - abx * t, apy - aby * t)
    }
}
