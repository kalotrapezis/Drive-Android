package com.kalotrapezis.drive

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class ScanPoint(val x: Float, val y: Float)

data class DocumentQuad(
    val topLeft: ScanPoint,
    val topRight: ScanPoint,
    val bottomRight: ScanPoint,
    val bottomLeft: ScanPoint,
) {
    val points get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        fun fullFrame() = DocumentQuad(ScanPoint(0f, 0f), ScanPoint(1f, 0f), ScanPoint(1f, 1f), ScanPoint(0f, 1f))
    }
}

data class LumaFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val pixelStride: Int,
)

object ScanDetection {
    private var initialized = false

    /**
     * First searches for a bright paper-shaped quadrilateral, then falls back to all strong edges.
     * It deliberately does not classify page types: a receipt and an ID use the same corner path.
     */
    fun detect(frame: LumaFrame): DocumentQuad? {
        if (!initialize()) return null
        val sample = sample(frame) ?: return null
        return try {
            brightPaperQuad(sample) ?: edgeQuad(sample)
        } finally {
            sample.release()
        }
    }

    /**
     * Four corners that could be a page: convex, and no corner folded flat or bent double. A page seen at an
     * angle is a trapezoid, so the test is deliberately generous — 45° to 135° covers any view you can actually
     * read from — but a shape with a 20° spike is a shadow, a tile edge or a hand, and saying so out loud is
     * cheaper than showing an outline that jumps away a frame later.
     */
    internal fun isPageShaped(quad: DocumentQuad): Boolean {
        val points = quad.points
        var sign = 0
        for (index in 0 until 4) {
            val previous = points[(index + 3) % 4]
            val corner = points[index]
            val next = points[(index + 1) % 4]
            val ax = previous.x - corner.x; val ay = previous.y - corner.y
            val bx = next.x - corner.x; val by = next.y - corner.y
            val lengths = kotlin.math.hypot(ax, ay) * kotlin.math.hypot(bx, by)
            if (lengths <= 0f) return false
            val angle = Math.toDegrees(kotlin.math.acos(((ax * bx + ay * by) / lengths).coerceIn(-1f, 1f)).toDouble())
            if (angle < 45.0 || angle > 135.0) return false
            val cross = ax * by - ay * bx // every turn the same way, or it is not convex
            val turn = if (cross > 0f) 1 else -1
            if (sign == 0) sign = turn else if (sign != turn) return false
        }
        return true
    }

    /** A page the screen asked you to keep inside the frame. A shape running off the edge is not one. */
    internal fun isInsideFrame(quad: DocumentQuad): Boolean =
        quad.points.none { it.x < 0.02f || it.x > 0.98f || it.y < 0.02f || it.y > 0.98f }

    /**
     * A torn or crumpled edge is still a straight edge: the paper was cut straight once, and the tear is the
     * part to ignore. Each side is fitted to the outline points along it rather than taken from two corners, so
     * a bite out of one edge moves the line by the little it deserves instead of dragging a corner with it, and
     * the corners come back as where those four lines cross.
     *
     * Points far from the first fit are dropped once and the line refitted, which is what keeps a torn flap
     * sticking out of one edge from tilting the whole side.
     */
    internal fun straighten(outline: List<ScanPoint>, corners: List<ScanPoint>): List<ScanPoint>? {
        if (corners.size != 4 || outline.size < 4) return null
        val at = corners.map { corner -> outline.indices.minBy { distance(outline[it], corner) } }
        val lines = List(4) { side ->
            val from = at[side]
            val to = at[(side + 1) % 4]
            val along = buildList {
                var index = from
                add(outline[index])
                while (index != to) { index = (index + 1) % outline.size; add(outline[index]) }
            }
            fitLine(along) ?: fitLine(listOf(corners[side], corners[(side + 1) % 4])) ?: return null
        }
        return List(4) { corner ->
            intersect(lines[(corner + 3) % 4], lines[corner]) ?: corners[corner]
        }
    }

