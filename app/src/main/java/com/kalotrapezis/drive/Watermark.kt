package com.kalotrapezis.drive

import kotlin.math.roundToInt

/**
 * Design › Watermark (Word's, 2026-09-30): a word across every page, light grey, half see-through, turned 45°, behind
 * the text. Written as each program writes its own, so each shows it and can take it away: Word's WordArt shape
 * (PowerPlusWaterMarkObject) in the header; LibreOffice's fontwork shape (Format › Watermark) in the page style's header.
 */
internal object Watermark {
    const val NAME = "PowerPlusWaterMarkObject"
    private const val VML = "urn:schemas-microsoft-com:vml"
    private const val OFFICE_VML = "urn:schemas-microsoft-com:office:office"
    private const val REL_HEADER = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/header"
    private const val TYPE_HEADER = "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"
    val PRESETS = listOf("CONFIDENTIAL", "DO NOT COPY", "DRAFT", "SAMPLE", "ASAP", "URGENT")

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    /** The watermark's text in a header's (or an ODT's styles') XML, Word's or LibreOffice's; null: none. */
    fun textIn(xml: String): String? {
        if (NAME !in xml) return null
        Regex("""$NAME[\s\S]*?<v:textpath\b[^>]*?\bstring="([^"]*)"""").find(xml)?.let { return unesc(it.groupValues[1]) }
        Regex("""draw:name="$NAME[^"]*"[\s\S]*?<text:p\b[^>]*>([\s\S]*?)</text:p>""").find(xml)?.let { return unesc(it.groupValues[1].replace(Regex("<[^>]+>"), "")).trim() }
        return null
    }

    /**
     * Its box: the text's width, and a height near its letters' own (both programs stretch the letters to the box) —
     * about 3 : 1 for DRAFT, as LibreOffice makes it; long words no flatter than 5 : 1.
     */
    private fun box(text: String, page: DocPage): Pair<Float, Float> {
        val w = page.textWidth
        return w to (w / (0.6f * text.length.coerceAtLeast(1))).coerceIn(w / 5, w / 2.5f)
    }

    fun docx(source: Map<String, ByteArray>, text: String?, page: DocPage): LinkedHashMap<String, ByteArray> {
        val parts = LinkedHashMap(source)
        val docPath = documentPath(parts)
        val dir = docPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        // Any watermark there is, out of every header.
        val old = Regex("""<w:r\b(?:(?!<w:r\b)[\s\S])*?$NAME[\s\S]*?</w:r>""")
        for ((path, bytes) in parts.toList()) if (path.startsWith(dir + "header") && path.endsWith(".xml")) {
            val x = String(bytes, Charsets.UTF_8)
            if (NAME in x) parts[path] = x.replace(old, "").toByteArray()
        }
        if (text.isNullOrBlank()) return parts
        val (w, h) = box(text, page)
        val run = """<w:r><w:rPr><w:noProof/></w:rPr><w:pict><v:shapetype id="_x0000_t136" coordsize="21600,21600" o:spt="136" adj="10800" path="m@7,l@8,m@5,21600l@6,21600e">""" +
            """<v:formulas><v:f eqn="sum #0 0 10800"/><v:f eqn="prod #0 2 1"/><v:f eqn="sum 21600 0 @1"/><v:f eqn="sum 0 0 @2"/><v:f eqn="sum 21600 0 @3"/><v:f eqn="if @0 @3 0"/><v:f eqn="if @0 21600 @1"/><v:f eqn="if @0 0 @2"/><v:f eqn="if @0 @4 21600"/><v:f eqn="mid @5 @6"/><v:f eqn="mid @8 @5"/><v:f eqn="mid @7 @8"/><v:f eqn="mid @6 @7"/><v:f eqn="sum @6 0 @5"/></v:formulas>""" +
            """<v:path textpathok="t" o:connecttype="custom" o:connectlocs="@9,0;@10,10800;@11,21600;@12,10800" o:connectangles="270,180,90,0"/><v:textpath on="t" fitshape="t"/><v:handles><v:h position="#0,bottomRight" xrange="6629,14971"/></v:handles><o:lock v:ext="edit" text="t" shapetype="t"/></v:shapetype>""" +
            """<v:shape id="${NAME}1" o:spid="_x0000_s2049" type="#_x0000_t136" style="position:absolute;margin-left:0;margin-top:0;width:${w.roundToInt()}pt;height:${h.roundToInt()}pt;rotation:315;z-index:-251657216;mso-position-horizontal:center;mso-position-horizontal-relative:margin;mso-position-vertical:center;mso-position-vertical-relative:margin" o:allowincell="f" fillcolor="silver" stroked="f">""" +
            """<v:fill opacity=".5"/><v:textpath style="font-family:&quot;Calibri&quot;;font-size:1pt" string="${esc(text)}"/></v:shape></w:pict></w:r>"""
        fun withNamespaces(x: String): String {
            val open = Regex("""<w:hdr\b[^>]*>""").find(x) ?: return x
            var tag = open.value
            if ("xmlns:v=" !in tag) tag = tag.replaceFirst("<w:hdr", "<w:hdr xmlns:v=\"$VML\"")
            if ("xmlns:o=" !in tag) tag = tag.replaceFirst("<w:hdr", "<w:hdr xmlns:o=\"$OFFICE_VML\"")
            return x.replaceRange(open.range, tag)
        }
        /** In the header's first paragraph (after its properties). */
        fun into(x: String): String {
            val p = Regex("""<w:p\b[^>]*?(/?)>""").find(x) ?: return x.replace("</w:hdr>", "<w:p>$run</w:p></w:hdr>")
            if (p.groupValues[1] == "/") return x.replaceRange(p.range, "<w:p>$run</w:p>")
            var at = p.range.last + 1
            if (x.startsWith("<w:pPr", at)) at = x.indexOf("</w:pPr>", at).takeIf { it >= 0 }?.plus("</w:pPr>".length) ?: at
            return x.substring(0, at) + run + x.substring(at)
        }
        val relsPath = relsPath(docPath)
        var rels = parts[relsPath]?.let { String(it, Charsets.UTF_8) } ?: """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""
        val targets = Regex("""<Relationship\b[^>]*>""").findAll(rels).mapNotNull { m ->
            val id = Regex("""\bId="([^"]*)"""").find(m.value)?.groupValues?.get(1); val t = Regex("""\bTarget="([^"]*)"""").find(m.value)?.groupValues?.get(1)
            if (id != null && t != null && REL_HEADER in m.value) id to (dir + t.removePrefix("/").removePrefix(dir)) else null
        }.toMap()
        var xml = String(parts.getValue(docPath), Charsets.UTF_8)
        // Every header the sections have gets it; a section with no header of its own gets one, with only the watermark.
        val sections = Regex("""<w:sectPr\b[^>]*?(?:/>|>[\s\S]*?</w:sectPr>)""")
        if (!sections.containsMatchIn(xml)) xml = xml.replace("</w:body>", "<w:sectPr/></w:body>")
        val used = sections.findAll(xml).flatMap { s -> Regex("""<w:headerReference\b[^>]*\br:id="([^"]*)"""").findAll(s.value).map { it.groupValues[1] } }.toSet()
        for (id in used) targets[id]?.let { path -> parts[path]?.let { parts[path] = withNamespaces(into(String(it, Charsets.UTF_8))).toByteArray() } }
        if (sections.findAll(xml).any { "<w:headerReference" !in it.value || !Regex("""<w:headerReference\b[^>]*w:type="default"""").containsMatchIn(it.value) }) {
            val name = generateSequence(1) { it + 1 }.map { "header$it.xml" }.first { dir + it !in parts }
            parts[dir + name] = ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:hdr xmlns:w="$W" xmlns:r="$RELATIONSHIPS" xmlns:v="$VML" xmlns:o="$OFFICE_VML"><w:p>$run</w:p></w:hdr>""").toByteArray()
            val id = generateSequence(1) { it + 1 }.map { "rIdTetraWatermark$it" }.first { "\"$it\"" !in rels }
            rels = rels.replace("</Relationships>", """<Relationship Id="$id" Type="$REL_HEADER" Target="$name"/></Relationships>""")
            parts["[Content_Types].xml"] = String(parts.getValue("[Content_Types].xml"), Charsets.UTF_8).replace("</Types>", """<Override PartName="/$dir$name" ContentType="$TYPE_HEADER"/></Types>""").toByteArray()
            xml = sections.replace(xml) { m ->
                if (Regex("""<w:headerReference\b[^>]*w:type="default"""").containsMatchIn(m.value)) m.value
                else if (m.value.endsWith("/>") && "</w:sectPr>" !in m.value) m.value.dropLast(2) + """><w:headerReference w:type="default" r:id="$id"/></w:sectPr>"""
                else m.value.replaceFirst(Regex("""<w:sectPr\b[^>]*>"""), "$0<w:headerReference w:type=\"default\" r:id=\"$id\"/>")
            }
            if (!xml.substring(0, xml.indexOf('>', xml.indexOf("<w:document"))).contains("xmlns:r=")) xml = xml.replaceFirst("<w:document", "<w:document xmlns:r=\"$RELATIONSHIPS\"")
            parts[relsPath] = rels.toByteArray()
        }
        parts[docPath] = xml.toByteArray()
        return parts
    }

    private const val ODT_STYLE = "Tetra_20_watermark"
    private const val ODT_TEXT = "Tetra_20_watermark_20_text"

    fun odt(source: Map<String, ByteArray>, text: String?, page: DocPage): LinkedHashMap<String, ByteArray> {
        val parts = LinkedHashMap(source)
        var xml = String(parts.getValue("styles.xml"), Charsets.UTF_8)
        xml = xml.replace(Regex("""<draw:custom-shape\b[^>]*draw:name="$NAME[\s\S]*?</draw:custom-shape>"""), "")
            .replace(Regex("""<style:style style:name="(?:$ODT_STYLE|$ODT_TEXT)" style:family="\w+">[\s\S]*?</style:style>"""), "")
        if (!text.isNullOrBlank()) {
            val (w, h) = box(text, page)
            // As LibreOffice's Format › Watermark writes it (checked 2026-09-30 against LibreOffice's own file).
            val shape = """<draw:custom-shape text:anchor-type="char" draw:z-index="0" draw:name="$NAME" draw:style-name="$ODT_STYLE" draw:text-style-name="$ODT_TEXT" svg:width="${w.roundToInt()}pt" svg:height="${h.roundToInt()}pt" draw:transform="rotate (0.785398163397448)">""" +
                """<text:p>${esc(text)}</text:p><draw:enhanced-geometry svg:viewBox="0 0 21600 21600" draw:text-areas="0 0 21600 21600" draw:text-path="true" draw:type="fontwork-plain-text" draw:modifiers="10800" draw:enhanced-path="M ?f3 0 L ?f5 0 N M ?f6 21600 L ?f7 21600 N">""" +
                """<draw:equation draw:name="f0" draw:formula="${'$'}0 -10800"/><draw:equation draw:name="f1" draw:formula="?f0 *2"/><draw:equation draw:name="f2" draw:formula="abs(?f1 )"/><draw:equation draw:name="f3" draw:formula="if(?f1 ,0,?f2 )"/><draw:equation draw:name="f4" draw:formula="21600-?f2 "/><draw:equation draw:name="f5" draw:formula="if(?f1 ,?f4 ,21600)"/><draw:equation draw:name="f6" draw:formula="if(?f1 ,?f2 ,0)"/><draw:equation draw:name="f7" draw:formula="if(?f1 ,21600,?f4 )"/>""" +
                """<draw:handle draw:handle-position="${'$'}0 21600" draw:handle-position-x="${'$'}0" draw:handle-position-y="21600" draw:handle-range-x-minimum="6629" draw:handle-range-x-maximum="14971"/></draw:enhanced-geometry></draw:custom-shape>"""
            val styles = """<style:style style:name="$ODT_TEXT" style:family="paragraph"><style:text-properties fo:font-family="&apos;Liberation Sans&apos;" style:font-family-generic="roman" style:font-pitch="variable" fo:font-size="1pt"/></style:style>""" +
                """<style:style style:name="$ODT_STYLE" style:family="graphic"><style:graphic-properties draw:stroke="none" draw:fill="solid" draw:fill-color="#c0c0c0" draw:opacity="50%" draw:auto-grow-height="false" draw:auto-grow-width="false" fo:min-height="${h.roundToInt()}pt" fo:min-width="${w.roundToInt()}pt" style:run-through="background" style:wrap="run-through" style:number-wrapped-paragraphs="no-limit" style:vertical-pos="middle" style:vertical-rel="page-content" style:horizontal-pos="center" style:horizontal-rel="page-content" draw:wrap-influence-on-position="once-concurrent" style:flow-with-text="false"/></style:style>"""
            xml = if ("<office:automatic-styles/>" in xml) xml.replace("<office:automatic-styles/>", "<office:automatic-styles>$styles</office:automatic-styles>")
                else if ("<office:automatic-styles>" in xml) xml.replace("<office:automatic-styles>", "<office:automatic-styles>$styles")
                else xml.replace("<office:master-styles", "<office:automatic-styles>$styles</office:automatic-styles><office:master-styles")
            // Every page style's header (made where there is none), in its first paragraph — as LibreOffice does.
            xml = Regex("""<style:master-page\b[^>]*?(?:/>|>[\s\S]*?</style:master-page>)""").replace(xml) { mm ->
                val m = mm.value
                val header = Regex("""<style:header\b(?![-])[^>]*?(?:/>|>[\s\S]*?</style:header>)""").find(m)
                if (header == null || header.value.endsWith("/>") || "style:display=\"false\"" in header.value) {
                    val fresh = "<style:header><text:p>$shape</text:p></style:header>"
                    when {
                        header != null -> m.replaceRange(header.range, fresh)
                        m.endsWith("/>") -> m.dropLast(2) + ">$fresh</style:master-page>"
                        else -> m.replaceFirst(Regex("""<style:master-page\b[^>]*>"""), "$0$fresh")
                    }
                } else {
                    val h0 = header.value
                    val p = Regex("""<text:(p|h)\b[^>]*?(/?)>""").find(h0)
                    m.replaceRange(header.range, when {
                        p == null -> h0.replace("</style:header>", "<text:p>$shape</text:p></style:header>")
                        p.groupValues[2] == "/" -> h0.replaceRange(p.range, p.value.dropLast(2) + ">$shape</text:${p.groupValues[1]}>")
                        else -> h0.substring(0, p.range.last + 1) + shape + h0.substring(p.range.last + 1)
                    })
                }
            }
        }
        parts["styles.xml"] = xml.toByteArray()
        return parts
    }
}
