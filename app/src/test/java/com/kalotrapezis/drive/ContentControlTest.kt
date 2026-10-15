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

    @Test fun `LibreOffice's content controls are read, filled and written back as its own`() {
        val bytes = javaClass.classLoader!!.getResource("controls.odt").readBytes() // LibreOffice 26.2's conversion of Word's six kinds
        val doc = OfficeFormats.read(bytes, OfficeKind.Odt)
        assertTrue(doc.blocking.toString(), doc.blocking.isEmpty())
        val controls = doc.paras.flatMap { p -> p.runs.mapNotNull { r -> r.control?.let { r.text to it } } }
        assertEquals(listOf("Click or tap here to enter text.", "☒", "Pear", "Two", "15/10/2026", "rich words"), controls.map { it.first })
        val (name, tick, fruit, combo, date, rich) = controls.map { it.second }.let { listOf(it[0], it[1], it[2], it[3], it[4], it[5]) }.let { Six(it) }
        assertEquals("Name", Forms.name(name)); assertTrue("showingPlcHdr" in name)
        assertTrue(Forms.checked(tick)); assertEquals(Forms.Kind.List, Forms.kind(fruit)); assertEquals(listOf("Apple", "Pear"), Forms.items(fruit))
        assertTrue("comboBox" in combo); assertEquals(java.time.LocalDate.of(2026, 10, 15), Forms.date(date)); assertEquals(Forms.Kind.Text, Forms.kind(rich))
        // Filled, then saved over the .odt: LibreOffice's own controls again, with what was put in.
        var filled = Forms.fill(doc, name, name, "Teo")
        filled = Forms.fill(filled, fruit, fruit, "Apple")
        filled = Forms.fill(filled, tick, Forms.checkedTo(tick, false), Forms.box(tick, false))
        val written = OfficeFormats.write(filled, OfficeKind.Odt, bytes)
        val xml = String(OfficeFormats.unzip(written).getValue("content.xml"), Charsets.UTF_8)
        assertTrue(xml, """<loext:content-control loext:plain-text="true" loext:alias="Name" loext:tag="Name" loext:id="11">Teo</loext:content-control>""" in xml)
        assertTrue(xml, """loext:checkbox="true" loext:checked="false" loext:checked-state="☒" loext:unchecked-state="☐" loext:id="12">☐</loext:content-control>""" in xml)
        assertTrue(xml, """<loext:content-control loext:dropdown="true" loext:alias="Fruit" loext:id="13"><loext:list-item loext:display-text="Apple" loext:value="Apple"/><loext:list-item loext:display-text="Pear" loext:value="Pear"/>Apple</loext:content-control>""" in xml)
        assertTrue(xml, """loext:date="true" loext:date-format="d/M/yyyy" loext:date-rfc-language-tag="en-GB" loext:current-date="2026-10-15T00:00:00Z" loext:id="14">15/10/2026<""" in xml)
        val again = OfficeFormats.read(written, OfficeKind.Odt)
        assertEquals(6, again.paras.sumOf { p -> p.runs.count { it.control != null } })
        assertTrue(again.blocking.isEmpty())
    }

    @Test fun `a new control in an odt is LibreOffice's`() {
        val (pr, shown) = Forms.newControl(Forms.New.DropDown, "Answer", listOf("Yes", "No"))
        val doc = OfficeDoc(listOf(DocPara(listOf(DocRun("Q: "), DocRun(shown, control = pr, color = 0x808080)))))
        val back = OfficeFormats.read(OfficeFormats.write(doc, OfficeKind.Odt), OfficeKind.Odt)
        val run = back.paras[0].runs.single { it.control != null }
        assertEquals(shown, run.text); assertEquals(listOf("Yes", "No"), Forms.items(run.control!!)); assertEquals("Answer", Forms.name(run.control!!))
    }

    private class Six(l: List<String>) { val a = l; operator fun component1() = a[0]; operator fun component2() = a[1]; operator fun component3() = a[2]
        operator fun component4() = a[3]; operator fun component5() = a[4]; operator fun component6() = a[5] }
}
