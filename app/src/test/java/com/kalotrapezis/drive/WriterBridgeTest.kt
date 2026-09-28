package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class WriterBridgeTest {
    @Test fun `superscript and subscript keep their sizes through the editor`() {
        // The document's own size, a size set on purpose (even the normal one), both scripts.
        val paras = listOf(DocPara(listOf(DocRun("x"), DocRun("2", script = 1), DocRun(" H"), DocRun("2", script = -1, sizePt = 11f),
            DocRun("O"), DocRun("n", script = 1, sizePt = 8.5f), DocRun(" big", sizePt = 14f))))
        val doc = OfficeDoc(paras, basePt = 11f)
        assertEquals(paras, WriterBridge.fromEditor(WriterBridge.toEditor(doc), doc.basePt))
    }

    @Test fun `a line break inside a paragraph is a new line in the editor and one paragraph again in the file`() {
        val br = LINE_BREAK.toString()
        val paras = listOf(
            DocPara(listOf(DocRun("Line one"), DocRun("${br}Line two", bold = true), DocRun(" end"))),
            DocPara(listOf(DocRun("item${br}${br}after an empty line")), list = DocList(false, 0)),
            DocPara(listOf(DocRun("ends with a break$br"))),
            DocPara(listOf(DocRun("next paragraph"))),
        )
        val rich = WriterBridge.toEditor(OfficeDoc(paras, basePt = 11f))
        assertEquals(listOf(false, true, false, true, true, false, true, false), rich.blocks.map { it.isLineBreak })
        assertEquals(WriterBridge.comparable(paras), WriterBridge.comparable(WriterBridge.fromEditor(rich, 11f)))
        // A break whose look differs from the text after it is compared by where it stands, not its look.
        val odd = listOf(DocPara(listOf(DocRun("a"), DocRun(br, italic = true), DocRun("b"))))
        assertEquals(WriterBridge.comparable(odd), WriterBridge.comparable(WriterBridge.fromEditor(WriterBridge.toEditor(OfficeDoc(odd, basePt = 11f)), 11f)))
    }

    @Test fun `a picture goes where the mark was typed, and nothing else moves`() {
        val img = DocImage("k", "word/media/a.png", 10f, 10f)
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("ab"))), DocPara(listOf(DocRun("cd$PICTURE_SPOT", bold = true), DocRun("ef"))), DocPara(listOf(DocRun("gh")))))
        val put = WriterBridge.putPicture(doc, img)
        assertEquals(listOf("ab", "cd${OBJECT_MARK}ef", "gh"), put.paras.map { it.text })
        assertEquals(img, put.paras[1].runs[1].image)
        assertEquals(DocRun("ef"), put.paras[1].runs[2])
    }
}
