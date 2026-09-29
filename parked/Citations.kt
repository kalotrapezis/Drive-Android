package com.kalotrapezis.drive

/**
 * References › Citations and the reference list (asked 2026-09-27: "bibliography / citations, APA etc."; APA 7 first).
 * A source is what a citation points at; its [tag] is Word's (Smi20) and LibreOffice's identifier.
 */
internal data class DocSource(
    val tag: String,
    val type: Type = Type.Book,
    val authors: List<Person> = emptyList(), // in order; an organisation as a person with only a last name
    val year: String = "", // "" → n.d.
    val title: String = "",
    val container: String = "", // the journal, the website, the book a chapter is in
    val publisher: String = "",
    val volume: String = "",
    val issue: String = "",
    val pages: String = "", // "12–19"
    val doi: String = "", // or a URL
    val edition: String = "",
    val editors: List<Person> = emptyList(), // a chapter's book's editors
) {
    enum class Type(val label: String) { Book("Book"), Article("Journal article"), Web("Web page"), Chapter("Book chapter") }
    data class Person(val last: String, val first: String = "") {
        /** "Κ. Μ." / "J. R." from the given names, as APA writes them. */
        val initials get() = first.split(' ', '-').filter { it.isNotBlank() }.joinToString(" ") { it.take(1).uppercase() + "." }
    }
}

/** APA 7: in-text citations and reference-list entries. Italics come back as runs. */
internal object Apa {
    private fun yearOf(s: DocSource) = s.year.ifBlank { "n.d." }

    /** The names in a citation: one, two with "&", three or more "et al." (APA 7); an organisation by its name. */
    fun citeNames(s: DocSource): String = when (s.authors.size) {
        0 -> s.title.split(' ').take(4).joinToString(" ").ifBlank { "Anonymous" }
        1 -> s.authors[0].last
        2 -> "${s.authors[0].last} & ${s.authors[1].last}"
        else -> "${s.authors[0].last} et al."
    }

    /** Parenthetical: (Smith, 2020), (Smith & Jones, 2020; Brown et al., 2019), with pages: (Smith, 2020, p. 12). */
    fun parenthetical(sources: List<DocSource>, page: String = ""): String =
        "(" + sources.joinToString("; ") { "${citeNames(it)}, ${yearOf(it)}" } + (if (page.isNotBlank() && sources.size == 1) ", ${pageLabel(page)}" else "") + ")"
    /** Narrative: Smith (2020). */
    fun narrative(s: DocSource, page: String = "") = "${citeNames(s)} (${yearOf(s)}${if (page.isNotBlank()) ", ${pageLabel(page)}" else ""})"
    private fun pageLabel(p: String) = if (p.contains('-') || p.contains('–')) "pp. ${p.replace('-', '–')}" else "p. $p"

    /** Authors as the reference list writes them: Smith, J. R., Jones, A., & Brown, K. (up to 20; then the first 19, …, the last). */
    fun authorList(people: List<DocSource.Person>): String {
        fun one(p: DocSource.Person) = if (p.first.isBlank()) p.last else "${p.last}, ${p.initials}"
        val names = people.map(::one)
        return when {
            names.isEmpty() -> ""
            names.size == 1 -> names[0]
            names.size == 2 -> "${names[0]}, & ${names[1]}"
            names.size <= 20 -> names.dropLast(1).joinToString(", ") + ", & " + names.last()
            else -> names.take(19).joinToString(", ") + ", . . . " + names.last()
        }
    }
    private fun end(s: String) = if (s.endsWith('.') || s.endsWith('?') || s.endsWith('!')) s else "$s."

    /** One reference-list entry, as runs (italic where APA italicises). */
    fun entry(s: DocSource): List<DocRun> {
        val out = ArrayList<DocRun>()
        fun plain(t: String) { if (t.isNotEmpty()) out += DocRun(t) }
        fun italic(t: String) { if (t.isNotEmpty()) out += DocRun(t, italic = true) }
        val who = authorList(s.authors)
        val date = "(${yearOf(s)})."
        // With no author, the title takes the author's place (APA 7).
        if (who.isNotEmpty()) plain("${end(who)} $date ") else if (s.type == DocSource.Type.Article || s.type == DocSource.Type.Chapter) plain("${end(s.title)} $date ")
        val link = s.doi.trim().let { if (it.isEmpty()) "" else if (it.startsWith("http")) it else "https://doi.org/$it" }
        when (s.type) {
            DocSource.Type.Book -> {
                if (who.isNotEmpty()) { italic(s.title); if (s.edition.isNotBlank()) plain(" (${s.edition} ed.)"); plain(". ") }
                else { italic(s.title); if (s.edition.isNotBlank()) plain(" (${s.edition} ed.)"); plain(". $date ") }
                if (s.publisher.isNotBlank()) plain(end(s.publisher) + (if (link.isNotEmpty()) " " else ""))
            }
            DocSource.Type.Article -> {
                if (who.isNotEmpty()) plain(end(s.title) + " ")
                italic(s.container.ifBlank { "" }.let { if (s.volume.isNotBlank()) "$it, ${s.volume}" else it })
                if (s.issue.isNotBlank()) plain("(${s.issue})")
                if (s.pages.isNotBlank()) plain(", ${s.pages.replace('-', '–')}")
                plain(if (link.isNotEmpty()) ". " else ".")
            }
            DocSource.Type.Chapter -> {
                if (who.isNotEmpty()) plain(end(s.title) + " ")
                val eds = s.editors.map { if (it.first.isBlank()) it.last else "${it.initials} ${it.last}" }
                plain("In ")
                if (eds.isNotEmpty()) plain(when (eds.size) { 1 -> eds[0]; 2 -> "${eds[0]} & ${eds[1]}"; else -> eds.dropLast(1).joinToString(", ") + ", & " + eds.last() } + " (Ed${if (eds.size > 1) "s" else ""}.), ")
                italic(s.container)
                if (s.pages.isNotBlank()) plain(" (pp. ${s.pages.replace('-', '–')})")
                plain(". ")
                if (s.publisher.isNotBlank()) plain(end(s.publisher) + (if (link.isNotEmpty()) " " else ""))
            }
            DocSource.Type.Web -> {
                if (who.isNotEmpty()) { italic(s.title); plain(". ") } else { italic(s.title); plain(". $date ") }
                if (s.container.isNotBlank() && s.container != s.authors.firstOrNull()?.last) plain(end(s.container) + " ")
            }
        }
        plain(link)
        return OfficeFormats.tidy(out.mapIndexed { i, r -> if (i == out.lastIndex) r.copy(text = r.text.trimEnd()) else r })
    }

    /** The reference list's order: by the first author's surname (or the title), then the year. */
    fun sorted(sources: List<DocSource>) = sources.sortedWith(compareBy({ (it.authors.firstOrNull()?.last ?: it.title).lowercase() }, { it.year }))

    /** A tag no other source has: Smi20, Smi201, … (Word's way). */
    fun newTag(s: DocSource, taken: Collection<String>): String {
        val base = (s.authors.firstOrNull()?.last ?: s.title).filter { it.isLetter() }.take(3).ifEmpty { "Src" } + s.year.takeLast(2)
        return generateSequence(0) { it + 1 }.map { if (it == 0) base else "$base$it" }.first { it !in taken }
    }
}
