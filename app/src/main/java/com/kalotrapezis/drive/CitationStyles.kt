package com.kalotrapezis.drive

/**
 * References › Style: APA 7 (Writer's first), MLA 9 and Chicago author-date (17th) — each document's own, kept as Word
 * keeps it (its sources part's StyleName); in an OpenDocument told by its list's heading and its citations' shape.
 */
internal enum class CiteStyle(val label: String, val heading: String, val wordName: String, val wordXsl: String) {
    APA("APA 7", "References", "APA", "\\APASixthEditionOfficeOnline.xsl"),
    MLA("MLA 9", "Works Cited", "MLA", "\\MLASeventhEditionOfficeOnline.xsl"),
    Chicago("Chicago (author-date)", "References", "Chicago", "\\CHICAGO.XSL");

    fun parenthetical(sources: List<DocSource>, page: String = ""): String = when (this) {
        APA -> Apa.parenthetical(sources, page)
        MLA -> Mla.parenthetical(sources, page)
        Chicago -> ChicagoAD.parenthetical(sources, page)
    }
    fun entry(s: DocSource): List<DocRun> = when (this) { APA -> Apa.entry(s); MLA -> Mla.entry(s); Chicago -> ChicagoAD.entry(s) }
    /** The list's order: by the first author's surname (or the title), then the year — the same in all three. */
    fun sorted(sources: List<DocSource>) = Apa.sorted(sources)

    companion object {
        val HEADINGS = entries.map { it.heading }.toSet()
        /** A Word file's: its sources part's StyleName ("APA", "MLA", "Chicago"…); APA when it says another. */
        fun ofWord(styleName: String?) = entries.firstOrNull { styleName?.startsWith(it.wordName, ignoreCase = true) == true } ?: APA
        /**
         * An OpenDocument's, from what it shows: a list headed "Works Cited" is MLA's; a citation with a year after the
         * name without a comma, Chicago's ("(Smith 2020, 47)"); else APA's.
         */
        fun infer(doc: OfficeDoc): CiteStyle {
            if (doc.paras.any { it.text.trim() == MLA.heading }) return MLA
            val shown = doc.paras.asSequence().flatMap { p -> p.runs.asSequence().filter { it.cite != null && !isField(it.cite) }.map { it.text } }.firstOrNull() ?: return APA
            return when {
                Regex("""\p{L}, (\d{4}|n\.d\.)""").containsMatchIn(shown) -> APA
                Regex("""\p{L} (\d{4}|n\.d\.)""").containsMatchIn(shown) -> Chicago
                else -> MLA
            }
        }
    }
}

/** The pieces the two styles share: names as the list writes them, an ending's full stop, pages with an en dash. */
private object Cite {
    fun full(p: DocSource.Person) = if (p.first.isBlank()) p.last else "${p.first} ${p.last}"
    fun inverted(p: DocSource.Person) = if (p.first.isBlank()) p.last else "${p.last}, ${p.first}"
    fun end(s: String) = if (s.endsWith('.') || s.endsWith('?') || s.endsWith('!')) s else "$s."
    fun pages(p: String) = p.replace('-', '–')
    fun link(s: DocSource) = s.doi.trim().let { if (it.isEmpty()) "" else if (it.startsWith("http")) it else "https://doi.org/$it" }
    fun year(s: DocSource) = s.year.ifBlank { "n.d." }
    /** A title in quotation marks, its full stop inside them (American style, as MLA and Chicago write it). */
    fun quoted(t: String, mark: String = ".") = "“" + (if (t.endsWith('?') || t.endsWith('!')) t else t + mark) + "”"
    fun runs(build: (plain: (String) -> Unit, italic: (String) -> Unit) -> Unit): List<DocRun> {
        val out = ArrayList<DocRun>()
        build({ if (it.isNotEmpty()) out += DocRun(it) }, { if (it.isNotEmpty()) out += DocRun(it, italic = true) })
        return OfficeFormats.tidy(out.mapIndexed { i, r -> if (i == out.lastIndex) r.copy(text = r.text.trimEnd()) else r })
    }
}

