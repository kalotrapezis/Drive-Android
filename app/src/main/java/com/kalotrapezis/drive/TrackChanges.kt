package com.kalotrapezis.drive

/*
 * Review › Track changes (asked 2026-09-30; a professor's corrected .docx was copy-only): the changes someone made
 * with Track Changes on, shown, and each accepted or rejected — or all of them. Word files. What changed:
 *  - letters inserted or deleted ([DocRun.change]);
 *  - letters' formatting (Word's rPrChange, carried raw in [DocRun.font]: the formatting before it inside);
 *  - a paragraph's formatting (pPrChange, a kept paragraph property);
 *  - a paragraph mark inserted or deleted (w:ins / w:del in the paragraph mark's rPr): two paragraphs split or joined;
 *  - in tables (all at once only): rows inserted or deleted, table / row / cell formatting.
 */
internal object TrackChanges {
    enum class Kind { Inserted, Deleted, Formatted, ParagraphFormatted, ParagraphInserted, ParagraphDeleted }

    /** One change, where it is: letters [from]..[to] of top-level paragraph [para] (a paragraph mark's: its end). */
    data class Change(val para: Int, val from: Int, val to: Int, val kind: Kind, val author: String, val date: String?) {
        val label get() = when (kind) {
            Kind.Inserted -> "Inserted"; Kind.Deleted -> "Deleted"; Kind.Formatted -> "Formatted"
            Kind.ParagraphFormatted -> "Paragraph formatted"; Kind.ParagraphInserted -> "Paragraph inserted"; Kind.ParagraphDeleted -> "Paragraphs joined"
        }
    }

    /**
     * The colour an author's changes show in (0xRRGGBB), as Word gives each reviewer one. ponytail: by the name's hash,
     * so two reviewers can share a colour; number them in order of appearance if that ever confuses.
     */
    fun color(author: String): Int = AUTHOR_COLORS[Math.floorMod(author.hashCode(), AUTHOR_COLORS.size)]
    private val AUTHOR_COLORS = intArrayOf(0xC00000, 0x1F5FBF, 0x2E7D32, 0x7B1FA2, 0xD35400, 0x00838F)

