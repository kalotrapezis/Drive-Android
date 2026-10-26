package com.kalotrapezis.drive

import androidx.compose.ui.geometry.Offset

/** The tests' way to the text under a point (the app chooses objects by id: PdfObjects.kt). */
internal fun PdfDoc.typedAt(i: Int, at: Offset): Pair<android.graphics.RectF, PdfText>? = objectAt(i, at)?.let { o -> o.text?.let { o.rect to it } }
internal fun PdfDoc.restack(i: Int, at: Offset, to: Int): Boolean = objectAt(i, at)?.let { restackObject(i, it.id, to) } ?: false
