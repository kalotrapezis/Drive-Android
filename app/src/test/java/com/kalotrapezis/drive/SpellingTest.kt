package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** LibreOffice's dictionaries through Lucene's Hunspell: Greek and English, right and wrong words. */
class SpellingTest {
    private fun speller(name: String) = File("src/main/assets/dictionaries").let { d ->
        File(d, "$name.aff").inputStream().use { a -> File(d, "$name.dic").inputStream().use { b -> Dictionaries.of(a, b, name.take(2)) } } }

    @Test fun `Greek and English words are checked in their own language`() {
        val el = speller("el_GR"); val en = speller("en_US")
        for (w in listOf("Καλημέρα", "κόσμε", "εκπαιδευτική", "Ψυχολογίας", "ΣΧΟΛΗ")) assertEquals(w, null, Dictionaries.misspelled(el, w))
        for (w in listOf("The", "quick", "don't", "formatting", "Sample")) assertEquals(w, null, Dictionaries.misspelled(en, w))
        assert("the" in Dictionaries.misspelled(en, "teh").orEmpty()) { Dictionaries.misspelled(en, "teh").toString() }
        assert(Dictionaries.misspelled(el, "καλημρεα") != null)
        assert("καλημέρα" in Dictionaries.misspelled(el, "καλημερα").orEmpty()) { Dictionaries.misspelled(el, "καλημερα").toString() } // the accent missing
        for (w in listOf("rewritten", "unhappiness", "cities", "walked", "Paris")) assertEquals(w, null, Dictionaries.misspelled(en, w)) // affixes and a name
        assert(Dictionaries.misspelled(en, "paris") != null)
        assertEquals("el", WriterSpelling.langOf("Ψυχολογία")); assertEquals("en", WriterSpelling.langOf("Psychology"))
    }
}
