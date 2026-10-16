package com.kalotrapezis.drive

import java.io.File

/**
 * Slides › Design (asked 16 Oct: "some themes and colours, some dark ones some light ones"): a theme is PowerPoint's —
 * its colour scheme (the dark and light text/background colours, six accents, links) and its two fonts — and, for
 * Tetra's own, a band in its first accent on the master (as PowerPoint's Facet has its green edge). Applied to a .pptx
 * by writing its theme part(s) and its master(s): everything that uses the theme's colours and fonts follows.
 */
internal data class SlideTheme(
    val name: String, val dark: Boolean,
    /** dk1, lt1, dk2, lt2, accent1–6, hlink, folHlink (0xRRGGBB); a dark theme's lt1 is its dark background */ val colors: List<Int>,
    val major: String, val minor: String,
    /** a band in accent1 on the master: 'l' left, 'r' right, 'b' bottom, 't' top; null none */ val band: Char? = null,
)

internal object SlidesThemes {
    private fun t(name: String, dark: Boolean, major: String, minor: String, band: Char?, vararg c: Long) = SlideTheme(name, dark, c.map { it.toInt() }, major, minor, band)
    /** Tetra's themes: four light, four dark. */
    val BUILT_IN = listOf(
        t("Office", false, "Calibri Light", "Calibri", null, 0x000000, 0xFFFFFF, 0x44546A, 0xE7E6E6, 0x4472C4, 0xED7D31, 0xA5A5A5, 0xFFC000, 0x5B9BD5, 0x70AD47, 0x0563C1, 0x954F72),
        t("Leaf", false, "Trebuchet MS", "Trebuchet MS", 'r', 0x000000, 0xFFFFFF, 0x2C3C43, 0xEBEBEB, 0x90C226, 0x54A021, 0xE6B91E, 0xE76618, 0xC42F1A, 0x918655, 0x99CA3C, 0xB9D181),
        t("Ocean", false, "Calibri Light", "Calibri", 'b', 0x10253F, 0xFFFFFF, 0x1F4E79, 0xDEEBF7, 0x1CADE4, 0x2683C6, 0x27CED7, 0x42BA97, 0x3E8853, 0x62A39F, 0x0563C1, 0x5E8DB3),
        t("Paper", false, "Georgia", "Georgia", 't', 0x3B2F2A, 0xFBF8F1, 0x5A4636, 0xEFE7DA, 0xA53010, 0xDE7E18, 0x9F8351, 0x728653, 0x92AA4C, 0x6AAC91, 0xA53010, 0x7F5F3F),
        t("Night", true, "Segoe UI Light", "Segoe UI", null, 0xF2F2F2, 0x1E1E1E, 0xD9D9D9, 0x333333, 0x4FC3F7, 0xFF8A65, 0xAED581, 0xFFD54F, 0xBA68C8, 0x4DB6AC, 0x81D4FA, 0xCE93D8),
        t("Slate", true, "Calibri Light", "Calibri", 'l', 0xECEFF1, 0x2B3A42, 0xCFD8DC, 0x37474F, 0xFF8A65, 0xFFD54F, 0x4DB6AC, 0x81C784, 0x64B5F6, 0xBA68C8, 0x80CBC4, 0xB0BEC5),
        t("Midnight", true, "Georgia", "Calibri", 'b', 0xE0E1DD, 0x0D1B2A, 0xC9CDD3, 0x1B263B, 0xFCA311, 0xE63946, 0x2A9D8F, 0xE9C46A, 0x778DA9, 0xF4A261, 0xFCA311, 0x778DA9),
        t("Charcoal", true, "Trebuchet MS", "Trebuchet MS", 't', 0xF5F5F5, 0x222222, 0xE0E0E0, 0x3A3A3A, 0xD4AF37, 0xC0C0C0, 0xCD7F32, 0x8FBC8F, 0x87CEEB, 0xE9967A, 0xD4AF37, 0xC0C0C0),
    )

    private val SLOTS = listOf("dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")
    private const val BAND = "Tetra theme band"

