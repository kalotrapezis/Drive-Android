package com.kalotrapezis.drive

import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile

/**
 * Insert › Chart in Tables (Words' chart editor, Words' [ChartWriter]): a chart put on a sheet at a cell, [cols] × [rows]
 * cells big — Excel's chart part in the sheet's drawing (a two-cell anchor), LibreOffice's chart object in the cell.
 * shortcut: the chart holds its own numbers (Excel's numLit, LibreOffice's own table), not the cells' — changing the cells
 * does not change it; point its series at the cells when charts are edited from the sheet.
 */
internal object SheetChart {
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun xlsx(source: File, target: File, sheetPart: String, row: Int, col: Int, rows: Int, cols: Int, ch: DocChart) {
        ZipFile(source).use { zip ->
            fun read(p: String) = zip.getEntry(p)?.let { e -> zip.getInputStream(e).use { String(it.readBytes(), Charsets.UTF_8) } }
            val out = LinkedHashMap<String, ByteArray?>()
            fun relsOf(p: String) = p.substringBeforeLast('/') + "/_rels/" + p.substringAfterLast('/') + ".rels"
            fun freeId(rels: String) = "rId" + ((Regex("""Id="rId(\d+)"""").findAll(rels).maxOfOrNull { it.groupValues[1].toInt() } ?: 0) + 1)
            fun emptyRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""
            var types = read("[Content_Types].xml") ?: error("Not a workbook.")
            val chartPath = generateSequence(1) { it + 1 }.map { "xl/charts/chart$it.xml" }.first { zip.getEntry(it) == null }
            out[chartPath] = ChartWriter.drawingml(ch).toByteArray()
            types = types.replace("</Types>", """<Override PartName="/$chartPath" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/></Types>""")
            var sheet = read(sheetPart) ?: error("No sheet.")
            var sheetRels = read(relsOf(sheetPart)) ?: emptyRels()
            // the sheet's drawing: its own, or a new one named in the sheet
            val own = Regex("""<drawing\b[^>]*\br:id="([^"]+)"""").find(sheet)?.groupValues?.get(1)
                ?.let { id -> Regex("""<Relationship\b[^>]*\bId="$id"[^>]*\bTarget="([^"]+)"""").find(sheetRels)?.groupValues?.get(1) ?: Regex("""<Relationship\b[^>]*\bTarget="([^"]+)"[^>]*\bId="$id"""").find(sheetRels)?.groupValues?.get(1) }
                ?.let { t -> if (t.startsWith("/")) t.removePrefix("/") else "xl/" + t.removePrefix("../") }
            val drawingPath = own ?: generateSequence(1) { it + 1 }.map { "xl/drawings/drawing$it.xml" }.first { zip.getEntry(it) == null }
            var drawing = (if (own != null) read(own) else null)
                ?: """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"></xdr:wsDr>"""
            if (own == null) {
                val id = freeId(sheetRels)
                sheetRels = sheetRels.replace("</Relationships>", """<Relationship Id="$id" Type="$REL/drawing" Target="../drawings/${drawingPath.substringAfterLast('/')}"/></Relationships>""")
                // in the schema's place: before the legacy drawings, pictures, objects, controls, tables and extensions
                val at = Regex("""<(legacyDrawing|legacyDrawingHF|drawingHF|picture|oleObjects|controls|webPublishItems|tableParts|extLst)\b""").find(sheet)?.range?.first ?: sheet.lastIndexOf("</worksheet>")
                sheet = sheet.substring(0, at) + """<drawing xmlns:r="$REL" r:id="$id"/>""" + sheet.substring(at)
                types = types.replace("</Types>", """<Override PartName="/$drawingPath" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/></Types>""")
                out[sheetPart] = sheet.toByteArray(); out[relsOf(sheetPart)] = sheetRels.toByteArray()
            }
            var drawingRels = read(relsOf(drawingPath)) ?: emptyRels()
            val chartId = freeId(drawingRels)
            drawingRels = drawingRels.replace("</Relationships>", """<Relationship Id="$chartId" Type="$REL/chart" Target="../charts/${chartPath.substringAfterLast('/')}"/></Relationships>""")
            val n = (Regex("""<xdr:cNvPr\b[^>]*\bid="(\d+)"""").findAll(drawing).maxOfOrNull { it.groupValues[1].toInt() } ?: 1) + 1
            fun mark(tag: String, r: Int, c: Int) = "<xdr:$tag><xdr:col>$c</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$r</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:$tag>"
            val anchor = """<xdr:twoCellAnchor editAs="oneCell">${mark("from", row, col)}${mark("to", row + rows, col + cols)}<xdr:graphicFrame macro=""><xdr:nvGraphicFramePr><xdr:cNvPr id="$n" name="Chart $n"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>""" +
                """<xdr:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/></xdr:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart"><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:r="$REL" r:id="$chartId"/></a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor>"""
            drawing = drawing.replace(Regex("""<xdr:wsDr\b([^>]*)/>"""), "<xdr:wsDr$1></xdr:wsDr>").replace("</xdr:wsDr>", "$anchor</xdr:wsDr>")
            out[drawingPath] = drawing.toByteArray(); out[relsOf(drawingPath)] = drawingRels.toByteArray(); out["[Content_Types].xml"] = types.toByteArray()
            SheetSave.rezip(zip, target, out, null)
        }
    }

    /** LibreOffice: sheet [index]'s cell [row], [col] holding the chart's object, [widthPt] × [heightPt]. */
    fun ods(source: File, target: File, index: Int, row: Int, col: Int, widthPt: Float, heightPt: Float, ch: DocChart) {
        ZipFile(source).use { zip ->
            val doc = OfficeFormats.parse(zip.getInputStream(zip.getEntry("content.xml") ?: error("Not a spreadsheet.")).use { it.readBytes() })
            val T = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
            val table = doc.getElementsByTagNameNS(T, "table").let { l -> (0 until l.length).map { l.item(it) as Element } }
                .filter { t -> generateSequence(t.parentNode) { it.parentNode }.none { it is Element && it.localName == "table" } }.getOrNull(index) ?: error("No sheet ${index + 1}.")
            val cell = SheetMerge.odsCell(doc, table, row, col)
            val name = generateSequence(1) { it + 1 }.map { "Object tetra-$it" }.first { zip.getEntry("$it/content.xml") == null }
            fun cm(pt: Float) = String.format(java.util.Locale.ROOT, "%.3fcm", pt / 28.346457f)
            val frame = OfficeFormats.parse("""<draw:frame xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" xmlns:xlink="http://www.w3.org/1999/xlink" draw:z-index="0" draw:name="Chart tetra" svg:x="0cm" svg:y="0cm" svg:width="${cm(widthPt)}" svg:height="${cm(heightPt)}"><draw:object xlink:href="./$name" xlink:type="simple" xlink:show="embed" xlink:actuate="onLoad"/></draw:frame>""".toByteArray()).documentElement
            cell.insertBefore(doc.importNode(frame, true), cell.firstChild)
            var manifest = zip.getEntry("META-INF/manifest.xml")?.let { e -> zip.getInputStream(e).use { String(it.readBytes(), Charsets.UTF_8) } } ?: error("No manifest.")
            manifest = manifest.replace("</manifest:manifest>", """<manifest:file-entry manifest:full-path="$name/" manifest:version="1.3" manifest:media-type="application/vnd.oasis.opendocument.chart"/><manifest:file-entry manifest:full-path="$name/content.xml" manifest:media-type="text/xml"/></manifest:manifest>""")
            SheetSave.rezip(zip, target, mapOf("content.xml" to SlidesEdit.domXml(doc), "$name/content.xml" to ChartWriter.odf(ch, widthPt, heightPt).toByteArray(), "META-INF/manifest.xml" to manifest.toByteArray()), "mimetype")
        }
    }
}