    /** A line as a point on it and a unit direction, from the points' own principal axis. */
    private fun fitLine(points: List<ScanPoint>): Line? {
        if (points.size < 2) return null
        fun fit(subset: List<ScanPoint>): Line? {
            if (subset.size < 2) return null
            val cx = subset.sumOf { it.x.toDouble() } / subset.size
            val cy = subset.sumOf { it.y.toDouble() } / subset.size
            var xx = 0.0; var yy = 0.0; var xy = 0.0
            subset.forEach { point ->
                val dx = point.x - cx; val dy = point.y - cy
                xx += dx * dx; yy += dy * dy; xy += dx * dy
            }
            val theta = 0.5 * kotlin.math.atan2(2 * xy, xx - yy)
            val dx = kotlin.math.cos(theta).toFloat(); val dy = kotlin.math.sin(theta).toFloat()
            if (dx == 0f && dy == 0f) return null
            return Line(ScanPoint(cx.toFloat(), cy.toFloat()), ScanPoint(dx, dy))
        }
        val first = fit(points) ?: return null
        if (points.size < 5) return first
        val distances = points.map { offset(first, it) }
        val mean = distances.average().toFloat()
        val kept = points.filterIndexed { index, _ -> distances[index] <= mean * 1.5f + 1e-6f }
        return fit(kept) ?: first
    }

    private fun offset(line: Line, point: ScanPoint): Float =
        abs((point.x - line.point.x) * line.direction.y - (point.y - line.point.y) * line.direction.x)

    private fun intersect(first: Line, second: Line): ScanPoint? {
        val denominator = first.direction.x * second.direction.y - first.direction.y * second.direction.x
        if (abs(denominator) < 1e-6f) return null // two sides that never meet are not two sides
        val dx = second.point.x - first.point.x
        val dy = second.point.y - first.point.y
        val t = (dx * second.direction.y - dy * second.direction.x) / denominator
        return ScanPoint(first.point.x + first.direction.x * t, first.point.y + first.direction.y * t)
    }

    private data class Line(val point: ScanPoint, val direction: ScanPoint)

    /**
     * Moves the outline most of the way towards the new reading rather than jumping to it. Camera noise moves a
     * corner by a pixel or two every frame; following that exactly is what made the page look like it was
     * shivering, and a detection that shivers never looks settled enough to trust.
     */
    fun smooth(previous: DocumentQuad?, next: DocumentQuad, weight: Float = 0.45f): DocumentQuad {
        if (previous == null || !isStable(previous, next)) return next // a real move is followed at once
        val blended = previous.points.zip(next.points).map { (before, after) ->
            ScanPoint(before.x + (after.x - before.x) * weight, before.y + (after.y - before.y) * weight)
        }
        return DocumentQuad(blended[0], blended[1], blended[2], blended[3])
    }

    fun isStable(previous: DocumentQuad?, next: DocumentQuad): Boolean = previous != null &&
        previous.points.zip(next.points).all { (before, after) -> abs(before.x - after.x) <= 0.05f && abs(before.y - after.y) <= 0.05f }

    private fun initialize(): Boolean {
        if (initialized) return true
        initialized = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        return initialized
    }

