package com.kalotrapezis.drive

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * A document, spreadsheet or presentation another app opens in Tetra (Open with › Tetra Office, asked 16 Oct): the file
 * itself when it is on the phone's storage (edited and saved where it is), else a copy in Documents/From other apps
 * (an email's attachment, a cloud app's file), which Tetra then keeps like any other.
 */
internal object OfficeOpen {
    /** Words', Tables' and Slides' kinds, by name. */
    fun isOffice(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("docx", "odt", "doc", "rtf") || Sheets.isSheet(name) || Slides.isSlides(name)

    /** An address's file on the phone's storage, when it names one: file://, a Files app's (primary:…), Downloads' raw: one. */
    fun localPath(uri: Uri, storage: File): File? = when {
        uri.scheme == "file" -> uri.path?.let(::File)
        uri.authority == "com.android.externalstorage.documents" -> documentId(uri)?.let { id ->
            if (id.startsWith("primary:")) File(storage, id.removePrefix("primary:")) else null
        }
        uri.authority == "com.android.providers.downloads.documents" -> documentId(uri)?.takeIf { it.startsWith("raw:") }?.let { File(it.removePrefix("raw:")) }
        else -> null
    }?.takeIf { it.isFile && it.canRead() && it.canWrite() }

    private fun documentId(uri: Uri) = uri.pathSegments.let { s -> s.indexOf("document").takeIf { it >= 0 }?.let { s.getOrNull(it + 1) } }

    /** The file to open: the phone's own, or a copy made in [into] under the name the other app gives it. */
    fun file(context: Context, uri: Uri, storage: File, into: File, type: String?): File? {
        localPath(uri, storage)?.let { return it }
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.replace(Regex("""[\\/:*?"<>|]"""), "_")?.takeIf { it.isNotBlank() } ?: ("Document." + (extensionOf(type) ?: "docx"))
        into.mkdirs()
        var target = File(into, name); var n = 2
        while (target.exists()) { target = File(into, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.', "")}"); n++ }
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        WriterLog.i("Office: a file from another app copied into ${into.name}")
        return target
    }

    /** A kind's file ending, when the other app gives no name. */
    fun extensionOf(type: String?) = when (type) {
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.oasis.opendocument.text" -> "odt"
        "application/msword" -> "doc"
        "application/rtf", "text/rtf" -> "rtf"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.oasis.opendocument.spreadsheet" -> "ods"
        "application/vnd.ms-excel" -> "xls"
        "text/csv", "text/comma-separated-values" -> "csv"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        "application/vnd.openxmlformats-officedocument.presentationml.slideshow" -> "ppsx"
        "application/vnd.oasis.opendocument.presentation" -> "odp"
        else -> null
    }
}