    private val RUN_CHANGE = Regex("""<w:rPrChange\b[^>]*?(?:/>|>([\s\S]*?)</w:rPrChange>)""")
    private val MARK = Regex("""<w:(ins|del|moveTo|moveFrom)\b[^>]*?(?:/>|>\s*</w:\1>)""")
    private val ANY_CHANGE = Regex("""<w:(\w+)Change\b[^>]*?(?:/>|>[\s\S]*?</w:\1Change>)""")
    private fun author(raw: String) = Regex("""w:author="([^"]*)"""").find(raw)?.groupValues?.get(1)?.let(::unesc) ?: ""
    private fun date(raw: String) = Regex("""w:date="([^"]*)"""").find(raw)?.groupValues?.get(1)
    private fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun markOf(p: DocPara): MatchResult? = p.keep?.parts?.firstOrNull { it.first == "rPr" }?.second?.let { MARK.find(it) }
    private fun paraChange(p: DocPara): String? = p.keep?.parts?.firstOrNull { it.first == "pPrChange" }?.second

    /** Every change in the body's paragraphs, in reading order (tables' insides are not listed; [all] reaches them). */
    fun list(doc: OfficeDoc): List<Change> = doc.paras.flatMapIndexed { i, p ->
        val out = ArrayList<Change>()
        paraChange(p)?.let { out += Change(i, 0, p.text.length, Kind.ParagraphFormatted, author(it), date(it)) }
        var at = 0
        var open: Change? = null; var openFormat: Change? = null
        for (r in p.runs) {
            val end = at + r.text.length
            val c = r.change
            open = if (c != null && open != null && open.to == at && open.kind == (if (c.deleted) Kind.Deleted else Kind.Inserted) && open.author == c.author && open.date == c.date) open.copy(to = end)
                else { open?.let { out += it }; c?.let { Change(i, at, end, if (it.deleted) Kind.Deleted else Kind.Inserted, it.author, it.date) } }
            val f = r.font?.let { RUN_CHANGE.find(it)?.value }
            openFormat = if (f != null && openFormat != null && openFormat.to == at && openFormat.author == author(f)) openFormat.copy(to = end)
                else { openFormat?.let { out += it }; f?.let { Change(i, at, end, Kind.Formatted, author(it), date(it)) } }
            at = end
        }
        open?.let { out += it }; openFormat?.let { out += it }
        markOf(p)?.let { m -> out += Change(i, at, at, if (m.groupValues[1].let { it == "ins" || it == "moveTo" }) Kind.ParagraphInserted else Kind.ParagraphDeleted, author(m.value), date(m.value)) }
        out.sortedBy { it.from }
    }

    fun any(doc: OfficeDoc): Boolean {
        fun walk(ps: List<DocPara>): Boolean = ps.any { p ->
            paraChange(p) != null || markOf(p) != null || p.runs.any { r -> r.change != null || r.font?.contains("<w:rPrChange") == true } ||
                p.table?.let { t -> ANY_CHANGE.containsMatchIn(t.props + t.grid) || t.rows.any { row -> MARK.containsMatchIn(row.props) || ANY_CHANGE.containsMatchIn(row.props) ||
                    row.cells.any { c -> ANY_CHANGE.containsMatchIn(c.props) || walk(c.paras) } } } == true
        }
        return walk(doc.paras)
    }

    /** [changes] accepted (or rejected), the rest left as they are. */
    fun decide(doc: OfficeDoc, changes: List<Change>, accept: Boolean): OfficeDoc {
        val byPara = changes.groupBy { it.para }
        return doc.copy(paras = settle(doc.paras.mapIndexed { i, p ->
            val mine = byPara[i] ?: return@mapIndexed Settled(p, false)
            var q = p
            if (mine.any { it.kind == Kind.ParagraphFormatted }) q = paragraphFormat(q, accept)
            // Letters: only those inside the chosen changes, of their kind.
            var at = 0
            val runs = ArrayList<DocRun>()
            for (r in q.runs) {
                val cuts = sortedSetOf(0, r.text.length)
                mine.forEach { c -> if (c.kind in LETTERS) { cuts += (c.from - at).coerceIn(0, r.text.length); cuts += (c.to - at).coerceIn(0, r.text.length) } }
                cuts.toList().zipWithNext().forEach { (a, z) ->
                    var piece: DocRun? = r.copy(text = r.text.substring(a, z))
                    val kinds = mine.filter { it.from <= at + a && at + z <= it.to && it.from < it.to }.map { it.kind }
                    if (Kind.Formatted in kinds) piece = format(piece!!, accept)
                    if (Kind.Inserted in kinds && piece!!.change?.deleted == false || Kind.Deleted in kinds && piece!!.change?.deleted == true) piece = letters(piece!!, accept)
                    piece?.let { runs += it }
                }
                at += r.text.length
            }
            q = q.copy(runs = OfficeFormats.tidy(runs))
            mark(q, mine.any { it.kind == Kind.ParagraphInserted || it.kind == Kind.ParagraphDeleted }, accept)
        }))
    }

    /** Every change accepted (or rejected), tables' too. */
    fun all(doc: OfficeDoc, accept: Boolean): OfficeDoc {
        fun walk(ps: List<DocPara>): List<DocPara> = settle(ps.map { p ->
            p.table?.let { t -> return@map Settled(p.copy(table = t.copy(props = props(t.props, accept), grid = props(t.grid, accept), rows = t.rows.mapNotNull { row ->
                val m = MARK.find(row.props)?.groupValues?.get(1)
                // A row inserted, rejected — or deleted, accepted — goes.
                if (m != null && (m == "del" || m == "moveFrom") == accept) null
                else row.copy(props = props(row.props.let { if (m != null) MARK.replace(it, "") else it }, accept), cells = row.cells.map { c -> c.copy(props = props(c.props, accept), paras = walk(c.paras)) })
            }.ifEmpty { return@map Settled(null, false) })), false) }
            var q = if (paraChange(p) != null) paragraphFormat(p, accept) else p
            q = q.copy(runs = OfficeFormats.tidy(q.runs.mapNotNull { r -> (if (r.font?.contains("<w:rPrChange") == true) format(r, accept) else r).let { x -> if (x.change != null) letters(x, accept) else x } }))
            mark(q, true, accept)
        })
        return doc.copy(paras = walk(doc.paras))
    }

    private val LETTERS = setOf(Kind.Inserted, Kind.Deleted, Kind.Formatted)

    /** Inserted or deleted letters decided: kept (their mark taken off) or gone. */
    private fun letters(r: DocRun, accept: Boolean): DocRun? { val c = r.change ?: return r; return if (c.deleted == accept) null else r.copy(change = null) }

    /** Letters' formatting change decided: the new kept, or the old back (Word's formatting before it). */
    private fun format(r: DocRun, accept: Boolean): DocRun {
        val font = r.font ?: return r
        val m = RUN_CHANGE.find(font) ?: return r
        if (accept) return r.copy(font = font.replace(m.value, "").ifEmpty { null })
        val old = m.groupValues[1].takeIf { it.isNotBlank() }?.let { OfficeFormats.docxLook(it) } ?: DocRun("")
        return old.copy(text = r.text, link = r.link, image = r.image, comments = r.comments, cite = r.cite, change = r.change)
    }

    /**
     * A paragraph's formatting change decided: accepted, its record goes; rejected, its properties before it come back
     * — spacing, indents, alignment, borders… ponytail: its style and list stay as they are now (a style changed under
     * track changes is rare); read them from the old pPr if that is ever wanted.
     */
    private fun paragraphFormat(p: DocPara, accept: Boolean): DocPara {
        val keep = p.keep ?: return p
        val raw = paraChange(p) ?: return p
        val rest = keep.parts.filter { it.first != "pPrChange" }
        if (accept) return p.copy(keep = keep.copy(parts = rest).takeIf { !it.isEmpty })
        val old = Regex("""<w:pPr\b[^>]*>([\s\S]*)</w:pPr>""").find(raw)?.groupValues?.get(1).orEmpty()
        val oldParts = Regex("""<w:(\w+)\b[^>]*?(?:/>|>[\s\S]*?</w:\1>)""").findAll(old).map { it.groupValues[1] to it.value }.toList()
        val align = oldParts.firstOrNull { it.first == "jc" }?.second?.let { Regex("""w:val="([^"]*)"""").find(it)?.groupValues?.get(1) }.let {
            when (it) { "center" -> DocAlign.Center; "right", "end" -> DocAlign.End; "both", "distribute" -> DocAlign.Justify; else -> DocAlign.Start } }
        val mine = setOf("pStyle", "numPr", "jc", "rPr", "sectPr")
        val parts = rest.filter { it.first in mine } + oldParts.filter { it.first !in mine }
        return p.copy(align = align, keep = keep.copy(parts = parts.sortedBy { PPR_ORDER.indexOf(it.first).let { i -> if (i < 0) PPR_ORDER.indexOf("rPr") else i } }).takeIf { !it.isEmpty })
    }

    /** A paragraph, and whether it joins the one after it (its mark deleted, accepted — or inserted, rejected). */
    private class Settled(val p: DocPara?, val join: Boolean)

    private fun mark(p: DocPara, decide: Boolean, accept: Boolean): Settled {
        val m = markOf(p) ?: return Settled(p, false)
        if (!decide) return Settled(p, false)
        val deleted = m.groupValues[1].let { it == "del" || it == "moveFrom" }
        val stripped = p.keep!!.let { k -> k.copy(parts = k.parts.mapNotNull { (n, x) -> if (n != "rPr") n to x else x.replace(m.value, "").takeIf { it != "<w:rPr></w:rPr>" }?.let { n to it } }) }
        return Settled(p.copy(keep = stripped.takeIf { !it.isEmpty }), deleted == accept)
    }

    /** Joined paragraphs made one: the letters go on into the next paragraph, which keeps its own formatting (Word's). */
    private fun settle(ps: List<Settled>): List<DocPara> {
        val out = ArrayList<DocPara>()
        var carry: List<DocRun>? = null
        ps.forEachIndexed { k, s ->
            val p = s.p ?: return@forEachIndexed
            val here = carry?.let { c -> if (p.table == null) p.copy(runs = OfficeFormats.tidy(c + p.runs)) else { out += DocPara(c); p } } ?: p
            carry = null
            if (s.join && here.table == null && ps.drop(k + 1).firstOrNull { it.p != null }?.p?.table == null && k + 1 < ps.size) carry = here.runs else out += here
        }
        carry?.let { out += DocPara(it) }
        return out.ifEmpty { listOf(DocPara(emptyList())) }
    }

    /** A table's, row's or cell's properties: a change's record taken out (accepted), or its properties before it back. */
    private fun props(raw: String, accept: Boolean): String = if (accept) ANY_CHANGE.replace(raw, "") else
        Regex("""<w:(\w+)\b([^>]*)>([\s\S]*?)<w:\1Change\b[^>]*>\s*(?:<w:\1\b[^>]*?/>|<w:\1\b[^>]*>([\s\S]*?)</w:\1>)\s*</w:\1Change>([\s\S]*?)</w:\1>""").replace(raw) { m ->
            "<w:${m.groupValues[1]}${m.groupValues[2]}>${m.groupValues[4]}</w:${m.groupValues[1]}>"
        }.let { ANY_CHANGE.replace(it, "") }
}