    private fun sample(frame: LumaFrame): Mat? {
        if (frame.width < 32 || frame.height < 32) return null
        val scale = max(1, max(frame.width, frame.height) / 256)
        val width = frame.width / scale
        val height = frame.height / scale
        val luma = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val source = (y * scale) * frame.rowStride + (x * scale) * frame.pixelStride
            if (source !in frame.bytes.indices) return null
            luma[y * width + x] = frame.bytes[source]
        }
        return Mat(height, width, CvType.CV_8UC1).also { it.put(0, 0, luma) }
    }

    private fun brightPaperQuad(gray: Mat): DocumentQuad? {
        val histogram = IntArray(256)
        val bytes = ByteArray((gray.total() * gray.channels()).toInt())
        gray.get(0, 0, bytes)
        bytes.forEach { histogram[it.toInt() and 0xFF]++ }
        // With the light on, paper and table are both bright and a fixed floor of 170 stops telling them apart.
        // Otsu asks the picture where the two groups actually split; the percentile keeps it from splitting a
        // frame that holds no paper at all.
        val threshold = max(otsu(histogram, bytes.size), percentile(histogram, bytes.size, 70))
        val mask = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        return try {
            Imgproc.threshold(gray, mask, threshold.toDouble(), 255.0, Imgproc.THRESH_BINARY)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            bestQuad(mask, gray)
        } finally {
            kernel.release()
            mask.release()
        }
    }

    private fun edgeQuad(gray: Mat): DocumentQuad? {
        val blurred = Mat()
        val edges = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        return try {
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 130.0)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
            bestQuad(edges, gray)
        } finally {
            kernel.release()
            edges.release()
            blurred.release()
        }
    }

    /** The brightness that best splits the picture into two groups — paper and everything else (Otsu 1979). */
    internal fun otsu(histogram: IntArray, total: Int): Int {
        if (total <= 0) return 0
        val sum = histogram.indices.sumOf { (it * histogram[it]).toLong() }
        var backgroundWeight = 0L
        var backgroundSum = 0L
        var best = 0
        var bestVariance = -1.0
        for (value in 0 until 256) {
            backgroundWeight += histogram[value]
            if (backgroundWeight == 0L) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight <= 0L) break
            backgroundSum += (value * histogram[value]).toLong()
            val backgroundMean = backgroundSum.toDouble() / backgroundWeight
            val foregroundMean = (sum - backgroundSum).toDouble() / foregroundWeight
            val variance = backgroundWeight.toDouble() * foregroundWeight * (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)
            if (variance > bestVariance) { bestVariance = variance; best = value }
        }
        return best
    }

    private fun percentile(histogram: IntArray, total: Int, percentile: Int): Int {
        val target = total * percentile / 100
        var seen = 0
        histogram.forEachIndexed { value, count ->
            seen += count
            if (seen >= target) return value
        }
        return 255
    }

    private fun bestQuad(mask: Mat, gray: Mat): DocumentQuad? {
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        val sceneMean = Core.mean(gray).`val`[0].toFloat()
        return contours.mapNotNull { contour ->
            try {
                quadCorners(contour)?.let { (corners, hull) -> candidate(corners, hull, mask.width(), mask.height(), gray, sceneMean) }
            } finally {
                contour.release()
            }
        }.maxByOrNull { it.score }?.quad
    }

    /** Average brightness well inside a quad, from a handful of samples — enough to tell paper from a table. */
    private fun innerBrightness(quad: DocumentQuad, gray: Mat): Float {
        var total = 0.0
        var count = 0
        val buffer = ByteArray(1)
        for (u in listOf(0.3f, 0.5f, 0.7f)) for (v in listOf(0.3f, 0.5f, 0.7f)) {
            val top = ScanPoint(
                quad.topLeft.x + (quad.topRight.x - quad.topLeft.x) * u,
                quad.topLeft.y + (quad.topRight.y - quad.topLeft.y) * u,
            )
            val bottom = ScanPoint(
                quad.bottomLeft.x + (quad.bottomRight.x - quad.bottomLeft.x) * u,
                quad.bottomLeft.y + (quad.bottomRight.y - quad.bottomLeft.y) * u,
            )
            val x = ((top.x + (bottom.x - top.x) * v) * gray.width()).toInt().coerceIn(0, gray.width() - 1)
            val y = ((top.y + (bottom.y - top.y) * v) * gray.height()).toInt().coerceIn(0, gray.height() - 1)
            gray.get(y, x, buffer)
            total += buffer[0].toInt() and 0xFF
            count++
        }
        return if (count == 0) 0f else (total / count).toFloat()
    }

    /**
     * Crumpled or wavy edges yield many vertices; simplify the convex hull until only the four page corners
     * remain. The hull itself comes back too, because the four corners alone cannot say where a straight edge
     * ran — the points along each side can.
     */
    private fun quadCorners(contour: MatOfPoint): Pair<Array<Point>, Array<Point>>? {
        val points = contour.toArray()
        if (points.size < 4) return null
        val hullIndices = org.opencv.core.MatOfInt()
        Imgproc.convexHull(contour, hullIndices)
        val hullPoints = hullIndices.toArray().map { points[it] }.toTypedArray()
        val hull = MatOfPoint2f(*hullPoints)
        hullIndices.release()
        return try {
            val perimeter = Imgproc.arcLength(hull, true)
            for (epsilon in listOf(0.02, 0.03, 0.045, 0.06, 0.08, 0.1)) {
                val approximation = MatOfPoint2f()
                Imgproc.approxPolyDP(hull, approximation, perimeter * epsilon, true)
                val corners = approximation.toArray()
                approximation.release()
                if (corners.size == 4) return corners to hullPoints
                if (corners.size < 4) return null
            }
            null
        } finally {
            hull.release()
        }
    }

    private fun candidate(points: Array<Point>, hull: Array<Point>, width: Int, height: Int, gray: Mat, sceneMean: Float): Candidate? {
        val pointContour = MatOfPoint(*points)
        val area = try { kotlin.math.abs(Imgproc.contourArea(pointContour)).toFloat() } finally { pointContour.release() }
        val coverage = area / (width * height)
        // A long receipt has to be held far enough away to fit, and then it is a thin ribbon in a wide frame:
        // 2.5% of the view ruled that out before anything else could judge it.
        if (coverage !in 0.008f..0.95f) return null
        val normalize = { point: Point -> ScanPoint((point.x / width).toFloat(), (point.y / height).toFloat()) }
        val corners = points.map(normalize)
        val straightened = straighten(hull.map(normalize), corners) ?: corners
        val ordered = orderCorners(straightened.map { ScanPoint(it.x.coerceIn(-0.05f, 1.05f), it.y.coerceIn(-0.05f, 1.05f)) })
            ?: orderCorners(corners) ?: return null
        val quad = DocumentQuad(ordered[0], ordered[1], ordered[2], ordered[3])
        if (!isPageShaped(quad)) return null
        val averageWidth = (distance(quad.topLeft, quad.topRight) + distance(quad.bottomLeft, quad.bottomRight)) / 2f
        val averageHeight = (distance(quad.topLeft, quad.bottomLeft) + distance(quad.topRight, quad.bottomRight)) / 2f
        val aspect = max(averageWidth, averageHeight) / min(averageWidth, averageHeight)
        if (aspect > 12f) return null
        val centerX = quad.points.sumOf { it.x.toDouble() }.toFloat() / 4f
        val centerY = quad.points.sumOf { it.y.toDouble() }.toFloat() / 4f
        val centered = (1f - (abs(centerX - 0.5f) * 1.5f + abs(centerY - 0.5f) * 0.3f)).coerceAtLeast(0f)
        // "Keep the page inside the frame" is what this screen asks for, so a shape running off the edge is the
        // table, the wall or the whole view — not the page. Penalising that was not enough: a wrong shape big
        // enough still won on size alone, and then the outline held on to it.
        if (!isInsideFrame(quad)) return null
        // Paper is brighter than what it is lying on. Without this, any large quadrilateral — a tile, a table
        // edge, the border between wood and floor — is as good a page as the page.
        val brightness = innerBrightness(quad, gray)
        if (brightness < sceneMean + 12f) return null
        val standsOut = ((brightness - sceneMean) / 60f).coerceIn(0f, 1f)
        return Candidate(quad, coverage * centered * (0.35f + 0.65f * standsOut))
    }

    /**
     * Orders corners clockwise (screen coordinates) starting from the top-left-most point.
     * Angle ordering stays correct for pages rotated near 45°, where x+y sorting ties.
     */
    internal fun orderCorners(points: List<ScanPoint>): List<ScanPoint>? {
        if (points.size != 4 || points.toSet().size != 4) return null
        val cx = points.sumOf { it.x.toDouble() } / 4
        val cy = points.sumOf { it.y.toDouble() } / 4
        val clockwise = points.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
        val start = clockwise.indices.minBy { clockwise[it].x + clockwise[it].y }
        return List(4) { clockwise[(start + it) % 4] }
    }

    /**
     * Maps a quad from raw analysis-buffer coordinates into the saved photo's upright coordinates.
     * Analysis and capture share one ViewPort, so both use the same crop rect and rotation.
     */
    internal fun toCaptureFrame(quad: DocumentQuad, frameWidth: Int, frameHeight: Int, cropLeft: Int, cropTop: Int, cropWidth: Int, cropHeight: Int, rotationDegrees: Int): DocumentQuad? {
        if (cropWidth <= 0 || cropHeight <= 0) return null
        val mapped = quad.points.map { point ->
            val u = ((point.x * frameWidth - cropLeft) / cropWidth).coerceIn(0f, 1f)
            val v = ((point.y * frameHeight - cropTop) / cropHeight).coerceIn(0f, 1f)
            when (rotationDegrees) {
                90 -> ScanPoint(1f - v, u)
                180 -> ScanPoint(1f - u, 1f - v)
                270 -> ScanPoint(v, 1f - u)
                else -> ScanPoint(u, v)
            }
        }
        val ordered = orderCorners(mapped) ?: return null
        return DocumentQuad(ordered[0], ordered[1], ordered[2], ordered[3])
    }

    /**
     * Paints background visible near the edges of a perspective-corrected page with the paper colour beside it.
     * Each side gets a reference colour sampled just inside the edge band and median-smoothed along the edge, so
     * shading continues without streaks. Only pixels that clearly differ from that local paper and connect to the
     * image border are painted: an edge that is already paper is left untouched, ink in the page body always is.
     * [pixels] are ARGB and are modified in place.
     */
    internal fun fillPageGaps(pixels: IntArray, width: Int, height: Int, bandFraction: Float = 0.06f, tolerance: Int = 48) {
        val band = maxOf(4, (minOf(width, height) * bandFraction).toInt())
        if (width <= band * 3 || height <= band * 3) return
        // side 0 top, 1 bottom (indexed by x); 2 left, 3 right (indexed by y). Depth grows inward from that edge.
        fun at(side: Int, position: Int, depth: Int) = when (side) {
            0 -> pixels[depth * width + position]
            1 -> pixels[(height - 1 - depth) * width + position]
            2 -> pixels[position * width + depth]
            else -> pixels[position * width + width - 1 - depth]
        }
        val window = maxOf(7, band / 2) or 1
        val references = Array(4) { side ->
            val length = if (side < 2) width else height
            val raw = Array(3) { IntArray(length) }
            for (position in 0 until length) for ((channel, shift) in intArrayOf(16, 8, 0).withIndex()) {
                raw[channel][position] = (band until band + 4).sumOf { (at(side, position, it) shr shift) and 0xFF } / 4
            }
            // Corners: sample only beside the page, never inside the neighbouring side's gap.
            IntArray(length) { index ->
                val position = index.coerceIn(band, length - 1 - band)
                val from = maxOf(band, position - window / 2)
                val to = minOf(length - band, position + window / 2 + 1)
                val (r, g, b) = raw.map { it.copyOfRange(from, to).sorted().let { values -> values[values.size / 2] } }
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        fun reference(x: Int, y: Int): Int = when (minOf(y, height - 1 - y, x, width - 1 - x)) {
            y -> references[0][x]
            height - 1 - y -> references[1][x]
            x -> references[2][y]
            else -> references[3][y]
        }
        fun differs(pixel: Int, paper: Int) = intArrayOf(16, 8, 0).any { shift -> abs(((pixel shr shift) and 0xFF) - ((paper shr shift) and 0xFF)) > tolerance }
        fun inBand(x: Int, y: Int) = x < band || y < band || x >= width - band || y >= height - band

        val filled = BooleanArray(width * height)
        val queue = ArrayDeque<Int>()
        fun visit(x: Int, y: Int) {
            val index = y * width + x
            if (!filled[index] && inBand(x, y) && differs(pixels[index], reference(x, y))) { filled[index] = true; queue.addLast(index) }
        }
        for (x in 0 until width) { visit(x, 0); visit(x, height - 1) }
        for (y in 0 until height) { visit(0, y); visit(width - 1, y) }
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val x = index % width
            val y = index / width
            if (x > 0) visit(x - 1, y)
            if (x < width - 1) visit(x + 1, y)
            if (y > 0) visit(x, y - 1)
            if (y < height - 1) visit(x, y + 1)
        }
        // Grow a little to swallow the blended fringe and shadow line where the paper meets the table.
        repeat(maxOf(2, band / 10)) {
            val grown = filled.copyOf()
            for (y in 1 until height - 1) for (x in 1 until width - 1) {
                val index = y * width + x
                if (!filled[index] && inBand(x, y) && (filled[index - 1] || filled[index + 1] || filled[index - width] || filled[index + width])) grown[index] = true
            }
            grown.copyInto(filled)
        }
        for (index in filled.indices) if (filled[index]) pixels[index] = reference(index % width, index / width)
    }

    private fun distance(first: ScanPoint, second: ScanPoint): Float = kotlin.math.hypot(first.x - second.x, first.y - second.y)

    private data class Candidate(val quad: DocumentQuad, val score: Float)
}
