# Office — the features, one by one

What Microsoft Office's phone apps do (researched 27 September 2026: Word, Excel and PowerPoint for Android), as a
checklist for Tetra's Office. Tetra is local — nothing that needs Microsoft's cloud (live co-editing, public links,
Copilot). Each item: **done**, **next**, or later. Build them in order, one at a time; each one is tested on the
emulator and on the phone, and a file saved with it opens right in Word / LibreOffice.

**15 Oct: none of the user's 318 documents is copy-only any more** (fields over paragraphs kept, the last cause).

Rule for every feature: a file part Writer can show *and* save is no longer "copy-only" — each item below that adds
such a part also moves it out of `OfficeDoc.blocking`.

## Word (Writer)

**Tablet and phone (the user, 27 Sep 2026):** the work is not split — every feature goes on both. The phone is good
too; it just can never beat a big screen, so on the phone Claude decides how each feature fits (the user leaves that
to Claude). A document opens **view only** (as LibreOffice does), with **Edit** at the top.

### FIRST — proper pages, viewing before editing (asked 2026-09-27)
- [x] A document opens **view only**, as pages (Print layout), like LibreOffice: the file's page size and margins, a
      gap between pages, scroll and pinch-zoom, "Page 3 of 12"; **Edit** at the top switches to the editor and back
- [x] The pages show what the file sets: line spacing, space before/after, indents, page breaks, pictures in place,
      fonts as close as the phone has them
- [x] **Tablet view** (asked 2026-09-27): the text must not stretch across the whole screen — a page of its real width,
      centred, zoomable (landscape tablet: one page, or two side by side); the editor too keeps the page's width
- [x] Tablet ribbon: a tab's tools in one line, close together and centred (no pages, which spread them apart)
- [x] Then: header and footer, page numbers, tables
- [x] Built on the Export PDF layout (OfficePdf.kt), so the PDF gets every improvement too; checked page by page against
      LibreOffice on the user's documents

