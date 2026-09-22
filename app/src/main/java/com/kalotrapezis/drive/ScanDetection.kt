package com.kalotrapezis.drive

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.opencv.android.OpenCVLoader
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
        val threshold = max(170, percentile(histogram, bytes.size, 84))
        val mask = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        return try {
            Imgproc.threshold(gray, mask, threshold.toDouble(), 255.0, Imgproc.THRESH_BINARY)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            bestQuad(mask)
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
            bestQuad(edges)
        } finally {
            kernel.release()
            edges.release()
            blurred.release()
        }
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

    private fun bestQuad(mask: Mat): DocumentQuad? {
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        return contours.mapNotNull { contour ->
            try {
                quadCorners(contour)?.let { candidate(it, mask.width(), mask.height()) }
            } finally {
                contour.release()
            }
        }.maxByOrNull { it.score }?.quad
    }

    /** Crumpled or wavy edges yield many vertices; simplify the convex hull until only the four page corners remain. */
    private fun quadCorners(contour: MatOfPoint): Array<Point>? {
        val points = contour.toArray()
        if (points.size < 4) return null
        val hullIndices = org.opencv.core.MatOfInt()
        Imgproc.convexHull(contour, hullIndices)
        val hull = MatOfPoint2f(*hullIndices.toArray().map { points[it] }.toTypedArray())
        hullIndices.release()
        return try {
            val perimeter = Imgproc.arcLength(hull, true)
            for (epsilon in listOf(0.02, 0.03, 0.045, 0.06, 0.08, 0.1)) {
                val approximation = MatOfPoint2f()
                Imgproc.approxPolyDP(hull, approximation, perimeter * epsilon, true)
                val corners = approximation.toArray()
                approximation.release()
                if (corners.size == 4) return corners
                if (corners.size < 4) return null
            }
            null
        } finally {
            hull.release()
        }
    }

    private fun candidate(points: Array<Point>, width: Int, height: Int): Candidate? {
        val pointContour = MatOfPoint(*points)
        val area = try { kotlin.math.abs(Imgproc.contourArea(pointContour)).toFloat() } finally { pointContour.release() }
        val coverage = area / (width * height)
        if (coverage !in 0.025f..0.9f) return null
        val ordered = orderCorners(points.map { ScanPoint((it.x / width).toFloat(), (it.y / height).toFloat()) }) ?: return null
        val quad = DocumentQuad(ordered[0], ordered[1], ordered[2], ordered[3])
        val averageWidth = (distance(quad.topLeft, quad.topRight) + distance(quad.bottomLeft, quad.bottomRight)) / 2f
        val averageHeight = (distance(quad.topLeft, quad.bottomLeft) + distance(quad.topRight, quad.bottomRight)) / 2f
        val aspect = max(averageWidth, averageHeight) / min(averageWidth, averageHeight)
        if (aspect > 12f) return null
        val centerX = quad.points.sumOf { it.x.toDouble() }.toFloat() / 4f
        val centerY = quad.points.sumOf { it.y.toDouble() }.toFloat() / 4f
        val centered = (1f - (abs(centerX - 0.5f) * 1.5f + abs(centerY - 0.5f) * 0.3f)).coerceAtLeast(0f)
        val nearFrame = quad.points.any { it.x < 0.015f || it.x > 0.985f || it.y < 0.015f || it.y > 0.985f }
        return Candidate(quad, coverage * centered * if (nearFrame) 0.35f else 1f)
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
