package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentControlTest {
    private fun roundTrip(doc: OfficeDoc) = OfficeFormats.read(OfficeFormats.write(doc, OfficeKind.Docx), OfficeKind.Docx)

    @Test fun `each new content control is written as Word's and read back as the form it is`() {
        val kinds = mapOf(Forms.New.PlainText to Forms.Kind.Text, Forms.New.RichText to Forms.Kind.Text, Forms.New.CheckBox to Forms.Kind.Check,
            Forms.New.DropDown to Forms.Kind.List, Forms.New.ComboBox to Forms.Kind.List, Forms.New.Date to Forms.Kind.Date)
        for ((new, kind) in kinds) {
            val (pr, shown) = Forms.newControl(new, "Field \"1\"", listOf("Yes", "No", " ", "Yes"), "d MMMM yyyy")
            val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Answer: "), DocRun(shown, control = pr, color = 0x808080), DocRun(" end")))))
            val back = roundTrip(doc)
            assertTrue(back.blocking.toString(), back.blocking.isEmpty())
            val run = back.paras[0].runs.single { it.control != null }
            assertEquals(new.label, shown, run.text)
            assertEquals(new.label, kind, Forms.kind(run.control!!))
            assertEquals(new.label, "Field \"1\"", Forms.name(run.control!!))
            if (kind == Forms.Kind.List) assertEquals(listOf("Yes", "No"), Forms.items(run.control!!))
        }
    }

    @Test fun `filling a new one takes its placeholder away, a tick ticks it`() {
        val (pr, shown) = Forms.newControl(Forms.New.PlainText, "Name")
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun(shown, control = pr, color = 0x808080)))))
        val filled = Forms.fill(doc, pr, pr, "Teo")
        val run = roundTrip(filled).paras[0].runs.single { it.control != null }
        assertEquals("Teo", run.text); assertEquals(null, run.color); assertFalse("showingPlcHdr" in run.control!!)
        val (box, empty) = Forms.newControl(Forms.New.CheckBox, "")
        assertEquals("☐", empty); assertFalse(Forms.checked(box))
        val ticked = Forms.checkedTo(box, true)
        assertTrue(Forms.checked(ticked)); assertEquals("☒", Forms.box(ticked, true))
    }
}
