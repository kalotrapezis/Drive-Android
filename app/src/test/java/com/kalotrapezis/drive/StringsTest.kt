package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every language file has every English string, with the same %1$s / %1$d placeholders, and nothing it lacks. */
class StringsTest {
    private val res = File("src/main/res")
    private val placeholder = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfx]")

    /** name (a plural as name#quantity) → text, from a folder's strings*.xml. */
    private fun strings(folder: File): Map<String, String> = folder.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty()
        .flatMap { f ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val list = mutableListOf<Pair<String, String>>()
            val s = doc.getElementsByTagName("string")
            for (i in 0 until s.length) s.item(i).let { n ->
                if (n.attributes.getNamedItem("translatable")?.nodeValue != "false") list += n.attributes.getNamedItem("name").nodeValue to n.textContent
            }
            val p = doc.getElementsByTagName("plurals")
            for (i in 0 until p.length) p.item(i).let { n ->
                val name = n.attributes.getNamedItem("name").nodeValue
                val items = n.childNodes
                for (j in 0 until items.length) items.item(j).takeIf { it.nodeName == "item" }?.let { list += "$name#plural" to it.textContent }
            }
            list
        }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.joinToString("\u0000") }

    private fun holders(text: String) = placeholder.findAll(text.replace("%%", "")).map { it.value }.toSortedSet()

    @Test fun everyLanguageIsComplete() {
        val english = strings(File(res, "values"))
        val languages = res.listFiles { f -> f.name.matches(Regex("values-[a-z]{2,3}(-r[A-Z]{2})?")) }.orEmpty()
        assertTrue(languages.isNotEmpty())
        for (folder in languages) {
            val other = strings(folder)
            assertEquals("${folder.name}: missing", emptySet<String>(), english.keys - other.keys)
            assertEquals("${folder.name}: not in English", emptySet<String>(), other.keys - english.keys)
            for ((name, text) in english) {
                // a plural's "one" may leave its number out ("A file" / "Ένα αρχείο"), so only the other strings must match exactly
                if (name.endsWith("#plural")) continue
                assertEquals("${folder.name}: $name's placeholders", holders(text), holders(other.getValue(name)))
            }
        }
    }
}
