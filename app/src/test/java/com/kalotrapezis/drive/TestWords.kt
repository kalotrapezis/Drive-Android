package com.kalotrapezis.drive

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [Words] for JVM tests, which have no Android: Tetra's own string resources read from src/main/res/values(-[lang])/strings.xml,
 * filled in as Android fills them. A plural takes "one" for 1, else "other" — right for the languages tested here.
 */
internal fun testWords(lang: String, units: String = "metric"): Words {
    val names = (R.string::class.java.fields + R.plurals::class.java.fields).associate { it.getInt(null) to it.name }
    fun read(folder: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/strings.xml"))
        val out = HashMap<String, String>()
        fun text(n: org.w3c.dom.Node) = n.textContent.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        val s = doc.getElementsByTagName("string")
        for (i in 0 until s.length) s.item(i).let { out[it.attributes.getNamedItem("name").nodeValue] = text(it) }
        val p = doc.getElementsByTagName("item")
        for (i in 0 until p.length) p.item(i).takeIf { it.parentNode.nodeName == "plurals" }?.let {
            out[it.parentNode.attributes.getNamedItem("name").nodeValue + "#" + it.attributes.getNamedItem("quantity").nodeValue] = text(it)
        }
        return out
    }
    val texts = read("values") + if (lang == "en") emptyMap() else read("values-$lang")
    val locale = Locale.forLanguageTag(lang)
    return Words(lang, units, { id, a -> String.format(locale, texts.getValue(names.getValue(id)), *a) },
        { id, n, a -> String.format(locale, texts.getValue(names.getValue(id) + if (n == 1) "#one" else "#other"), *a) })
}