**Order (agreed 29 Sep 2026, from Word and OnlyOffice's ribbons the user sent):** finish Home and Insert first, then
Layout, References, Draw, Design; Review and View after. Word's "Format" is not a tab of its own: it appears when a
picture/shape/table is selected (Tetra's Picture and Table tabs are that). UI text English only (languages later,
with string files).

**After the tabs (asked 2026-09-29):** an icon for every ribbon button (they help remember where each one is) —
one pass once the tabs' features are done.

### Top bar
- [x] Back, name (• unsaved), Undo, Redo, Save
- [x] Phone: hides when scrolling down the document, back when scrolling up (as Chrome/Docs); the ribbon stays while
      editing; the tablet keeps it
- [x] Undo/redo for Writer's own changes too (style, spacing, picture, table, header…), not only typing
- [x] Find and Replace — **improve: the match is not highlighted while the Find box has the keyboard; match case / whole words**
- [x] Share the file (as last saved) — as PDF with Export
- [x] Mobile view (text reflows to the phone) / Print layout (the page as it prints) — View tab; the phone keeps Print layout (Mobile view off, the user's choice)
- [x] File: Save as (beside the file, going on as the new one), Rename (draft and versions move with it), Print (Android printing of the PDF pages) — 14 Oct; Properties is About this file
- [x] Word and character counter (1 Oct): tablet — in the top bar, of the selection when there is one ("12 of 1,234
      words"), a tap opens About; phone — File › About this file (words, characters with and without spaces, and the
      selection's). Tables' words count, pictures do not; the phone's top bar too, under the name: "12 of 1,234 words" (15 Oct)

### File
- [x] Save over the file when nothing is lost; else a copy beside it
- [x] New documents: .docx (or .odt); Save as with a name, in Tetra/Documents
- [x] Unsaved changes kept as a draft; leaving asks Save / Don't save
- [x] Versions (File): the file as it was before each of the last 3 saves; Restore keeps the current one as a version too
- [x] Export PDF (File): A4, 2 cm margins, beside the document — laid out by Android, not by Word
- [x] AutoSave (on by default in Word): save by itself after a pause, for files that are not copy-only — File › AutoSave and Settings › Office (14 Oct)
- [x] Open .doc / .rtf (read; save as .docx) — Writer's own readers (LegacyDocs.kt): text, letters' looks, alignment, spacing, indents, headings, numbered lists, links, tables, page breaks (an .rtf's PNG/JPEG pictures); Save writes name.docx beside it (15 Oct, evening) — [ ] a .doc's pictures, headers and footers, footnotes

### Home
- [x] Bold, Italic, Underline, Strikethrough
- [x] Text size −/+, text colour, highlight
- [x] Styles: Normal, Heading 1–3 (**the library fixes heading sizes (H1 = 2× text); show the document's own — workaround needed**)
- [x] Bullets, numbering, indent −/+
- [x] Alignment: left, centre, right, justify
- [x] Fonts kept letter by letter on save (the editor shows its own font) ; Home › Font: the chosen letters or the word at the cursor in a font — the document's own, or Calibri, Cambria, Times New Roman, Arial, Courier New (drawn as Word draws them), each shown in its face (14 Oct)
- [x] Superscript, subscript
- [x] Clear formatting (the eraser after Highlight: the chosen letters, or the paragraph at the cursor, back to their style's look — LibreOffice's "clear direct formatting"; the style stays; the letters' font is kept)
- [x] Borders (Home, after line spacing): bottom, top, left, right, none, all, outside, inside horizontal; horizontal
      line (an empty paragraph with a line under it). Word's pBdr / ODT fo:border (+ join-border); drawn on the pages
      (and PDF) as Word groups them, outlined in the editor; (16 Oct) a centred Borders panel with a preview — the sides, their line (solid, double, dotted,
      dashed), thickness and colour (Word's w:val/sz/color, ODT's fo:border; LibreOffice reads them); the Paragraph panel has
      the preview too; on a phone the ribbon is a solid bar, as Tables' and Slides'
- [x] Change case (Aa beside the eraser: Sentence case, lowercase, UPPERCASE, Capitalise Each Word; the chosen letters or the word at the cursor; looks kept; Greek capitals without accents, final ς)
- [x] Multilevel list — Home › Multilevel: 1. 2. 3. / 1. a. i. / 1. 1.1. 1.1.1.; "2.1." shown in Edit as on the pages (14 Oct)
- Not on the ribbon: the font name (the space is needed; a style's page sets fonts)
- [x] Paragraph formatting kept: spacing, indents, tab stops, borders, page break before, styles such as Title/Quote, one font per paragraph, the file's own lists — carried through edits (a new paragraph takes the one before it), checked pixel-identical in LibreOffice on 6 of his files
- [x] Paragraph formatting *shown* on the page and in the editor (the space before/after each paragraph drawn by Writer's own look over the library's) and *changed* from the ribbon (Home's ⋯ page, line spacing buttons)
- [x] Line break inside a paragraph (Shift+Enter; Insert › Line break) — kept in .docx and .odt
- [x] Format painter (copy formatting) — Home, and Copy/Paste formatting in the Selection tab
- [x] More styles: Title, Subtitle, Quote, Heading 4–6 and the document's own (Home › Styles…); Normal, Title, H1–H3 on the ribbon; the editor draws each as the document defines it

### Viewing (asked 2026-09-29)
- [x] Tablet: pages at the editor's size, centred, Word's rulers (top + each page's side); the editor is the page itself with a ruler whose indent marks drag
- [x] Centimetres or inches (View tab) for rulers and every length (APA uses inches)

### Formatting pages (asked 2026-09-27: "set everything once, then type" — for big work like a thesis)
- [x] **Styles**: Styles… › Change… on a style → its page: font, size, bold/italic, colour,
      alignment, line spacing (single · 1.15 · 1.5 · double), space before/after, first-line indent — changes the
      document's own style, so every paragraph with it changes, in Word/LibreOffice too
- [x] **Line spacing** quick buttons on Home (1 · 1.15 · 1.5 · 2) — shown on the pages and in the file; the editor keeps its own lines
- [x] **⋯ at the end of Home → Paragraph page**: line spacing, space before/after, left, right and first-line/hanging indent for the chosen paragraphs ; "Edit style “…”" from there (16 Oct: Word's Modify Style, its own panel, with the preview too)
- [x] **Page format**: margins, orientation, paper size (Layout › Page setup)
- [x] **Table format**: styles, shading, lines, alignment, row height, header row, column width, cell alignment and direction (Table tab + More… page)
- [x] **Bibliography / citations** (references list, APA 7 — see References)
- [x] The editor shows the space between paragraphs (Writer's own look, `WriterBridge.Display`, carried over each keystroke and Enter), as the pages, Word, LibreOffice and Export PDF do

### Insert
- [x] Link
- [x] Table (insert 5×5 picker, rows/columns at the cell, merge by tapping two cells, split, cell text in place with Home formatting; .odt tables kept, text only)
- [x] Pictures, charts, shapes, text boxes in a file: kept exactly on save (no longer copy-only)
- [x] Pictures shown on the page (Writer draws them over the text; charts, shapes and text boxes drawn as they are; a floating one in line in the typing view, in its place on the pages)
- [x] Insert › Picture from the phone's photos (a big photo is made a 2400 px JPEG; as wide as the page at most), saved into .docx/.odt
- [x] **Picture tab** (the user's full list, 27 Sep): appears when a picture is selected —
      - **Resize** in a window: horizontal xx px · vertical xx px (not by hand), **Crop** in a window (Photos' crop), **Save picture as…** (take a picture out of a document
     someone sent, into the phone's photos/Files), **Delete**.
      - **Handles** on the chosen object (15 Oct): corner (a photo keeps proportions), right and bottom sides stretch it live;
        a knob above turns one not in line (snaps at 45°); held, it opens Position.
      - **Wrapping**: in line with text (standard) · square / around text · top and bottom (nothing at its sides) ·
     behind text · in front of text.
      - **Moving**: in line — drag it and it lands where it fits in the text (left, centre, right, between lines), not
     exactly where the finger lets go; free placement — drag it exactly where you want, no snapping, the text re-flowing
     around it live as you drag (Word's "fix position on page" with wrapping); in front of / behind text for overlays.
      - Files: docx wp:inline ⇄ wp:anchor (positionH/positionV offsets, wrapSquare / wrapTopAndBottom / wrapNone +
     behindDoc), a:srcRect for crop, wp:extent + a:ext for size; ODT draw:frame svg:x/y, text:anchor-type,
     style:wrap / style:run-through on its graphic style, fo:clip for crop.
- [x] Page break, blank page (Insert: split at the cursor, the rest starts a page — Word's pageBreakBefore / ODT fo:break-before; a dashed "Page break" line in the editor; Backspace at its start removes it)
- [x] Header and footer; page numbers (Insert › Header & footer / Page numbers; Word and ODT; a line each, number as 1 / Page 1 of 3 / 1 / 3, different first page; a file's own "Σελίδα 1 από 3" is kept) ; several lines each, different odd and even pages (even pages their own words, mirrored; Word's even headers + evenAndOddHeaders, LibreOffice's left pages) (15 Oct, evening) — [ ] pictures in headers
- [x] Comment (Insert: on the chosen words or the word at the cursor, your name remembered); comments read and kept in Word and ODT, tinted in the editor, following typing; tablet: cards in a pane beside the page (as Word), phone: View › Comments sidebar + a bar above the ribbon for the one at the cursor; delete, undo; reply and resolve on a card, and (16 Oct) Review's Comments group as Word's — New, Previous, Next, Reply, Resolve/Reopen, Delete for the one at the cursor; on the pages view the commented words tinted (not in Export PDF or Print, as Word)
- [x] Symbol (Ω: punctuation, money, signs, maths, arrows), date and time (today in the phone's language and in English, numeric and ISO; typed at the cursor)
- [x] **Shapes** (Insert, as OnlyOffice/Word/LibreOffice — the user's screenshots, 29 Sep): a gallery by group —
      recently used; basic (rectangle, ellipse, triangle, right triangle, parallelogram, trapezoid, diamond, pentagon,
      hexagon, heptagon/octagon/decagon/dodecagon, pie, chord, frame, L-shape, plus, cylinder, cube, donut, smiley,
      heart, lightning, sun, moon, cloud, arc, braces/brackets); block arrows; maths (+ − × ÷ = ≠); flowchart; stars and
      banners; callouts; lines. Its page (OnlyOffice's "Advanced settings"): Size (width/height absolute or relative to
      margin/page, lock proportions), Rotation, Text wrapping, Position, Lines & arrows, Text box, Alt text. Fill and
      outline colour/width on the ribbon when a shape is selected.
      Done (29 Sep): Insert › Shapes — 17 kinds (rectangle, rounded, ellipse, triangles, diamond, pentagon, hexagon,
      octagon, star, four arrows, plus, heart, line), fill (or none) and outline from six colours (16 Oct: twelve, and Word's
      Shape Styles — seven colours in four looks, in Insert › Shapes and Shape options); written as Word's
      wps:wsp / LibreOffice's custom shape (its look in an automatic graphic style); drawn in the editor, on the pages
      and PDF; the Picture tab sizes, wraps, replaces… it (wrapping now shows on the pages at once). Shapes in a file
      of those kinds are drawn too. Not yet: the full gallery, moving a floating shape, rotation, the settings page,
      changing fill/outline after.
      **15 Oct, evening: the full gallery** — all 187 of Word's preset shapes, in Word's groups (recently used, lines,
      rectangles, basic shapes, block arrows, equation shapes, flowchart, stars and banners, callouts), drawn from their
      real geometry (DrawingML's presetShapeDefinitions via LibreOffice's copy: `ShapeGeometry`), written as Word's prst
      and as LibreOffice's enhanced geometry; files' shapes of any preset (with their adjustments and theme colours) and
      LibreOffice's own custom shapes drawn; **Picture › Shape options**: fill, outline and its width, size (locked or
      not), rotation, alt text — [ ] lines with arrows and connectors, freeform
- [x] A shape's own words (16 Oct): Shape › Add text, in its middle, centred (Word's txbx in the wps:wsp, LibreOffice's
      words in the custom shape; LibreOffice shows them); Edit text after
- [x] Drop cap (Insert › Drop cap: none, dropped, in the margin — Word only; options: lines to drop, distance from text, the letter's font and colour) — Word's framed letter paragraph, read in and written out; ODT's style:drop-cap; drawn on the pages — [ ] shown in the editor
- [x] **Floating objects** (1 Oct): Picture › **Position** drags a floating picture, shape, text box or chart on its page
      (the page as laid out, the text re-flowing on letting go), saved from the page's corner (Word positionH/V
      relativeFrom="page", ODT svg:x/y page-relative); **Square wrap flows the text beside it** on the pages (the side
      with more room; too little room: below it, as Word) — also beside paragraphs before its anchor (a second pass)
      ; forward/backward, align (14 Oct); rotate any angle, group / ungroup (15 Oct; .odt groups — LibreOffice's draw:g — 15 Oct evening) — [ ] text on both sides of a picture in the middle, rotating a whole .odt group
- [x] **Text box** (Insert › Text box, Picture › Edit text): Word's wps text box / LibreOffice's rectangle with words;
      files' text boxes (Word's, LibreOffice's frames and shapes with words) show their words on the pages and in the
      editor ; editing a file's text box keeps every run's look and each paragraph's (15 Oct)
- [x] **WordArt** (Insert › WordArt): its words big, bold, in one of Word's six colours, no frame; outline, shadow,
      glow (15 Oct; glow in Word files — LibreOffice has no glow for letters in a text document); bends — arch up/down, circle, wave, slant up/down (Word's prstTxWarp, LibreOffice's fontwork), the words along them inside the box (15 Oct, evening) — [ ] Word's other warps (inflate, triangle…), changing effects after
- [x] **Line numbers** (Layout, 1 Oct): none / continuous / restart each page / each section (Word; LibreOffice has
      no per-section); drawn on the pages
- [x] **Hyphenation** (Layout, 1 Oct): none / automatic (Word's settings, LibreOffice's fo:hyphenate); the pages
      hyphenate by the paragraph's language (Greek or English) ; by hand (16 Oct): Layout › Optional hyphen at the cursor (Word's softHyphen, read and written; LibreOffice keeps it —
      Word's soft hyphens in files were dropped before); Hyphenation an on/off button, no menu — [ ] hyphenation zone/limits
- [x] **Style sets** (Design, 1 Oct): Basic (Word), Classic (Times), Modern (Arial), Elegant (Georgia) — Normal, Title,
      Headings 1–3 at once; nine sets (+ Word 2010, Word 2003, Centred, Casual, Minimalist); Design › Colours (Word's seven palettes, the file's theme) and Design › Fonts (seven pairs: the theme's in Word files, the styles' in .odt) (15 Oct, evening)
- [x] **Charts** (Insert › Chart, Picture › Edit data): column, bar, line, area, pie, doughnut; clustered or stacked;
      title, legend, colours; a grid of categories × series with the chart drawn above; Word's chart part (data inside,
      no spreadsheet) and LibreOffice's chart object; files' charts read and drawn (OfficeCharts.kt) ; scatter as x/y,
      axis titles, data labels (14 Oct); Word's own Edit Data works on Writer's charts — their numbers in an embedded workbook, the chart pointing at its cells (15 Oct, evening)
- [x] **Equations** (15 Oct): Word's equations read, drawn and kept (no longer copy-only); Insert › Equation in Word's
      linear form with a preview, Picture › Edit equation ; typing in one in place — an equation chosen shows its linear form in a bar over the ribbon, the page redrawing it as you type (15 Oct); LibreOffice's formulas (MathML objects) read and drawn, Insert › Equation and Edit equation in .odt write LibreOffice Math objects (15 Oct, evening)
- [x] **Content controls** (Insert › Content control, 15 Oct): plain/rich text, check box, drop-down and combo
      list, date picker, with a title; filled from the form bar; .odt's too — LibreOffice's loext:content-control read,
      filled, written and inserted (15 Oct, evening) — [ ] picture controls
- [x] SmartArt shown (15 Oct, evening): from the drawing Word saves with it (word/diagrams/drawing*.xml), each shape and its words where they stand, theme colours; kept in the file as it was — [ ] editing SmartArt, an .odt's (LibreOffice turns it into shapes)

### Draw (Word's Draw tab; OnlyOffice: Select · 2 pens · highlighter · Eraser)
- [x] A **drawing box** (Draw › Drawing box): drawn in with finger or pen, put in the text as a see-through PNG in a
      paragraph of its own (Word and LibreOffice show it); the Picture tab sizes/wraps it after
- [x] Two pens and a highlighter, each its colour (8) and width in mm (− 0.5 mm +); stroke eraser; undo a stroke —
      [ ] Select/lasso, more colours, editing a drawing again after it is placed (it is a picture then)
- [ ] Later: ink over the text itself (floats on the page: needs floating objects; Word saves it as InkML); Ink to
      Shape / Ink to Math / Ink Replay not planned

### Design (Word's Design tab; OnlyOffice Σχεδίαση)
- [x] APA 7 student paper (Design › APA 7 paper, asked 2026-09-30): Times New Roman 12, 1-inch margins, double spacing with no space between paragraphs, first lines 0.5 in, the page number top right, APA's heading levels 1–5 as Heading 1–5, references on a new page; optionally clears fonts/sizes/spacing set by hand and adds the title page (title, name, department & university, course, instructor, date) with the title again over the text ; the abstract page and run-in level 4–5 headings (15 Oct); the professional paper — its running head (short title in capitals) top left on every page, the title page without course/instructor/date (15 Oct, evening)
- [x] Page background: watermark (Word's six words or your own; remove) — as Word's WordArt in the header / LibreOffice's fontwork in each page style; page colour (Word's light tints); page borders (a box: thin, medium, thick); a picture watermark (washed out, Word's VML imagedata / LibreOffice's frame in colour mode "watermark", 14 Oct) ; border styles (solid, double, dotted, dashed), colours and some sides only (15 Oct) — [ ] art borders
- [x] Paragraph spacing presets for the whole document (No space, Compact, Tight, Open, Relaxed, Double) — the Normal style's
- [x] Style sets, theme colours and fonts (15 Oct, evening) — [ ] whole themes (effects), .odt's theme colours (LibreOffice 7.6+'s loext:theme)

### Layout (Word's Layout; OnlyOffice Διάταξη)
- [x] Margins (Normal = 1 in/APA, Narrow, Moderate, Wide, 2.5 cm, or custom in cm/in), orientation, size (A4, Letter, Legal, A5, A3) — Layout tab, one Page setup page; every section of a Word file (pgSz/pgMar, header distances kept), every page layout of an ODT (the header band allowed for); a tablet shows a page too wide smaller rather than reflowing
- [x] Columns (Layout › Columns: one, two, three — Word w:cols / ODT style:columns, every section; the pages flow text down each column, a page break still starts a page; the editor keeps one column) ; column breaks, columns in part of a document through continuous section breaks (15 Oct); in .odt a section's own columns (LibreOffice's text:section), files' sections kept and shown, page styles changing mid-document start a page of their size (15 Oct, evening) — [ ] balancing a continuous section
- [x] Page break, blank page (also on Layout); Layout › Breaks: column break, section break next page / continuous (15 Oct; .odt: LibreOffice's text:section, 15 Oct evening)
- [x] Line numbers, hyphenation (Layout, 1 Oct — see Insert's entries)
- [x] Indent left/right and spacing before/after as number boxes (Layout's second page: − value +, a quarter cm or a
      tenth of an inch, 6 pt — 16 Oct)
- [x] Arrange (for floating objects): position, wrap, bring to front / send to back, align to the margins (14 Oct); free rotation, group / ungroup (15 Oct; .odt too, 15 Oct evening); a rotation handle (the knob above a floating object, 15 Oct)

### References
- [x] Table of contents (References: from Heading 1–3, at the cursor; Update table rewrites it in place with fresh page numbers) — written as plain paragraphs in the file's TOC 1–3 / Contents 1–3 styles with a dot leader, not a field: Word/LibreOffice show it as written; **clickable entries** (16 Oct): each a link to its heading's _Toc bookmark
      (Word's w:anchor, LibreOffice's #name and text:bookmark), followed by Word, LibreOffice and their PDFs; in the editor
      the cursor on one shows "Go to …", which goes there; links inside documents now kept (they made a file copy-only); levels chosen (References › Levels 1–4, Update keeping
      what the contents show; Word's TOC 4 and LibreOffice's Contents 4 added when used)
- [x] Footnotes and endnotes (References › Insert footnote / Insert endnote): the number in the text (superscript, numbered in order — endnotes i, ii…), the note's words in a bar at the cursor and a dialog (Edit, Delete note); on the pages at the foot of their page under a short line, endnotes after the text; kept as each program keeps them (Word's footnotes.xml/endnotes.xml, LibreOffice's text:note) — a file with notes no longer copy-only — [ ] the note's own formatting when its words are edited (edited notes become plain), numbering formats other than 1/i, footnotes in columns
- [x] Citations and bibliography, APA 7 (References › Insert citation, Sources, References list / Update references, Update citations): Word's form — the kind of source, then its fields; APA writes the citation "(Smith & Jones, 2019, p. 47)" and the reference entry. Kept as each program keeps them: Word's CITATION fields and its sources part (Source Manager); LibreOffice's bibliography marks ; report, thesis, video sources (14 Oct); **a citation tapped** shows a bar — Edit each of its sources, Change… its sources or page (15 Oct) ; **References › Style**: APA 7, MLA 9, Chicago author-date — citations and the list rewritten, kept as Word keeps it (15 Oct, evening) — [ ] a citation's own options (no author, suppress year), Harvard/IEEE
- [x] Captions, cross-references (References › Caption, Cross-reference, Update fields — 14 Oct)

### Review
- [x] Spelling (Android's spell checker, Greek and English): wavy line, suggestions at the cursor, Next mistake, on/off ; Tetra's own dictionaries (LibreOffice's Greek and English Hunspell ones in the app: works without Gboard)
- [x] Word count (File › About this file); live in the top bar on the tablet and the phone, with the selection's (15 Oct)
- [x] Comments: read, add, delete, reply, resolve / reopen (Word's commentsExtended; LibreOffice's loext:parent-name / resolved), edit a comment's words (14 Oct)
- [x] Track changes in Word files (Review tab): shown as Word's All Markup (inserted underlined, deleted struck through, in the author's colour; a bar beside changed lines; ¶ for split/joined paragraphs), the change at the cursor named (what · who · when), Previous / Next, Accept / Reject (then on to the next), Accept all / Reject all (tables' row and formatting changes too); kept on saving — a corrected .docx is no longer copy-only ; **recording** (Review › Track changes, Word files: typing is your insertion, Backspace/Delete strike through and walk on past, Enter/joins are paragraph marks, under your name — asked once) — [ ] tracking remembered in the file (Word's trackRevisions), the typed insertion's colour before the next redraw (underlined at once), recording pasted/Writer-made changes, ODT's tracked changes (still copy-only), reject restoring a paragraph's style/list
- [x] Read aloud (Review): Android's text-to-speech from the cursor, each paragraph in Greek or English, equations said in words; a bar with Stop (15 Oct, evening); its speed in Settings › Office (0.75×–2×)

### View
- [x] Zoom
- [x] Fit to width (View: the page as wide as the screen, a tablet's too; a phone's pages always are), two pages side by
      side (View › Two pages, and the pages' bar) — 16 Oct
- [x] Reading mode (no ribbon, bigger text): View › Reading mode or the book over the pages; text reflowed at 140 %, − / +, read only (15 Oct)
- [x] Navigation pane: jump to a heading

### On the phone
- [x] Ribbon at the bottom; a strip on the keyboard; the tools in pages with dots (asked 2026-09-27)
- [x] Keyboard bar like Word's phone bar: B · I · U · highlighter · dictation · ⋯ (the whole ribbon, ⋯ again back) · hide keyboard (15 Oct, evening)
- [x] Dictation (the keyboard bar's mic: Android's voice typing, put in at the cursor) (15 Oct, evening)

## Excel (Calc) — a viewer first (the user, 30 Sep); started 15 Oct, night ("start with the viewer and let's complete it like we did with Word")

Code: `Sheets.kt` (readers), `SheetFormat.kt` (Excel's number formats), `SheetTools.kt` (layout, filter, sort, search,
stats), `SheetScreen.kt` (the screen and the painter). Tests: `SheetsTest`, `SheetToolsTest` (`TETRA_SWEEP=1` reads all
of the user's spreadsheets: 13, none failing). Logged to writer.log with a "Sheets:" prefix (open, read and draw times,
slow frames, taps, filters, searches — never a cell's words).

### Viewer — done 15–16 Oct (checked on the phone)
- [x] Open .xlsx / .xlsm / .ods / .csv from Files and the Office home (green, a table icon; Settings › Office's switch)
- [x] Sheets as tabs at the bottom (hidden ones left out, the one the file was left on first); each sheet read when
      opened, the last two kept; parts read from the zip when needed (the user's 46-sheet workbook: 66 MB of heap)
- [x] Cells as the file draws them: widths, heights, hidden rows/columns, fills, borders (thin…double, dashed, dotted),
      fonts (Carlito/Liberation for Calibri/Arial/Times, Arial Narrow condensed), bold/italic/underline/strike,
      colours (theme + tint, indexed), alignment, wrap, indent, rotation, merged cells, frozen panes, gridlines off
- [x] Words running into empty neighbours (left, right, centred), #### for a number that does not fit
- [x] Excel's number formats: General, 0.00, #,##0, %, E+00, fractions, currency, sections and conditions, [Red],
      dates/times (elapsed [h], Greek month names with [$-408]); the phone's decimal and thousands separators
- [x] .ods: the shown text as LibreOffice wrote it; styles with their parents; number styles' colours; view settings
- [x] .csv: separator found (, ; tab |), UTF-8 or Windows Greek
- [x] Excel Tables: the file's own table styles (Google Sheets' alternating colours) and the built-in Light/Medium/Dark
      (Medium 2 as Excel; the rest close — shortcut noted in `builtInTable`)
- [x] Pictures, charts (Writer's chart drawing; numbers from the cells when the file keeps none) and text boxes
- [x] Comments (notes, threaded comments, LibreOffice's annotations): red corner, shown in the cell bar
- [x] Links: a cell's hyperlink, HYPERLINK(), LibreOffice's — open, or go to the sheet and cell
- [x] Cell bar: the cell's name, its formula or all its words (tap to open it whole)
- [x] Pinch to zoom (25–400 %), fling; a sheet keeps its place when its tab is left
- [x] ⋯: Open in another app (to change it), Share, About this file, Send log

### The viewer's own tools (asked 30 Sep) — done 16 Oct
- [x] **Fuzzy search** across the cells: accents, case and ς/σ forgiven; a word of 4+ letters one letter off (7+: two),
      a word's start; numbers by their digits; finds in yellow, the current one framed in orange, next/previous
- [x] **Filter rows by a column**: a tap on its letter › Filter… — its values with counts, a search among them; the
      header row from the file's AutoFilter, frozen rows, or the first full row; rows numbered as in the file (blue)
- [x] **Sort** A→Z / Z→A (numbers by value, words in the phone's language, empty last) — the file is not changed
- [x] **Pin columns side by side**: Pin to the start, in the order pinned, the rest scrolling beside them
- [x] Chips say what is on (Pinned, Filter, Sorted), each with ✕
- [x] Choose cells (long press, drag; Select column/row): Sum, Average, Count (tap: Min, Max, Numbers)
- [x] Keep rows at the top: a tap on a row's number (Freeze Panes, chosen here)

### More, 16 Oct (night)
- [x] **Numbers checked against LibreOffice**: every cell of the user's 13 spreadsheets (and LibreOffice's .ods of
      each) as LibreOffice shows it — 7,848,540 cells, none differ (`SheetsTest`, TETRA_SHOWN; how to make its CSVs is
      in the test). Fixed on the way: seconds cut, not rounded; month names in the phone's language
- [x] General numbers fitted to narrow columns as Excel (fewer decimals, then scientific), #### only when nothing fits
- [x] Conditional formatting: highlight rules, top/bottom, average, duplicates, simple formulas, colour scales, data
      bars and icon sets (Excel's and LibreOffice's)
- [x] Copy the chosen cells (tab-separated) or a cell's words; Go To a cell by its name in search; Show formulas
- [x] A file opens where it was left (sheet, place, zoom, cell)
- [x] Export PDF (beside the file) and Print: the file's page setup, print area, rows on every page, as drawn
- [x] **Search in all sheets** ("All sheets" in the search bar): finds listed sheet by sheet with their row's words,
      a tap goes there; .xlsx sheets scanned in their bytes (the user's 46 tables: 7.7 s on the phone); numbers exact
- [x] Big files: parts streamed from the zip (.ods content.xml of 360 MB opens with 31 MB of heap — but ~9 s to list
      its sheets, shortcut noted); the school timetable's 168,039 cells read in 0.85 s

### Next (viewer)
- [x] Charts in more kinds: combo (a line over columns), radar, bubble — from .xlsx and .ods (charts.xlsx/.ods), in Writer too
      (shown, not offered for a new chart; a bubble chart saved from Writer is a scatter of its points)
- [x] Shapes in their outlines (Writer's 187 presets, their adjustments; LibreOffice's kinds and paths), the theme's look
      (fill, darker outline, white words) as Excel's default shape, words in the middle (shapes.xlsx/.ods)
- [x] A combo's second value axis: a line on its own scale, its numbers at the right (charts.xlsx "Two axes")
- [x] Tablet: checked at a tablet's width (the phone at 800 dp, 16 Oct): sheet, charts, pictures and the ribbon in one
      row fit; no second pane (Excel's tablet app has none either). On the tablet itself: with the user
- [x] .xls (Excel 97–2003): shown (values, formulas' results, fonts, fills, number formats, merges, widths, frozen panes);
      not edited (Excel-test.xls: every cell as the .xlsx's). Not read yet: its conditional formats, notes, pictures

### Editing — started 16 Oct (asked: "edit cells, the formula bar, save back to the file")
Code: `SheetEdit.kt` (typed entries, the bar's text, recalculation, undo), `SheetCalc.kt` (formulas), `SheetSave.kt`.
Tests: `SheetEditTest`, `SheetSaveTest` (saved files opened by LibreOffice), `SheetsTest.formulas` (129 formulas as
LibreOffice works them out).
- [x] Edit (pencil) → Undo, Redo, Save, Done; the cell bar is a field (the cell as typed: 1234,5, 15/3/2026, =B2*2);
      Enter puts it in and goes down; ✕ leaves it; a tap on a cell only chooses it, the bar is tapped to type; Clear chosen cells
- [x] Typed entries as Excel: the phone's 1.234,5, 25%, dates day first, times, TRUE, ' for words, =formulas (; or ,)
- [x] Formulas: ~80 functions (sums, counts, IF/IFS-style, lookups, text, dates, rounding, statistics), across sheets;
      a tapped cell's name goes into a formula being typed; formulas that read a changed cell follow at once (a function
      not known keeps the file's value)
- [x] Save back into the file: .xlsx (only the edited rows rewritten, every other part as it was; a typed date's or
      percent's format added as a style; shared formulas written out; calcChain dropped, worked out on opening),
      .ods (cells in content.xml), .csv (its separator and letters); the file before kept as a version (Writer's)
- [x] Leaving with changes: Save / Don't save
- [x] Versions in File and ⋯: the file as it was before a save (the last 3), opened in the editor as one step to undo
- [x] Insert/delete rows and columns (every reference, merge, rule, drawing, chart, comment and table moved), Undo/Redo
- [x] Cell formatting: bold, italic, underline, strike, colours, fill, borders, alignment, wrap, number/currency/date/
      percent/text, decimals, size, font, Clear formatting (.xlsx and .ods; LibreOffice shows them)
- [x] The ribbon as Word's (asked 16 Oct: "Tabs? home insert and so on"): Selection, File, Home, Insert, Formulas, Data,
      View while editing, and a tab for what is chosen (Column, Row, Sheet) that comes by itself, in view mode too;
      Selection first as Words' (Cut, Copy, Paste, Clear, Select all, the chosen cells' sum; asked 16 Oct: Home was crowded)
- [x] Ribbon rows shaded at an edge while there is more that way, as Words' (asked 16 Oct)
- [x] Formulas › AutoSum, Average, Count, Max, Min: the numbers above (or to the left) in the cell bar, Enter puts it in
- [x] New spreadsheet (the Office home's Excel card, named on its first save); new sheet, rename, move, delete (names
      and formulas follow; LibreOffice agrees), each one step to undo
- [x] Recovery draft: unsaved changes kept when the app goes to the background, back by themselves on the next open
      (Undo shows the file as saved), the sheet and place kept too
- [x] Font, Borders and Number as strips in the ribbon's place, as Writer's Page numbers (a font shown as tapped, ✕ puts
      it back); the ribbon slides down and up (asked 16 Oct)
- [x] Typing in the cell bar: a row of the functions for the letters typed, each with what it does; inside one's brackets,
      what it takes (asked 16 Oct: "I don't know them by heart")
- [x] The fill handle: a dot at the chosen cells' corner while editing, dragged to fill (series, dates, "Week 1" → 2,
      formulas with their references moved, $ kept)
- [x] Cut, Copy, Paste: cells copied here keep their formulas (moved), looks and formats; one cell onto many chosen
      fills them; text from another app goes in as a table (LibreOffice agrees)
- [x] .ods number styles read as Excel formats, so a typed or formatted .ods number shows as LibreOffice shows it
      (number-formats.ods: 300+ numbers formatted as LibreOffice wrote them; ? as a figure space, ΠΜ/ΜΜ, dates before
      1900, negative times); a number typed into a percent cell is a percent (25 → 25%), as Excel

## PowerPoint (Presentation) — after Excel

- [x] Open .pptx / .odp (16 Oct): slides with their layouts', masters' and themes' looks — placeholders' places, title and
      body styles, bullets and levels, theme colours and fonts — shapes in their outlines, pictures, charts, tables, groups,
      backgrounds, speaker notes (Notes under each slide); slides.py's deck and LibreOffice's .odp of it, as LibreOffice shows them
- [x] Present: full screen and sideways, swipe or tap (right: next, left: back), the bars hidden, hidden slides skipped
- [x] Gradients (their own, the theme's through a shape's style, LibreOffice's named ones), custom outlines (custGeom),
      flipped shapes, text shrunk to fit its box, a one-series chart titled with its name (Office's automatic title) —
      checked with the user's own decks against LibreOffice (20 decks read, 16 Oct)
- [x] Editing a .pptx (16 Oct): Edit → a slide tapped is chosen, its strip at the bottom: Duplicate, Move up/down, Hide/Show,
      Delete; a text box tapped on it: its words (a line a paragraph, each keeping its look); Undo, Save (the file before
      kept as a version; LibreOffice opens it); New slide: the deck's layouts in a strip, grey hints in their empty boxes
      (not saved); a slide's own empty boxes the same; a new presentation (the Office home's card, Files › New: an empty title
      slide, opened editing); **.odp too** (16 Oct): its pages moved, duplicated, deleted, hidden, words typed, its own items
      moved, sized, coloured (LibreOffice's copy of the fill in the text style too) or deleted, pictures and shapes put
      on, transitions and entrances/exits read, played, chosen and saved as LibreOffice writes them (its names for
      push, cover, uncover, wipe, fade — no cut), saved into content.xml (LibreOffice opens it, and its .pptx of it
      agrees); New slide from its master pages (LibreOffice's copies of PowerPoint's layouts; one master: Title only and
      Blank too), their title and text boxes empty with grey hints, a text box's lines a list (LibreOffice's bullets)
      ; tables read (their columns, rows, cells' fills and words) and put on (PowerPoint's banded look, as LibreOffice keeps it)
- [x] Edit text on a slide (above); layouts and themes from the file (as shown)
- [x] Insert picture (the photo picker; a GIF stays a GIF and plays, as Google Slides', 16 Oct) and shape (Office's
      everyday shapes); moved and sized with a finger (its corner's handle) or the arrows for exact places; Delete —
      saved into the file (LibreOffice opens it)
- [x] Design › Themes (16 Oct, asked: "some dark ones some light ones"): four light (Office, Leaf, Ocean, Paper) and four
      dark (Night, Slate, Midnight, Charcoal) — a theme's colours and fonts, a band on the master — and the templates in
      Documents/Templates (their colours and fonts); one step to undo, saved into the file's theme and master (LibreOffice
      shows them); .pptx only — [ ] an .odp's themes
- [x] New presentation from a template (the Slides card asks when Documents/Templates has .potx/.pptx in it)
- [x] Claude's tetra-slides-theme skill (~/.claude/skills): a school's or company's theme — its colours, fonts, logo,
      band — as a .potx in ~/Tetra/Files/Documents/Templates, which syncs to the phone
- [x] Insert › Shape: Words' Shapes panel (16 Oct) — all of Word's shapes in their groups, Shape Styles, twelve colours;
      a new item's id past every one on the slide (a shape saved there before had the same id as the next one put on)
- [x] Insert table on a slide (2×2 to 5×4, PowerPoint's default banded style; a cell tapped is typed into); GIFs play on
      the TV too; the words' box takes the keyboard at once (16 Oct)
- [x] Transitions and animations shown when presenting (16 Oct): push, gallery, cover, pull, wipe, cut, fade (any other
      as a fade), each its own length; click animations — appear, fade, fly in from its side (others as a fade), the
      clicks in turn, those that start by themselves too, a click during one ends it; on the TV the same frames
- [x] The ribbon with tabs, as Tables' (asked 16 Oct): Selection (what is chosen on the slide), Slide, Insert,
      Transitions, Animations; a tap chooses a text box, shape or picture, a second tap types in it
- [x] Colours (asked 16 Oct: "or are they just blue?"): a shape's fill, border and words' colour (PowerPoint's standard
      colours, or none), a table's (its first row, the others in its tints) — the file's own shapes too
- [x] The file's own text boxes, shapes, pictures, charts and tables moved and sized (finger or arrows), saved in their
      place, or deleted (its animation with it); a group chosen as a whole (moved, sized, coloured, animated, deleted; a
      second tap types into its shape)
- [x] Choosing a slide's transition (None, Fade, Push, Wipe, Cover, Uncover, Cut; Options: its side and speed; Apply to
      all; Play) and an item's entrance and exit (Appear/Disappear, Fade in/out, Fly in/out; Options: the fly's side,
      Fast/Medium/Slow; Earlier/Later in the clicks; Play), saved as PowerPoint's XML; files' exits read and played —
      LibreOffice reads them (its wipe, its clicks, fade-in, fly-out from the left); emphasis — Pulse, Spin, Grow — read,
      played (about the item's or group's middle; a grown one stays grown), chosen and saved (PowerPoint's animRot and
      animScale, LibreOffice's animateTransform) — [ ] other kinds of effect (changing a slide's animations writes them
      as their nearest: a fade, a pulse); Start — on a click, with the one before, after it
      (Options; the label counts clicks), in both formats (LibreOffice's "by itself" read in its .pptx too); Delay (none,
      0.5–5 s, from its click, the one it starts with, or the end of the one before); Next slide: on a click or by itself
      after 3–20 s (PowerPoint's advTm, LibreOffice's automatic transition; the time runs once the slide's effects are
      done) — [ ] LibreOffice's .pptx import drops a time on a slide with no transition (PowerPoint's own way of writing it)
- [x] Present: pen (ink kept on each slide while presenting, wiped with the eraser), laser, black screen (16 Oct)
- [x] Present to a TV (HDMI, a cast screen: Android's presentation display): the slide there with its ink and laser,
      the phone the presenter's (the notes large) — tried 16 Oct on the emulator's second display (a presentation
      display, as HDMI or a cast screen is): the slide there, transitions and effects moving there every frame (they
      had stood still until then); **a real TV still to try with the user**
- [x] Export PDF (File): A4, 2 cm margins, beside the document — laid out by Android, not by Word

## To test on the phone, with the user

Each is done on the emulator; tick when checked on the real phone.
- [ ] Home: orange Office group (icon, New document, Documents), purple Text editor group, their Settings cards
- [ ] Office home: header icon; Word blue, Excel green, PowerPoint deep orange; the documents list (spreadsheets listed in green, 16 Oct)
- [ ] Open a .docx and an .odt of his own; nothing looks different from Word/LibreOffice (bold, lists, headings)
- [ ] Edit + Save over a file with no banner; open it on the computer in LibreOffice/Word
- [ ] A file with tables or images: orange banner, Save makes "(copy)", the original untouched
- [ ] New document: Heading 1, Enter gives a Normal line, bullets, numbering, Save as .docx and as .odt
- [ ] Ribbon: tabs, pages of tools with dots, the strip on the keyboard, colours and highlight swatches
- [ ] Line break (Insert), Link (Insert), Find and Replace, Replace all, Share, About this file (word count)
- [ ] Leave with unsaved changes → Save / Don't save; close the app mid-edit → reopen → "Your unsaved changes are back"
- [ ] Text size −/+ and zoom; undo/redo
- [ ] File › Versions: save twice, restore the older one; File › Export PDF, open the PDF
- [ ] Pictures: open a document with pictures; Insert › Picture; save; open it on the computer
- [ ] PowerPoint: present to a real TV (HDMI or cast) — the slide on the TV, the notes on the phone (the emulator's second display works)
- [x] Tablet (the emulator's 1280×800, 16 Oct): the Office home's wide cards, Words, Tables, Slides open and edit; a slide
      no wider than fits the screen's height, so a whole one shows (it was taller than the screen)
- [ ] Αναπιρία.odt (or any of his): edit + Enter + Save → open on the computer: same layout, spacing, lines, bullets

## Sources

- [Microsoft Word for Android (and iOS) cheat sheet — Computerworld](https://computerworld.com/article/3481618/microsoft-word-for-android-and-ios-cheat-sheet.amp.html)
- [How to Use Excel on Android Phones — Noble Desktop](https://blog.nobledesktop.com/how-to-use-excel-on-android-phones)
- [Microsoft Excel — Google Play](https://play.google.com/store/apps/details?id=com.microsoft.office.excel&hl=en)
- [PowerPoint for Android — BrightCarbon](https://www.brightcarbon.com/blog/powerpoint-for-android/)
- [Add animation effects in PowerPoint on a mobile device — Microsoft Support](https://support.microsoft.com/en-us/powerpoint/add-animation-effects-in-powerpoint-on-a-mobile-device)
- The user's Word screenshots (27 September): top bar, Home ribbon, Insert and Draw panes, History, the phone keyboard bar
