package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentThreadsTest {
    private fun thread(kind: OfficeKind) {
        var doc = OfficeDoc(listOf(DocPara(listOf(DocRun("The "), DocRun("student", comments = listOf(if (kind == OfficeKind.Docx) "0" else "c1")), DocRun(" wrote.")))),
            comments = listOf(DocComment(if (kind == OfficeKind.Docx) "0" else "c1", "Prof K", "2026-09-30T10:00:00Z", "Which student?")))
        val parent = doc.comments.single().id
        doc = OfficeFormats.withReply(doc, parent, DocComment(OfficeFormats.newCommentId(doc, kind), "Teo", "2026-09-30T11:00:00Z", "The one from class A."))
        doc = OfficeFormats.withDone(doc, parent, true)
        val bytes = OfficeFormats.write(doc, kind)
        val back = OfficeFormats.read(bytes, kind)
        assertEquals(kind.name, emptySet<String>(), back.blocking)
        val (p, r) = back.comments.partition { it.parent == null }
        assertEquals("Which student?", p.single().text); assertTrue(p.single().done)
        assertEquals("The one from class A.", r.single().text); assertEquals(p.single().id, r.single().parent)
        // Written again, the same; reopened, the thread is open; the parent deleted, its reply goes too.
        val again = OfficeFormats.read(OfficeFormats.write(back, kind, bytes), kind)
        assertEquals(back.comments.map { Triple(it.text, it.parent != null, it.done) }, again.comments.map { Triple(it.text, it.parent != null, it.done) })
        assertFalse(OfficeFormats.read(OfficeFormats.write(OfficeFormats.withDone(again, p.single().id, false), kind, bytes), kind).comments.any { it.done })
        assertEquals(emptyList<DocComment>(), OfficeFormats.withoutComment(again, p.single().id).liveComments)
    }
    @Test fun `a reply and a resolved comment in a Word file`() = thread(OfficeKind.Docx)
    @Test fun `a reply and a resolved comment in an OpenDocument file`() = thread(OfficeKind.Odt)
}