/** MLA 9: (Smith 47), the Works Cited list. */
internal object Mla {
    fun citeNames(s: DocSource): String = when (s.authors.size) {
        0 -> "“" + s.title.split(' ').take(4).joinToString(" ") + "”"
        1 -> s.authors[0].last
        2 -> "${s.authors[0].last} and ${s.authors[1].last}"
        else -> "${s.authors[0].last} et al."
    }
    fun parenthetical(sources: List<DocSource>, page: String = ""): String =
        "(" + sources.joinToString("; ") { citeNames(it) } + (if (page.isNotBlank() && sources.size == 1) " ${Cite.pages(page)}" else "") + ")"
    /** Smith, John. / Smith, John, and Ann Jones. / Smith, John, et al. */
    fun authors(people: List<DocSource.Person>) = when (people.size) {
        0 -> ""
        1 -> Cite.inverted(people[0])
        2 -> "${Cite.inverted(people[0])}, and ${Cite.full(people[1])}"
        else -> "${Cite.inverted(people[0])}, et al."
    }
    fun entry(s: DocSource): List<DocRun> = Cite.runs { plain, italic ->
        val who = authors(s.authors)
        if (who.isNotEmpty()) plain(Cite.end(who) + " ")
        val link = Cite.link(s).removePrefix("https://").removePrefix("http://") // MLA leaves out "https://"
        val tail = ArrayList<String>()
        when (s.type) {
            DocSource.Type.Book, DocSource.Type.Report -> {
                italic(s.title); plain(". ")
                if (s.edition.isNotBlank()) tail += "${s.edition} ed."
                tail += listOf(s.publisher, s.year).filter { it.isNotBlank() }
            }
            DocSource.Type.Thesis -> { italic(s.title); plain(". "); tail += listOf(s.year, s.publisher, s.container.ifBlank { "Thesis" }).filter { it.isNotBlank() } }
            else -> {
                plain(Cite.quoted(s.title) + " ")
                if (s.container.isNotBlank()) { italic(s.container); plain(", ") }
                if (s.type == DocSource.Type.Chapter && s.editors.isNotEmpty()) tail += "edited by " + s.editors.joinToString(" and ") { Cite.full(it) }
                if (s.volume.isNotBlank()) tail += "vol. ${s.volume}"
                if (s.issue.isNotBlank()) tail += "no. ${s.issue}"
                if (s.type == DocSource.Type.Chapter && s.publisher.isNotBlank()) tail += s.publisher
                if (s.year.isNotBlank()) tail += s.year
                if (s.pages.isNotBlank()) tail += (if ('-' in s.pages || '–' in s.pages) "pp. " else "p. ") + Cite.pages(s.pages)
            }
        }
        if (link.isNotEmpty()) tail += link
        plain(Cite.end(tail.joinToString(", ")))
    }
}

/** Chicago author-date (17th): (Smith 2020, 47), the reference list. */
internal object ChicagoAD {
    fun citeNames(s: DocSource): String = when (s.authors.size) {
        0 -> "“" + s.title.split(' ').take(4).joinToString(" ") + "”"
        1 -> s.authors[0].last
        2 -> "${s.authors[0].last} and ${s.authors[1].last}"
        3 -> "${s.authors[0].last}, ${s.authors[1].last}, and ${s.authors[2].last}"
        else -> "${s.authors[0].last} et al."
    }
    fun parenthetical(sources: List<DocSource>, page: String = ""): String =
        "(" + sources.joinToString("; ") { "${citeNames(it)} ${Cite.year(it)}" } + (if (page.isNotBlank() && sources.size == 1) ", ${Cite.pages(page)}" else "") + ")"
    /** Smith, John, Ann Jones, and Bo Lee — up to ten; more: the first seven, then "et al." */
    fun authors(people: List<DocSource.Person>): String {
        val names = (if (people.size > 10) people.take(7) else people).mapIndexed { i, p -> if (i == 0) Cite.inverted(p) else Cite.full(p) }
        return when {
            names.isEmpty() -> ""
            people.size > 10 -> names.joinToString(", ") + ", et al."
            names.size == 1 -> names[0]
            names.size == 2 -> "${names[0]}, and ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
        }
    }
    fun entry(s: DocSource): List<DocRun> = Cite.runs { plain, italic ->
        val who = authors(s.authors)
        plain(if (who.isNotEmpty()) "${Cite.end(who)} ${Cite.year(s)}. " else "")
        val link = Cite.link(s)
        when (s.type) {
            DocSource.Type.Book, DocSource.Type.Report -> {
                italic(s.title); plain(". ")
                if (who.isEmpty()) plain("${Cite.year(s)}. ")
                if (s.edition.isNotBlank()) plain("${s.edition} ed. ")
                if (s.publisher.isNotBlank()) plain(Cite.end(s.publisher) + " ")
            }
            DocSource.Type.Article -> {
                plain(Cite.quoted(s.title) + " ")
                if (who.isEmpty()) plain("${Cite.year(s)}. ")
                italic(s.container)
                if (s.volume.isNotBlank()) plain(" ${s.volume}")
                if (s.issue.isNotBlank()) plain(" (${s.issue})")
                plain(if (s.pages.isNotBlank()) ": ${Cite.pages(s.pages)}. " else ". ")
            }
            DocSource.Type.Chapter -> {
                plain(Cite.quoted(s.title) + " In ")
                italic(s.container)
                if (s.editors.isNotEmpty()) plain(", edited by " + s.editors.joinToString(" and ") { Cite.full(it) })
                if (s.pages.isNotBlank()) plain(", ${Cite.pages(s.pages)}")
                plain(". ")
                if (s.publisher.isNotBlank()) plain(Cite.end(s.publisher) + " ")
            }
            DocSource.Type.Thesis -> {
                plain(Cite.quoted(s.title) + " ")
                plain(Cite.end(listOf(s.container.ifBlank { "PhD diss." }, s.publisher).filter { it.isNotBlank() }.joinToString(", ")) + " ")
            }
            DocSource.Type.Web, DocSource.Type.Video -> {
                plain(Cite.quoted(s.title) + " ")
                if (s.container.isNotBlank()) plain(Cite.end(s.container) + " ")
                if (s.type == DocSource.Type.Video) plain("Video. ")
            }
        }
        plain(link)
    }
}
