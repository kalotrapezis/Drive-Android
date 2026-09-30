package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackChangesTest {
    /** A Word file whose body is [body] (as a professor's corrections come: Track Changes on in Word). */
    private fun docx(body: String): ByteArray {
        val parts = OfficeFormats.unzip(OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("x"))))), OfficeKind.Docx))
        val xml = String(parts.getValue("word/document.xml"))
        val start = xml.indexOf("<w:body>") + "<w:body>".length
        parts["word/document.xml"] = (xml.substring(0, start) + body + xml.substring(xml.indexOf("<w:sectPr", start))).toByteArray()
        return OfficeFormats.zip(parts)
    }

    private val by = """w:author="Prof. K" w:date="2026-09-30T10:00:00Z""""
    private val corrected = docx(
        """<w:p><w:r><w:t xml:space="preserve">The cat </w:t></w:r><w:del w:id="1" $by><w:r><w:delText>sat</w:delText></w:r></w:del>""" +
            """<w:ins w:id="2" $by><w:r><w:t>slept</w:t></w:r></w:ins><w:r><w:rPr><w:b/><w:rPrChange w:id="3" $by><w:rPr/></w:rPrChange></w:rPr><w:t xml:space="preserve"> here</w:t></w:r></w:p>""" +
            """<w:p><w:pPr><w:jc w:val="center"/><w:pPrChange w:id="4" $by><w:pPr><w:ind w:left="720"/></w:pPr></w:pPrChange><w:rPr><w:del w:id="5" $by/></w:rPr></w:pPr><w:r><w:t>First</w:t></w:r></w:p>""" +
            """<w:p><w:r><w:t>second</w:t></w:r><w:moveFromRangeStart w:id="6" w:name="m"/><w:moveFrom w:id="7" $by><w:r><w:t xml:space="preserve"> moved</w:t></w:r></w:moveFrom><w:moveFromRangeEnd w:id="6"/></w:p>""")

    @Test fun `a corrected file opens with its changes, and saving over it is allowed`() {
        val doc = OfficeFormats.read(corrected, OfficeKind.Docx)
        assertEquals(emptySet<String>(), doc.blocking)
        assertEquals("The cat satslept here", doc.paras[0].text)
        val kinds = TrackChanges.list(doc).map { it.kind to doc.paras[it.para].text.substring(it.from, it.to) }
        assertEquals(listOf(TrackChanges.Kind.Deleted to "sat", TrackChanges.Kind.Inserted to "slept", TrackChanges.Kind.Formatted to " here",
            TrackChanges.Kind.ParagraphFormatted to "First", TrackChanges.Kind.ParagraphDeleted to "", TrackChanges.Kind.Deleted to " moved"), kinds)
        assertEquals("Prof. K", TrackChanges.list(doc).first().author)
        // Written and read again: the same changes, where they were.
        val again = OfficeFormats.read(OfficeFormats.write(doc, OfficeKind.Docx, corrected), OfficeKind.Docx)
        assertEquals(doc.paras, again.paras)
        val xml = String(OfficeFormats.unzip(OfficeFormats.write(doc, OfficeKind.Docx, corrected)).getValue("word/document.xml"))
        assertTrue("<w:delText xml:space=\"preserve\">sat</w:delText>" in xml)
        assertTrue(Regex("""<w:rPrChange [^>]*><w:rPr/></w:rPrChange></w:rPr>""").containsMatchIn(xml))
    }

    @Test fun `accept all and reject all`() {
        val doc = OfficeFormats.read(corrected, OfficeKind.Docx)
        val yes = TrackChanges.all(doc, true)
        assertEquals(listOf("The cat slept here", "Firstsecond"), yes.paras.map { it.text })
        assertTrue(yes.paras[0].runs.last().bold)
        assertFalse(TrackChanges.any(yes))
        val no = TrackChanges.all(doc, false)
        assertEquals(listOf("The cat sat here", "First", "second moved"), no.paras.map { it.text })
        assertFalse(no.paras[0].runs.any { it.bold })
        assertEquals(DocAlign.Start, no.paras[1].align)
        assertTrue(no.paras[1].keep!!.parts.any { it.second == "<w:ind w:left=\"720\"/>" })
        assertFalse(TrackChanges.any(no))
        // Both still Word files that read back as they are.
        for (d in listOf(yes, no)) assertEquals(d.paras.map { it.text }, OfficeFormats.read(OfficeFormats.write(d, OfficeKind.Docx, corrected), OfficeKind.Docx).paras.map { it.text })
    }

    @Test fun `one change at a time`() {
        val doc = OfficeFormats.read(corrected, OfficeKind.Docx)
        val changes = TrackChanges.list(doc)
        val rejectedInsert = TrackChanges.decide(doc, listOf(changes[1]), false)
        assertEquals("The cat sat here", rejectedInsert.paras[0].text)
        assertEquals(5, TrackChanges.list(rejectedInsert).size) // the deletion is still there
        val acceptedJoin = TrackChanges.decide(doc, listOf(changes[4]), true)
        assertEquals(listOf("The cat satslept here", "Firstsecond moved"), acceptedJoin.paras.map { it.text })
    }

    @Test fun `changes are kept through typing elsewhere`() {
        val doc = OfficeFormats.read(corrected, OfficeKind.Docx)
        val typed = doc.paras.map { p -> p.copy(runs = listOf(DocRun(p.text.replace("The cat", "A cat")))) }
        val carried = OfficeFormats.carryFonts(doc.paras, typed)
        assertEquals(listOf(true to "sat", false to "slept"), carried[0].runs.mapNotNull { r -> r.change?.let { it.deleted to r.text } })
    }

    // ——— Recording (Track changes on)
    private val me = DocChange(false, "Teo", "2026-09-30T18:00:00Z")
    private fun p(vararg runs: DocRun) = DocPara(runs.toList())
    private fun shown(ps: List<DocPara>) = ps.joinToString(" ¶ ") { q -> q.runs.joinToString("") { r -> when (r.change?.deleted) { true -> "[-${r.text}]"; false -> "[+${r.text}]"; null -> r.text } } }

    @Test fun `typing is recorded as an insertion`() {
        val r = TrackChanges.record(listOf(p(DocRun("The cat"))), listOf(p(DocRun("The big cat"))), me, 8)
        assertEquals("The [+big ]cat", shown(r.paras))
        assertFalse(r.reshow)
    }

    @Test fun `backspace strikes letters through and walks on past them`() {
        var doc = listOf(p(DocRun("The cat")))
        // Backspace after "c": it comes back deleted, the cursor before it.
        var r = TrackChanges.record(doc, listOf(p(DocRun("The at"))), me, 4)
        assertEquals("The [-c]at", shown(r.paras)); assertEquals(4, r.cursor); assertTrue(r.reshow)
        doc = r.paras
        // Backspace again: the space goes the same way, the cursor before both.
        r = TrackChanges.record(doc, listOf(p(DocRun("The"), DocRun("c", change = me.copy(deleted = true)), DocRun("at"))), me, 3)
        assertEquals("The[- c]at", shown(r.paras)); assertEquals(3, r.cursor)
        // Deleting a letter already deleted only moves the cursor over it.
        r = TrackChanges.record(listOf(p(DocRun("The "), DocRun("c", change = me.copy(deleted = true)), DocRun("at"))), listOf(p(DocRun("The at"))), me, 4)
        assertEquals("The [-c]at", shown(r.paras)); assertEquals(4, r.cursor)
    }

    @Test fun `backspace over a doubled letter strikes the right one`() {
        // "well" with its last l already deleted, the cursor before that l: Backspace takes the first l.
        val before = listOf(p(DocRun("wel"), DocRun("l", change = me.copy(deleted = true)), DocRun(".")))
        val typed = listOf(p(DocRun("we"), DocRun("l"), DocRun("."))) // what the editor holds now (its carried looks may be wrong)
        val r = TrackChanges.record(before, typed, me, 2)
        assertEquals("we[-ll].", shown(r.paras)); assertEquals(2, r.cursor)
    }

    @Test fun `one's own insertion deleted just goes`() {
        val r = TrackChanges.record(listOf(p(DocRun("The "), DocRun("big ", change = me), DocRun("cat"))), listOf(p(DocRun("The "), DocRun("ig ", change = me), DocRun("cat"))), me, 4)
        assertEquals("The [+ig ]cat", shown(r.paras))
    }

    @Test fun `enter and a join are paragraph marks, kept through a Word file`() {
        val split = TrackChanges.record(listOf(p(DocRun("abcdef"))), listOf(p(DocRun("abc")), p(DocRun("def"))), me, 4)
        assertEquals(TrackChanges.Kind.ParagraphInserted, TrackChanges.list(OfficeDoc(split.paras)).single().kind)
        val join = TrackChanges.record(listOf(p(DocRun("abc")), p(DocRun("def"))), listOf(p(DocRun("abcdef"))), me, 3)
        assertEquals("abc ¶ def", shown(join.paras)); assertEquals(3, join.cursor)
        val doc = OfficeFormats.read(OfficeFormats.write(OfficeDoc(join.paras), OfficeKind.Docx), OfficeKind.Docx)
        assertEquals(listOf(TrackChanges.Kind.ParagraphDeleted), TrackChanges.list(doc).map { it.kind })
        assertEquals(listOf("abcdef"), TrackChanges.all(doc, true).paras.map { it.text })
        assertEquals(listOf("abc", "def"), TrackChanges.all(doc, false).paras.map { it.text })
    }

    @Test fun `typed over a selection, the old then the new`() {
        // "bad" chosen, "g" typed over it; then "ood" typed after it (one keystroke at a time, as the editor sends them).
        var r = TrackChanges.record(listOf(p(DocRun("It was bad."))), listOf(p(DocRun("It was g."))), me, 8)
        assertEquals("It was [-bad][+g].", shown(r.paras)); assertEquals(11, r.cursor)
        r = TrackChanges.record(r.paras, listOf(p(DocRun("It was "), DocRun("bad", change = me.copy(deleted = true)), DocRun("go", change = me), DocRun("."))), me, 12)
        assertEquals("It was [-bad][+go].", shown(r.paras)); assertEquals(12, r.cursor)
    }
}
