package com.kalotrapezis.drive

/**
 * Design › Page colour and Page borders (Word's Design tab, 2026-09-30), written in each format's own words: Word's
 * w:background (shown once settings say displayBackgroundShape) and a pgBorders box in every section, 24 pt in from
 * the page's edges as Word's Page Borders › Box puts it; an OpenDocument's page layouts' background colour and border
 * (LibreOffice draws it round the margins, the text 4 pt inside it). Null takes each away.
 */
internal object PageDesign {
    private fun hex(c: Int) = "%06X".format(c and 0xFFFFFF)

    fun docx(source: Map<String, ByteArray>, color: Int?, border: Border?): LinkedHashMap<String, ByteArray> {
        val parts = LinkedHashMap(source)
        val docPath = documentPath(parts)
        var xml = String(parts.getValue(docPath), Charsets.UTF_8)
        xml = xml.replace(Regex("""<w:background\b[^>]*?(?:/>|>[\s\S]*?</w:background>)"""), "")
        if (color != null) Regex("""<w:document\b[^>]*>""").find(xml)?.let { m -> xml = xml.replaceRange(m.range.last + 1, m.range.last + 1, """<w:background w:color="${hex(color)}"/>""") }
        xml = Regex("""<w:sectPr\b[^>]*?(?:/>|>[\s\S]*?</w:sectPr>)""").replace(xml) { m -> section(m.value, border) }
        parts[docPath] = xml.toByteArray()
        // Word draws the page's colour only when its settings say so.
        val settingsPath = docPath.substringBeforeLast('/') + "/settings.xml"
        if (color != null && settingsPath !in parts) {
            // A document without settings (Writer's own new one): a settings part, its relationship and its type.
            parts[settingsPath] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"></w:settings>""".toByteArray()
            val relsPath = relsPath(docPath)
            val rels = parts[relsPath]?.let { String(it, Charsets.UTF_8) } ?: """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""
            val id = generateSequence(1) { it + 1 }.map { "rIdTetraSettings$it" }.first { "\"$it\"" !in rels }
            parts[relsPath] = rels.replace("</Relationships>", """<Relationship Id="$id" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/></Relationships>""").toByteArray()
            parts["[Content_Types].xml"] = String(parts.getValue("[Content_Types].xml"), Charsets.UTF_8).replace("</Types>",
                """<Override PartName="/$settingsPath" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/></Types>""").toByteArray()
        }
        parts[settingsPath]?.let { String(it, Charsets.UTF_8) }?.takeIf { color != null && "<w:displayBackgroundShape" !in it }?.let { s ->
            // In the schema's order: after writeProtection, view and zoom, before the rest.
            val after = Regex("""<w:(?:writeProtection|view|zoom)\b[^>]*?(?:/>|>[\s\S]*?</w:(?:writeProtection|view|zoom)>)""").findAll(s).lastOrNull()?.range?.last
                ?: Regex("""<w:settings\b[^>]*>""").find(s)?.range?.last ?: return@let
            parts[settingsPath] = (s.substring(0, after + 1) + "<w:displayBackgroundShape/>" + s.substring(after + 1)).toByteArray()
        }
        return parts
    }

    private fun section(sect: String, border: Border?): String {
        val self = sect.endsWith("/>") && !sect.contains("</w:sectPr>")
        val open = if (self) sect.dropLast(2) + ">" else sect.substring(0, sect.indexOf('>') + 1)
        var inner = if (self) "" else sect.substring(sect.indexOf('>') + 1, sect.length - "</w:sectPr>".length)
        inner = inner.replace(Regex("""<w:pgBorders\b[^>]*?(?:/>|>[\s\S]*?</w:pgBorders>)"""), "")
        if (border != null) {
            val sz = (border.width * 8).toInt().coerceIn(2, 96)
            val c = if (border.color == 0) "auto" else hex(border.color)
            val box = """<w:pgBorders w:offsetFrom="page">""" + listOf("top", "left", "bottom", "right").joinToString("") { """<w:$it w:val="single" w:sz="$sz" w:space="24" w:color="$c"/>""" } + "</w:pgBorders>"
            // Word's order: after the page's size, margins and paper source, before line numbers and the rest.
            val at = Regex("""<w:(lnNumType|pgNumType|cols|formProt|vAlign|noEndnote|titlePg|textDirection|bidi|rtlGutter|docGrid|printerSettings)\b""").find(inner)?.range?.first ?: inner.length
            inner = inner.substring(0, at) + box + inner.substring(at)
        }
        return "$open$inner</w:sectPr>"
    }

    fun odt(source: Map<String, ByteArray>, color: Int?, border: Border?): LinkedHashMap<String, ByteArray> {
        val parts = LinkedHashMap(source)
        var xml = String(parts.getValue("styles.xml"), Charsets.UTF_8)
        xml = Regex("""<style:page-layout-properties\b[^>]*>""").replace(xml) { m ->
            val self = m.value.endsWith("/>")
            var tag = m.value.removeSuffix("/>").removeSuffix(">")
            tag = tag.replace(Regex("""\s(fo:background-color|fo:border(?:-top|-bottom|-left|-right)?|fo:padding(?:-top|-bottom|-left|-right)?|draw:fill|draw:fill-color)="[^"]*""""), "")
            if (color != null) tag += """ fo:background-color="#${hex(color).lowercase()}" draw:fill="solid" draw:fill-color="#${hex(color).lowercase()}""""
            if (border != null) tag += """ fo:border="${"%.2f".format(java.util.Locale.ROOT, border.width)}pt solid #${if (border.color == 0) "000000" else hex(border.color).lowercase()}" fo:padding="4pt""""
            tag + if (self) "/>" else ">"
        }
        parts["styles.xml"] = xml.toByteArray()
        return parts
    }
}
