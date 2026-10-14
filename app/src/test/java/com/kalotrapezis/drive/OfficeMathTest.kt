package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeMathTest {
    private val M = OfficeMath.M

    /** Word's own OMML for (−b ± √(b²−4ac)) / 2a, as Word 365 writes it. */
    private val quadratic = """<m:oMath xmlns:m="$M" xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><m:r><m:t>x=</m:t></m:r><m:f><m:num><m:r><m:t>−b±</m:t></m:r><m:rad><m:radPr><m:degHide m:val="1"/></m:radPr><m:deg/><m:e><m:sSup><m:e><m:r><m:t>b</m:t></m:r></m:e><m:sup><m:r><m:t>2</m:t></m:r></m:sup></m:sSup><m:r><m:t>−4ac</m:t></m:r></m:e></m:rad></m:num><m:den><m:r><m:t>2a</m:t></m:r></m:den></m:f></m:oMath>"""

    @Test fun `Word's equation is read into what it shows`() {
        val n = OfficeMath.parse(quadratic)!!
        assertTrue(n is MathNode.Row)
        val frac = (n as MathNode.Row).items[1] as MathNode.Frac
        assertTrue((frac.num as MathNode.Row).items[1] is MathNode.Radical)
        assertEquals("x=(−b±sqrt(b^2−4ac))/(2a)", OfficeMath.linear(n))
    }

    @Test fun `the linear form makes the same equation as Word's`() {
        val typed = OfficeMath.fromLinear("x = (-b +- sqrt(b^2 - 4ac))/(2a)")
        // Drawn alike: the same fraction of the same pieces (spaces aside).
        assertEquals(OfficeMath.linear(OfficeMath.parse(quadratic)!!), OfficeMath.linear(typed).replace(" ", ""))
    }

    @Test fun `sums, integrals, functions, limits, accents and matrices read from the linear form`() {
        val sum = OfficeMath.fromLinear("∑_(i=1)^n i^2 = 1") as MathNode.Row
        val nary = sum.items[0] as MathNode.Nary
        assertEquals("∑", nary.op); assertTrue(nary.under); assertEquals(MathNode.Text("n"), nary.sup)
        assertTrue(nary.body is MathNode.Script) // the body stops at "="
        val integral = OfficeMath.fromLinear("\\int_0^1 f(x) dx") as MathNode.Nary
        assertEquals(false, integral.under)
        assertTrue(OfficeMath.fromLinear("sin x") is MathNode.Func)
        val lim = OfficeMath.fromLinear("lim_(x->0) f(x)") as MathNode.Func
        assertTrue(lim.name is MathNode.Limit)
        assertEquals(MathNode.Accent("̂", MathNode.Text("x")), OfficeMath.fromLinear("hat(x)"))
        assertEquals(MathNode.Matrix(listOf(listOf(MathNode.Text("a"), MathNode.Text("b")), listOf(MathNode.Text("c"), MathNode.Text("d")))), OfficeMath.fromLinear("matrix(a&b@c&d)"))
        assertEquals(MathNode.Text("α"), OfficeMath.fromLinear("\\alpha"))
        assertEquals(MathNode.Radical(MathNode.Text("x"), MathNode.Text("3")), OfficeMath.fromLinear("root(3,x)"))
    }

    @Test fun `an equation written as OMML reads back the same, and its linear form too`() {
        for (src in listOf("x^2 + y^2 = r^2", "a/b + c_i^2", "∑_(i=1)^n i", "sqrt(x+1)", "lim_(x→0) sin x/x", "hat(v) ⋅ vec(u)", "matrix(1&0@0&1)", "|x| ≤ 1", "f(x)=e^(-x^2)")) {
            val n = OfficeMath.fromLinear(src)
            assertEquals(src, n, OfficeMath.parse(OfficeMath.equation(n, display = false)))
            assertEquals(src, n, OfficeMath.parse(OfficeMath.equation(n, display = true)))
            assertEquals(src, n, OfficeMath.fromLinear(OfficeMath.linear(n)))
        }
    }

    @Test fun `a fraction stands taller than its line, a script beside its base`() {
        val x = MathBox.of(MathNode.Text("x"), 11f)
        val frac = MathBox.of(OfficeMath.fromLinear("a/b"), 11f)
        assertTrue(frac.height > x.height * 1.5f)
        val sq = MathBox.of(OfficeMath.fromLinear("x^2"), 11f)
        assertTrue(sq.width > x.width && sq.ascent > x.ascent)
        val sum = MathBox.of(OfficeMath.fromLinear("∑_(i=1)^n i"), 11f)
        assertTrue(sum.height > frac.height)
    }

    @Test fun `a Word document with an equation is no longer copy-only, and keeps it as it was`() {
        val math = OfficeMath.parse(quadratic)!!
        val img = OfficeMath.image("e1", math, quadratic, 11f)
        val display = OfficeMath.equation(OfficeMath.fromLinear("E=mc^2"), display = true)
        val doc = OfficeDoc(listOf(
            DocPara(listOf(DocRun("Roots: "), DocRun(OBJECT_MARK.toString(), image = img), DocRun(" done."))),
            DocPara(listOf(DocRun(OBJECT_MARK.toString(), image = OfficeMath.image("e2", OfficeMath.parse(display)!!, display, 11f))), align = DocAlign.Center),
        ))
        val bytes = OfficeFormats.write(doc, OfficeKind.Docx)
        val back = OfficeFormats.read(bytes, OfficeKind.Docx)
        assertTrue(back.blocking.toString(), back.blocking.isEmpty())
        val eq = back.paras[0].runs.mapNotNull { it.image?.math }
        assertEquals(listOf(math), eq)
        assertEquals("Roots: ￼ done.", back.paras[0].text)
        val second = back.paras[1].runs.single().image
        assertNotNull(second?.math)
        assertEquals(DocAlign.Center, back.paras[1].align)
        // The file's XML: the equation beside the runs (never inside a w:r), as Word reads it.
        val xml = java.util.zip.ZipInputStream(bytes.inputStream()).use { z -> generateSequence { z.nextEntry }.first { it.name == "word/document.xml" }.let { String(z.readBytes()) } }
        assertTrue(xml, Regex("""<w:r>(?:(?!</w:r>).)*<m:oMath""").find(xml) == null)
        assertTrue(xml, "<m:oMathPara" in xml && "<m:rad>" in xml)
    }
}
