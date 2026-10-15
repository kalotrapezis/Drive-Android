package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design › Colours and Fonts: a Word file's theme, and colours that name it. */
class DesignThemesTest {
    @Test fun `theme colours go into the theme, and theme-named colours follow`() {
        // Writer's own new Word file has no theme: Office's is added, then given Green's colours.
        val start = OfficeFormats.unzip(OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("x"))))), OfficeKind.Docx))
        val styles = String(start.getValue("word/styles.xml"), Charsets.UTF_8)
            .replace("</w:styles>", """<w:style w:type="paragraph" w:styleId="Accent"><w:name w:val="Accent"/><w:rPr><w:color w:val="4472C4" w:themeColor="accent1" w:themeShade="BF"/></w:rPr></w:style></w:styles>""")
        val parts = LinkedHashMap(start).apply { put("word/styles.xml", styles.toByteArray()) }
        val green = DocTheme.withColors(parts, ThemeColors.Green)
        assertEquals(0x549E39, DocTheme.colorsOf(green)["accent1"])
        assertTrue(String(green.getValue("[Content_Types].xml"), Charsets.UTF_8).contains("/word/theme/theme1.xml"))
        // accent1 549E39 shaded by BF (75%): 3F762B.
        assertTrue(String(green.getValue("word/styles.xml"), Charsets.UTF_8).contains("""<w:color w:val="3F762B" w:themeColor="accent1" w:themeShade="BF"/>"""))
        // The package still reads, and its theme stays through saving.
        val bytes = OfficeFormats.zip(green, null)
        java.io.File("build/office-roundtrip").apply { mkdirs() }.resolve("theme-green.docx").writeBytes( // for a look in LibreOffice
            OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("Accent-coloured heading", sizePt = 28f)), keep = DocKeep(style = "Accent")))), OfficeKind.Docx, bytes))
        val saved = OfficeFormats.unzip(OfficeFormats.write(OfficeFormats.read(bytes, OfficeKind.Docx), OfficeKind.Docx, bytes))
        assertEquals(0x549E39, DocTheme.colorsOf(saved)["accent1"])
    }

    @Test fun `theme fonts are the headings' and the text's`() {
        val bytes = OfficeFormats.zip(DocTheme.withFonts(OfficeFormats.unzip(OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("x"))))), OfficeKind.Docx)), ThemeFonts.Georgia), null)
        val theme = String(OfficeFormats.unzip(bytes).getValue("word/theme/theme1.xml"), Charsets.UTF_8)
        assertTrue(theme, Regex("""<a:majorFont><a:latin typeface="Georgia"""").containsMatchIn(theme) && Regex("""<a:minorFont><a:latin typeface="Georgia"""").containsMatchIn(theme))
        // A style that takes the theme's heading font shows Georgia on the pages.
        val doc = OfficeFormats.unzip(bytes).also { p ->
            p["word/styles.xml"] = String(p.getValue("word/styles.xml"), Charsets.UTF_8).replace("</w:styles>",
                """<w:style w:type="paragraph" w:styleId="Big"><w:name w:val="Big"/><w:rPr><w:rFonts w:asciiTheme="majorHAnsi" w:hAnsiTheme="majorHAnsi"/></w:rPr></w:style></w:styles>""").toByteArray()
        }
        val styles = DocStyles.of(OfficeKind.Docx, OfficeFormats.zip(doc, null))
        assertEquals("Georgia", styles.look(DocPara(listOf(DocRun("t")), keep = DocKeep(style = "Big"))).font)
    }
}