    /** [theme]'s colour scheme as DrawingML. */
    fun clrScheme(theme: SlideTheme) = """<a:clrScheme name="${esc(theme.name)}">""" + SLOTS.zip(theme.colors).joinToString("") { (slot, c) -> """<a:$slot><a:srgbClr val="${"%06X".format(c and 0xFFFFFF)}"/></a:$slot>""" } + "</a:clrScheme>"
    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** A theme part made [theme]'s: its colour scheme, its fonts' Latin faces, its name; the rest (effects, other scripts' fonts) as it was. */
    fun themed(xml: String, theme: SlideTheme): String = xml
        .replace(Regex("""<a:clrScheme\b[\s\S]*?</a:clrScheme>"""), Regex.escapeReplacement(clrScheme(theme)))
        .replace(Regex("""(<a:majorFont>\s*<a:latin typeface=")[^"]*(")"""), "$1${Regex.escapeReplacement(esc(theme.major))}$2")
        .replace(Regex("""(<a:minorFont>\s*<a:latin typeface=")[^"]*(")"""), "$1${Regex.escapeReplacement(esc(theme.minor))}$2")
        .replace(Regex("""(<a:theme\b[^>]*\sname=")[^"]*(")"""), "$1${Regex.escapeReplacement(esc(theme.name))}$2")

    /** A master made [theme]'s: its background the theme's (bg1), its band (or none) behind everything on it. */
    fun mastered(xml: String, theme: SlideTheme, width: Long, height: Long): String {
        var s = xml.replace(Regex("""<p:bg>[\s\S]*?</p:bg>"""), """<p:bg><p:bgRef idx="1001"><a:schemeClr val="bg1"/></p:bgRef></p:bg>""")
        if ("<p:bg>" !in s) s = s.replaceFirst(Regex("""(<p:cSld\b[^>]*>)"""), "$1<p:bg><p:bgRef idx=\"1001\"><a:schemeClr val=\"bg1\"/></p:bgRef></p:bg>")
        // the band of a theme applied before: gone
        s = s.replace(Regex("""<p:sp><p:nvSpPr><p:cNvPr id="\d+" name="$BAND"/>[\s\S]*?</p:sp>"""), "")
        val band = theme.band ?: return s
        val k = 0.06
        val (x, y, w, h) = when (band) {
            'l' -> listOf(0L, 0L, (width * k / 2).toLong(), height); 'r' -> listOf(width - (width * k).toLong(), 0L, (width * k).toLong(), height)
            't' -> listOf(0L, 0L, width, (height * k / 2).toLong()); else -> listOf(0L, height - (height * k).toLong(), width, (height * k).toLong())
        }
        val id = (Regex("""<p:cNvPr id="(\d+)"""").findAll(s).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 1) + 1
        val sp = """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="$BAND"/><p:cNvSpPr/><p:nvPr userDrawn="1"/></p:nvSpPr><p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$w" cy="$h"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:solidFill><a:schemeClr val="accent1"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr></p:sp>"""
        // first in the tree: behind the placeholders and the slides' own
        return s.replaceFirst(Regex("""(<p:spTree>[\s\S]*?</p:grpSpPr>)"""), "$1${Regex.escapeReplacement(sp)}")
    }

    /** [source] (a .pptx) to [target] with [theme]: every master's theme part and the masters themselves. */
    fun apply(source: File, theme: SlideTheme, target: File) {
        java.util.zip.ZipFile(source).use { zip ->
            fun read(n: String) = zip.getEntry(n)?.let { e -> zip.getInputStream(e).use { String(it.readBytes()) } }
            val pres = read("ppt/presentation.xml") ?: error("Not a presentation.")
            val size = Regex("""<p:sldSz\s+cx="(\d+)"\s+cy="(\d+)"""").find(pres)
            val w = size?.groupValues?.get(1)?.toLongOrNull() ?: 9144000L; val h = size?.groupValues?.get(2)?.toLongOrNull() ?: 6858000L
            val out = HashMap<String, ByteArray?>()
            val masters = zip.entries().asSequence().map { it.name }.filter { Regex("""^ppt/slideMasters/slideMaster\d+\.xml$""").matches(it) }.toList()
            for (m in masters) {
                out[m] = mastered(read(m)!!, theme, w, h).toByteArray()
                // the master's theme: from its relationships
                val rels = read(m.replace("slideMasters/", "slideMasters/_rels/") + ".rels").orEmpty()
                Regex("""Target="\.\./theme/(theme\d+\.xml)"""").find(rels)?.groupValues?.get(1)?.let { "ppt/theme/$it" }?.let { tp -> read(tp)?.let { out[tp] = themed(it, theme).toByteArray() } }
            }
            SheetSave.rezip(zip, target, out, stored = null)
        }
    }

    /** A template's (or any deck's) own theme, to apply elsewhere: its first theme part's colours and fonts. */
    fun fromFile(file: File): SlideTheme? = runCatching {
        java.util.zip.ZipFile(file).use { zip ->
            val name = zip.entries().asSequence().map { it.name }.filter { Regex("""^ppt/theme/theme\d+\.xml$""").matches(it) }.minOrNull() ?: return null
            val xml = zip.getInputStream(zip.getEntry(name)).use { String(it.readBytes()) }
            val scheme = Regex("""<a:clrScheme\b[\s\S]*?</a:clrScheme>""").find(xml)?.value ?: return null
            val colors = SLOTS.map { slot -> Regex("""<a:$slot>\s*<a:(?:srgbClr val|sysClr\b[^>]*lastClr)="([0-9A-Fa-f]{6})"""").find(scheme)?.groupValues?.get(1)?.toInt(16) ?: 0 }
            fun font(kind: String) = Regex("""<a:$kind>\s*<a:latin typeface="([^"]*)"""").find(xml)?.groupValues?.get(1) ?: "Calibri"
            // dark when its background (lt1) is darker than its text (dk1)
            fun lum(c: Int) = 0.299 * ((c shr 16) and 255) + 0.587 * ((c shr 8) and 255) + 0.114 * (c and 255)
            SlideTheme(file.nameWithoutExtension, lum(colors[1]) < lum(colors[0]), colors, font("majorFont"), font("minorFont"))
        }
    }.getOrNull()
}
