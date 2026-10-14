# Tetra sources file (`*.sources.json`)

The references found while researching, written once and imported into any draft with **Writer › References ›
Sources › Import**. After that, **References list** (or **Update references**) gives the APA 7 list in one click, in
every draft. Made for the user's way of working (2026-10-14): find papers with Consensus, keep the PDFs on the PC,
write — an AI writes this file during the research.

Save it anywhere in the Tetra folder (it syncs like any file), e.g. next to the paper: `Έρευνα/604.sources.json`.

## Shape

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
      "pdf": "Έρευνα/Paisley 2022.pdf"
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
      "authors": ["Bandura, Albert"],
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

| Field | Meaning |
|---|---|
| `type` | `article`, `book`, `chapter` or `web` (also accepted: `report`, `thesis` → book; missing → article when there is a `journal`, else book) |
| `authors` | in order; each `{ "last", "first" }` (given names in full or initials), `{ "org" }` for an organisation, or a `"Last, First"` string |
| `year` | `"2022"`; leave empty for n.d. |
| `title` | the work's title, in sentence case as APA writes it — **required** (a source without one is skipped and named) |
| `journal` / `website` / `book` | where it appeared: the journal, the website's name, the book a chapter is in |
| `volume`, `issue`, `pages` | as printed; `12-30` becomes `12–30` |
| `doi` | `10.xxxx/…` (a `https://doi.org/` or `doi:` in front is taken off; APA's list shows it as `https://doi.org/10.xxxx/…`) — or `url` for a web page |
| `publisher`, `edition`, `editors` | books and chapters |
| `pdf` | where the paper is, for the user; Writer ignores it today |

Unknown fields are ignored; `format`/`version` are there for later. A bare list (`[ {…}, {…} ]`) is read too.
Importing again is safe: a source already in the document (same DOI, or same title and year) is not added twice.
