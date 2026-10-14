package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApaPaperTest {
    @Test fun `a level 4 heading of its own runs into the paragraph after it, bold and ending in a period`() {
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Sleep and Memory")), heading = 4), DocPara(listOf(DocRun("Students who slept more remembered more."))), DocPara(listOf(DocRun("Next.")))))
        val d = ApaPaper.runIn(doc, 0, 4)!!
        assertEquals(2, d.paras.size)
        val p = d.paras[0]
        assertEquals("Sleep and Memory. Students who slept more remembered more.", p.text)
        assertEquals(0, p.heading)
        assertEquals(listOf("Sleep and Memory." to true, " Students who slept more remembered more." to false), p.runs.map { it.text to it.bold })
        assertTrue(p.runs.none { it.italic })
    }

    @Test fun `level 5 is bold italic - a paragraph's first sentence, or the words chosen, become the heading`() {
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Effects on Grades. The grades rose by a point."))), DocPara(listOf(DocRun("Short Title Then text")))))
        val d = ApaPaper.runIn(doc, 0, 5)!!
        val head = d.paras[0].runs.first()
        assertEquals("Effects on Grades.", head.text); assertTrue(head.bold && head.italic)
        val e = ApaPaper.runIn(doc, 1, 4, length = 11)!!
        assertEquals("Short Title. Then text", e.paras[1].text)
        assertNull(ApaPaper.runIn(OfficeDoc(listOf(DocPara(emptyList()))), 0, 4))
    }

    @Test fun `APA turns every heading 4 and 5 into run-in ones, and an abstract page reads as APA's`() {
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Method")), heading = 1), DocPara(listOf(DocRun("Sample")), heading = 4), DocPara(listOf(DocRun("We asked 40."))),
            DocPara(listOf(DocRun("Age")), heading = 5), DocPara(listOf(DocRun("All were adults.")))))
        val d = ApaPaper.runInAll(doc)
        assertEquals(listOf("Method", "Sample. We asked 40.", "Age. All were adults."), d.paras.map { it.text })
        val page = ApaPaper.abstractPage(ApaAbstract("We studied sleep.\nIt helps.", "sleep, memory."), OfficeKind.Docx, "Title")
        assertEquals(listOf("Abstract", "We studied sleep. It helps.", "Keywords: sleep, memory"), page.map { it.text })
        assertEquals(DocAlign.Center, page[0].align)
        assertTrue(page[2].runs[0].italic && !page[2].runs[1].italic)
        // Written and read back: the abstract's label starts a page.
        val back = OfficeFormats.read(OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("Title")))) + page), OfficeKind.Docx), OfficeKind.Docx)
        assertTrue(back.paras[1].keep?.parts.orEmpty().any { it.first == "pageBreakBefore" })
    }
}
