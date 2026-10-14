---
name: tetra-sources
description: Make a Tetra sources file (.sources.json) of the references for a paper, for import into Tetra's Writer (References › Sources › Import), which then writes a correct APA 7 reference list in one click in every draft. Use whenever the user researches for a paper, essay or assignment with you, asks for "the sources", "the references", "a sources file", "the JSON for Tetra", or shares papers/PDFs/Consensus results and wants them cited in Tetra.
---

# Tetra sources file

The user writes papers in **Tetra** (their own app: Writer is its Word editor, on Android and soon the PC). They find
papers with **Consensus**, keep the **PDFs locally**, and do **not** use Zotero or Mendeley. When you research with them,
end with a **Tetra sources file**: every reference you used or recommended, in this exact format. They import it into
any draft (Writer › References › Sources › Import sources file, or Paste) and the APA 7 list is right every time.

## What to deliver

A single JSON block (and, when you can make files, a file named `<topic>.sources.json`):

```json
{
  "format": "tetra-sources",
  "version": 1,
  "sources": [
    {
      "type": "article",
      "authors": [{ "last": "Paisley", "first": "Courtney A." }, { "last": "Tomeny", "first": "Theodore S." }],
      "year": "2022",
      "title": "A systematic review of the research-to-practice gap in autism",
      "journal": "Research in Autism Spectrum Disorders",
      "volume": "96",
      "issue": "",
      "pages": "102000",
      "doi": "10.1016/j.rasd.2022.102000",
      "pdf": "Paisley 2022.pdf"
    },
    {
      "type": "book",
      "authors": [{ "org": "American Psychiatric Association" }],
      "year": "2013",
      "title": "Diagnostic and statistical manual of mental disorders",
      "edition": "5",
      "publisher": "American Psychiatric Publishing"
    },
    {
      "type": "chapter",
      "authors": [{ "last": "Bandura", "first": "Albert" }],
      "year": "1977",
      "title": "Social learning theory",
      "book": "Theories of learning",
      "editors": [{ "last": "Smith", "first": "J." }],
      "pages": "12-30",
      "publisher": "Prentice Hall"
    },
    {
      "type": "web",
      "authors": [{ "org": "World Health Organization" }],
      "year": "2023",
      "title": "Autism",
      "website": "WHO",
      "url": "https://www.who.int/news-room/fact-sheets/detail/autism-spectrum-disorders"
    }
  ]
}
```

## Fields

| Field | Rule |
|---|---|
| `type` | `article`, `book`, `chapter`, `web`, `report` (+ `number`, `publisher`), `thesis` (+ `kind`, `university`) or `video` (+ `platform`) |
| `authors` | in the paper's order; `{ "last", "first" }` with the given names as printed (full or initials), `{ "org" }` for an organisation |
| `year` | `"2022"`; `""` when there is none (APA: n.d.) |
| `title` | **required**; sentence case as APA wants it (capitalise only the first word, the first word after a colon, and proper nouns), no final full stop |
| `journal` | articles: the journal's full name in title case |
| `website` / `book` | web pages: the site's name; chapters: the book's title |
| `volume`, `issue`, `pages` | as printed (`12-30` is fine; an article number like `102000` goes in `pages`) |
| `doi` | the bare DOI `10.xxxx/…` — always give it for articles that have one; `url` instead only for web pages without a DOI |
| `publisher`, `edition`, `editors` | books and chapters (`edition` as a number: `"5"`) |
| `pdf` | the file name of the PDF the user has, when known — helps them find it |

## Rules

- **Only real, checked references.** Every entry must be a work you found and verified (Consensus, the PDF itself, the
  publisher's page, doi.org). Never invent a DOI, page range or author. When a detail is unknown, leave the field `""`
  and say which ones are missing after the JSON.
- One entry per work; the same work twice (DOI, or title + year) is merged by Tetra anyway — do not rely on it.
- Greek works: keep the Greek title, authors and journal as printed (Greek letters), the same fields.
- Prefer the published version over a preprint; for a preprint, `journal` is the server (e.g. `PsyArXiv`) and the DOI its own.
- Valid JSON only: double quotes, no comments, no trailing commas.
- After the JSON, one short line: how many sources, and anything left empty. The user then pastes or imports it in Tetra.

The full specification lives in the Tetra repository: `Drive-Android/TETRA_SOURCES_FORMAT.md` (this skill and that file
change together).
