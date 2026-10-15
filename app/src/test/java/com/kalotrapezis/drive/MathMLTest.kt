package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** LibreOffice's formulas (MathML objects) read, drawn as Word's equations are, and written. */
class MathMLTest {
    private fun images(doc: OfficeDoc) = doc.paras.flatMap { p -> p.runs.mapNotNull { it.image } }

    @Test fun `LibreOffice's own formulas are read as equations`() {
        // LibreOffice 26.2's conversion of a Word file with a²+√b in a line and ∑_(i=1)^n x_i = α/2 (x+1) sin θ on its own.
        val bytes = javaClass.classLoader!!.getResource("equations.odt").readBytes()
        val doc = OfficeFormats.read(bytes, OfficeKind.Odt)
        assertTrue(doc.blocking.toString(), doc.blocking.isEmpty())
        val maths = images(doc).map { it.math }
        assertEquals(2, maths.size)
        assertEquals("a^2+sqrt(b)", OfficeMath.linear(maths[0]!!))
        val second = OfficeMath.linear(maths[1]!!)
        assertTrue(second, second.startsWith("∑_(i=1)^n x_i=α/2(x+1)") && "sin" in second && second.endsWith("θ"))
        val n = maths[1]!! as MathNode.Row
        assertTrue(n.items[0] is MathNode.Nary && (n.items[0] as MathNode.Nary).under)
        assertTrue(n.items.any { it is MathNode.Delim })
        // Saved unchanged: the objects are the file's own.
        val again = OfficeFormats.read(OfficeFormats.write(doc, OfficeKind.Odt, bytes), OfficeKind.Odt)
        assertEquals(maths, images(again).map { it.math })
    }

    @Test fun `the tree goes to MathML and back`() {
        for (s in listOf("x=(-b±sqrt(b^2-4ac))/(2a)", "∑_(i=1)^n x_i", "∫_0^1 f(x)dx", "root(3,x)+hat(y)", "matrix(a&b@c&d)", "e^(iπ)+1=0", "lim_(x→0) x")) {
            val tree = OfficeMath.fromLinear(s)
            val back = OfficeMath.fromMathML(OfficeMath.mathml(tree, true).toByteArray())
            assertNotNull(s, back)
            assertEquals(s, OfficeMath.linear(tree), OfficeMath.linear(back!!))
        }
    }

    @Test fun `a new equation in an odt is a LibreOffice Math object`() {
        val m = OfficeMath.fromLinear("a^2+b^2=c^2")
        val img = OfficeMath.image("n1", m, "", 12f).with(raw = null)
        val bytes = OfficeFormats.write(OfficeDoc(listOf(DocPara(listOf(DocRun("So "), DocRun(OBJECT_MARK.toString(), image = img))))), OfficeKind.Odt)
        val parts = OfficeFormats.unzip(bytes)
        val obj = parts.keys.single { it.endsWith("/content.xml") && it.startsWith("Object") }
        assertTrue(String(parts.getValue(obj), Charsets.UTF_8).contains("<msup><mrow><mi>a</mi></mrow><mrow><mn>2</mn></mrow></msup>"))
        assertTrue(String(parts.getValue("META-INF/manifest.xml"), Charsets.UTF_8).contains("application/vnd.oasis.opendocument.formula"))
        val back = images(OfficeFormats.read(bytes, OfficeKind.Odt)).single()
        assertEquals("a^2+b^2=c^2", OfficeMath.linear(back.math!!))
    }
}
