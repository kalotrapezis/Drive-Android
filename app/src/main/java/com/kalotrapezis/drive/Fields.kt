package com.kalotrapezis.drive

/**
 * Word's fields Writer keeps (a date, a reference, a sequence number: [FIELD_CITE]) brought up to date — what Word's
 * F9 does. Today the numbers: each SEQ field ("Figure 3", "Table 2") counted again in document order, so deleting a
 * figure renumbers the ones after it (asked 2026-10-13, Phase 2: captions and cross-references).
 */
internal object Fields {
    /** The instruction of a kept field ("SEQ Figure \* ARABIC"), or null for a run that is none. */
    fun instr(r: DocRun): String? = r.cite?.takeIf { isField(it) }?.drop(1)?.trim()

    /** [doc] with every SEQ field's shown number counted again. */
    // shortcut: body paragraphs only (not inside tables, notes, headers); SEQ's \s (restart at a heading level) is not followed.
    fun update(doc: OfficeDoc): OfficeDoc {
        val counts = HashMap<String, Int>()
        val paras = doc.paras.map { p ->
            if (p.runs.none { instr(it)?.startsWith("SEQ ", ignoreCase = true) == true }) return@map p
            val out = ArrayList<DocRun>()
            var k = 0
            while (k < p.runs.size) {
                val r = p.runs[k]
                val instr = instr(r)
                if (instr == null || !instr.startsWith("SEQ ", ignoreCase = true)) { out += r; k++; continue }
                var e = k + 1
                while (e < p.runs.size && p.runs[e].cite == r.cite && p.runs[e].image == null) e++ // one field's letters
                val words = Regex("""\\\S|"[^"]*"|\S+""").findAll(instr).map { it.value }.toList()
                val name = words.getOrNull(1)?.uppercase() ?: ""
                fun switch(s: String) = words.indexOfFirst { it.equals(s, ignoreCase = s != "\\*") }
                val n = when {
                    switch("\\r") >= 0 -> words.getOrNull(switch("\\r") + 1)?.toIntOrNull() ?: 1
                    switch("\\c") >= 0 -> counts[name] ?: 0
                    else -> (counts[name] ?: 0) + 1
                }
                counts[name] = n
                val format = words.getOrNull(switch("\\*") + 1).takeIf { switch("\\*") >= 0 }
                val shown = if (switch("\\h") >= 0) "" else number(n, format)
                out += r.copy(text = shown)
                k = e
            }
            p.copy(runs = out)
        }
        return doc.copy(paras = paras)
    }

    /** A number as a field's \* switch asks: ARABIC (1), ROMAN (IV), roman (iv), ALPHABETIC (D), alphabetic (d). */
    fun number(n: Int, format: String?): String = when (format) {
        "ROMAN" -> roman(n)
        "roman" -> roman(n).lowercase()
        "ALPHABETIC" -> letters(n)
        "alphabetic" -> letters(n).lowercase()
        else -> n.toString()
    }

    private fun letters(n: Int): String = if (n <= 0) "" else ('A' + (n - 1) % 26).toString().repeat((n - 1) / 26 + 1)
    private fun roman(n: Int): String {
        var v = n; val out = StringBuilder()
        for ((value, s) in listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"))
            while (v >= value) { out.append(s); v -= value }
        return out.toString()
    }
}
