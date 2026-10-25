package com.kalotrapezis.drive

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** [UiWords] in English from values/strings*.xml, for JVM tests (they have no Android resources). */
object EnglishWords : UiWords {
    override val locale: Locale = Locale.ENGLISH
    private val strings = HashMap<String, String>()
    private val plurals = HashMap<String, Map<String, String>>()
    init {
        File("src/main/res/values").listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty().forEach { f ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val s = doc.getElementsByTagName("string")
            for (i in 0 until s.length) s.item(i).let { strings[it.attributes.getNamedItem("name").nodeValue] = it.textContent.replace("\\'", "'") }
            val p = doc.getElementsByTagName("plurals")
            for (i in 0 until p.length) p.item(i).let { n ->
                val items = n.childNodes
                plurals[n.attributes.getNamedItem("name").nodeValue] = (0 until items.length).map(items::item).filter { it.nodeName == "item" }
                    .associate { it.attributes.getNamedItem("quantity").nodeValue to it.textContent.replace("\\'", "'") }
            }
        }
    }
    private fun name(type: Class<*>, id: Int) = type.fields.first { it.getInt(null) == id }.name
    override fun get(id: Int, vararg args: Any) = String.format(Locale.ENGLISH, strings.getValue(name(R.string::class.java, id)), *args)
    override fun plural(id: Int, n: Int, vararg args: Any) =
        String.format(Locale.ENGLISH, plurals.getValue(name(R.plurals::class.java, id)).getValue(if (n == 1) "one" else "other"), *args)
}
