package com.kalotrapezis.drive

import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile

/**
 * A sheet hidden or shown again (Excel's Hide and Unhide on a sheet's tab): Excel's state="hidden" on the workbook's sheet
 * (the open one moved off it), LibreOffice's table:display in the sheet's table style. A change to the file.
 */
internal object SheetVisibility {
    fun xlsx(source: File, target: File, index: Int, hidden: Boolean) {
        ZipFile(source).use { zip ->
            var x = zip.getEntry("xl/workbook.xml")?.let { e -> zip.getInputStream(e).use { String(it.readBytes(), Charsets.UTF_8) } } ?: error("Not a workbook.")
            val sheets = Regex("""<sheet\b[^>]*/>""").findAll(x).toList()
            val m = sheets.getOrNull(index) ?: error("No sheet ${index + 1}.")
            val bare = m.value.replace(Regex("""\sstate="[^"]*""""), "")
            x = x.replaceRange(m.range, if (hidden) bare.replaceFirst("<sheet ", """<sheet state="hidden" """) else bare)
            // Excel opens on the active tab: not a hidden one
            if (hidden) { val visible = Regex("""<sheet\b[^>]*/>""").findAll(x).withIndex().firstOrNull { (_, s) -> "state=" !in s.value }?.index ?: 0
                x = x.replace(Regex("""(<workbookView\b[^>]*?)\s(activeTab|firstSheet)="\d+""""), "$1").replace(Regex("""(<workbookView\b[^>]*?)\s(activeTab|firstSheet)="\d+""""), "$1")
                    .replace(Regex("""<workbookView\b"""), """<workbookView activeTab="$visible"""") }
            SheetSave.rezip(zip, target, mapOf("xl/workbook.xml" to x.toByteArray()), null)
        }
    }

    fun ods(source: File, target: File, index: Int, hidden: Boolean) {
        ZipFile(source).use { zip ->
            val doc = OfficeFormats.parse(zip.getInputStream(zip.getEntry("content.xml") ?: error("Not a spreadsheet.")).use { it.readBytes() })
            val S = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
            val table = doc.getElementsByTagNameNS(SheetMerge.T, "table").let { l -> (0 until l.length).map { l.item(it) as Element } }
                .filter { t -> generateSequence(t.parentNode) { it.parentNode }.none { it is Element && it.localName == "table" } }.getOrNull(index) ?: error("No sheet ${index + 1}.")
            val autos = doc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:office:1.0", "automatic-styles").item(0) as? Element ?: error("No styles.")
            val styles = (0 until autos.childNodes.length).mapNotNull { autos.childNodes.item(it) as? Element }
            // the table's own style copied (others may share it), its display set
            val old = table.getAttributeNS(SheetMerge.T, "style-name").takeIf { it.isNotEmpty() }?.let { n -> styles.firstOrNull { it.getAttributeNS(S, "name") == n && it.getAttributeNS(S, "family") == "table" } }
            val st = (old?.cloneNode(true) as Element?) ?: doc.createElementNS(S, "style:style").also { it.setAttributeNS(S, "style:family", "table") }
            val name = generateSequence(1) { it + 1 }.map { "taT$it" }.first { n -> styles.none { it.getAttributeNS(S, "name") == n } }
            st.setAttributeNS(S, "style:name", name)
            val pr = (0 until st.childNodes.length).mapNotNull { st.childNodes.item(it) as? Element }.firstOrNull { it.localName == "table-properties" }
                ?: doc.createElementNS(S, "style:table-properties").also { st.appendChild(it) }
            pr.setAttributeNS(SheetMerge.T, "table:display", if (hidden) "false" else "true")
            autos.appendChild(st); table.setAttributeNS(SheetMerge.T, "table:style-name", name)
            SheetSave.rezip(zip, target, mapOf("content.xml" to SlidesEdit.domXml(doc)), "mimetype")
        }
    }
}
