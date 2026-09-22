package com.kalotrapezis.drive

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import java.io.File

object ScanFiles {
    private const val FOLDER = "Documents/Scanned Documents"

    fun outputName(value: String): String {
        val stem = value.trim().removeSuffix(".pdf").trim()
        require(stem.isNotEmpty() && stem.length <= 120 && '/' !in stem && '\\' !in stem && stem !in setOf(".", "..")) { "Enter a valid file name." }
        return "$stem.pdf"
    }

    fun nextAvailable(folder: File, outputName: String): File {
        val stem = outputName.removeSuffix(".pdf")
        return generateSequence(1) { it + 1 }
            .map { index -> File(folder, if (index == 1) outputName else "$stem ($index).pdf") }
            .first { !it.exists() }
    }

    /** Writes [pageCount] pages rendered one at a time (bounded memory) as A4-width PDF pages. */
    fun savePdf(root: File, name: String, pageCount: Int, render: (Int) -> Bitmap): String {
        require(pageCount > 0) { "There are no pages to save." }
        val folder = File(root, FOLDER).canonicalFile
        require(DriveRules.inside(root, folder) && (folder.exists() || folder.mkdirs())) { "Could not create Scans." }
        val target = nextAvailable(folder, outputName(name)).canonicalFile
        require(DriveRules.inside(root, target) && target.createNewFile()) { "Could not create the scan file." }
        val document = PdfDocument()
        try {
            for (index in 0 until pageCount) {
                val page = render(index)
                val width = A4_WIDTH_POINTS
                val height = (A4_WIDTH_POINTS * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                pdfPage.canvas.drawBitmap(page, null, android.graphics.Rect(0, 0, width, height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                document.finishPage(pdfPage)
                page.recycle()
            }
            // A failed write keeps the partial file for recovery instead of deleting user-visible data.
            target.outputStream().use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }
        return DriveRules.relative(root, target)
    }

    private const val A4_WIDTH_POINTS = 595
}
