package com.kalotrapezis.drive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesInDocsTest {
    private fun note(end: Boolean, text: String, kind: OfficeKind) = DocNote(end, listOf(DocPara(listOf(DocRun(text)),
        keep = DocKeep(if (kind == OfficeKind.Docx) (if (end) "EndnoteText" else "FootnoteText") else (if (end) "Endnote" else "Footnote")))))

    private fun doc(kind: OfficeKind) = OfficeFormats.numberNotes(OfficeDoc(listOf(
        DocPara(listOf(DocRun("First claim"), DocRun("x", note = note(false, "Smith, 2019, p. 4.", kind)), DocRun(" and more."))),
        DocPara(listOf(DocRun("Second"), DocRun("x", note = note(false, "See also Jones.", kind)), DocRun(" and an end"), DocRun("x", note = note(true, "An endnote.", kind)))),
    )))

    private fun roundTrip(kind: OfficeKind) {
        val d = doc(kind)
        assertEquals(listOf("First claim1 and more.", "Second2 and an endi"), d.paras.map { it.text })
        val bytes = OfficeFormats.write(d, kind)
        val back = OfficeFormats.read(bytes, kind)
        assertEquals(kind.name, emptySet<String>(), back.blocking)
        assertEquals(d.paras.map { it.text }, back.paras.map { it.text })
        assertEquals(listOf("Smith, 2019, p. 4.", "See also Jones.", "An endnote."), OfficeFormats.notesOf(back).map { it.paras.single().text })
        // Written into itself again: nothing drifts (no space gained before a note's text, no note lost).
        val again = OfficeFormats.read(OfficeFormats.write(back, kind, bytes), kind)
        assertEquals(back.paras, again.paras)
    }

    @Test fun `footnotes and endnotes in a Word file`() = roundTrip(OfficeKind.Docx)
    @Test fun `footnotes and endnotes in an OpenDocument file`() = roundTrip(OfficeKind.Odt)

    @Test fun `the test suite's academic file keeps its notes`() {
        val f = File(System.getProperty("user.home"), "Έγγραφα/Claude/Projects/Defaulty/Word App Test Suite/04_Academic_References.docx").takeIf { it.isFile } ?: return
        val doc = OfficeFormats.read(f.readBytes(), OfficeKind.Docx)
        assertTrue("footnotes" !in doc.unsupported)
        assertEquals(listOf("A real footnote stored in its own Word part.", "A real endnote stored in its own Word part."), OfficeFormats.notesOf(doc).map { it.paras.single().text })
    }
}
