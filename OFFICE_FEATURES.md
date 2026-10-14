# Office — the features, one by one

What Microsoft Office's phone apps do (researched 27 September 2026: Word, Excel and PowerPoint for Android), as a
checklist for Tetra's Office. Tetra is local — nothing that needs Microsoft's cloud (live co-editing, public links,
Copilot). Each item: **done**, **next**, or later. Build them in order, one at a time; each one is tested on the
emulator and on the phone, and a file saved with it opens right in Word / LibreOffice.

Rule for every feature: a file part Writer can show *and* save is no longer "copy-only" — each item below that adds
such a part also moves it out of `OfficeDoc.blocking`.

## Word (Writer)

**Tablet and phone (the user, 27 Sep 2026):** the work is not split — every feature goes on both. The phone is good
too; it just can never beat a big screen, so on the phone Claude decides how each feature fits (the user leaves that
to Claude). A document opens **view only** (as LibreOffice does), with **Edit** at the top.

### FIRST — proper pages, viewing before editing (asked 2026-09-27)
- [ ] A document opens **view only**, as pages (Print layout), like LibreOffice: the file's page size and margins, a
      gap between pages, scroll and pinch-zoom, "Page 3 of 12"; **Edit** at the top switches to the editor and back
- [ ] The pages show what the file sets: line spacing, space before/after, indents, page breaks, pictures in place,
      fonts as close as the phone has them
- [ ] **Tablet view** (asked 2026-09-27): the text must not stretch across the whole screen — a page of its real width,
      centred, zoomable (landscape tablet: one page, or two side by side); the editor too keeps the page's width
- [x] Tablet ribbon: a tab's tools in one line, close together and centred (no pages, which spread them apart)
- [ ] Then: header and footer, page numbers, tables
- [ ] Built on the Export PDF layout (OfficePdf.kt), so the PDF gets every improvement too; checked page by page against
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
      selection's). Tables' words count, pictures do not

### File
- [x] Save over the file when nothing is lost; else a copy beside it
- [x] New documents: .docx (or .odt); Save as with a name, in Tetra/Documents
- [x] Unsaved changes kept as a draft; leaving asks Save / Don't save
- [x] Versions (File): the file as it was before each of the last 3 saves; Restore keeps the current one as a version too
- [x] Export PDF (File): A4, 2 cm margins, beside the document — laid out by Android, not by Word
- [x] AutoSave (on by default in Word): save by itself after a pause, for files that are not copy-only — File › AutoSave and Settings › Office (14 Oct)
- [ ] Open .doc / .rtf (read; save as .docx)

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
      (and PDF) as Word groups them, outlined in the editor — [ ] border style and colour (behind ⋯)
- [x] Change case (Aa beside the eraser: Sentence case, lowercase, UPPERCASE, Capitalise Each Word; the chosen letters or the word at the cursor; looks kept; Greek capitals without accents, final ς)
- [x] Multilevel list — Home › Multilevel: 1. 2. 3. / 1. a. i. / 1. 1.1. 1.1.1.; "2.1." shown in Edit as on the pages (14 Oct)
- Not on the ribbon: the font name (the space is needed; a style's page sets fonts)
- [x] Paragraph formatting kept: spacing, indents, tab stops, borders, page break before, styles such as Title/Quote, one font per paragraph, the file's own lists — carried through edits (a new paragraph takes the one before it), checked pixel-identical in LibreOffice on 6 of his files
- [ ] Paragraph formatting *shown* on the page and *changed* from the ribbon (spacing, indents) — the editor library has no paragraph spacing
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
- [x] **⋯ at the end of Home → Paragraph page**: line spacing, space before/after, left, right and first-line/hanging indent for the chosen paragraphs — [ ] "Edit its style" from there
- [x] **Page format**: margins, orientation, paper size (Layout › Page setup)
- [x] **Table format**: styles, shading, lines, alignment, row height, header row, column width, cell alignment and direction (Table tab + More… page)
- [x] **Bibliography / citations** (references list, APA 7 — see References)
- [ ] The editor cannot show space between paragraphs (library limit); what is set is saved and shows in Word,
      LibreOffice and Export PDF

### Insert
- [x] Link
- [x] Table (insert 5×5 picker, rows/columns at the cell, merge by tapping two cells, split, cell text in place with Home formatting; .odt tables kept, text only)
- [x] Pictures, charts, shapes, text boxes in a file: kept exactly on save (no longer copy-only)
- [x] Pictures shown on the page (Writer draws them over the text; charts, shapes and text boxes drawn as they are; a floating one in line in the typing view, in its place on the pages)
- [x] Insert › Picture from the phone's photos (a big photo is made a 2400 px JPEG; as wide as the page at most), saved into .docx/.odt
- [ ] **Picture tab** (the user's full list, 27 Sep): appears when a picture is selected —
      - **Resize** in a window: horizontal xx px · vertical xx px (not by hand), **Crop** in a window (Photos' crop), **Save picture as…** (take a picture out of a document
     someone sent, into the phone's photos/Files), **Delete**.
      - **Wrapping**: in line with text (standard) · square / around text · top and bottom (nothing at its sides) ·
     behind text · in front of text.
      - **Moving**: in line — drag it and it lands where it fits in the text (left, centre, right, between lines), not
     exactly where the finger lets go; free placement — drag it exactly where you want, no snapping, the text re-flowing
     around it live as you drag (Word's "fix position on page" with wrapping); in front of / behind text for overlays.
      - Files: docx wp:inline ⇄ wp:anchor (positionH/positionV offsets, wrapSquare / wrapTopAndBottom / wrapNone +
     behindDoc), a:srcRect for crop, wp:extent + a:ext for size; ODT draw:frame svg:x/y, text:anchor-type,
     style:wrap / style:run-through on its graphic style, fo:clip for crop.
- [x] Page break, blank page (Insert: split at the cursor, the rest starts a page — Word's pageBreakBefore / ODT fo:break-before; a dashed "Page break" line in the editor; Backspace at its start removes it)
- [x] Header and footer; page numbers (Insert › Header & footer / Page numbers; Word and ODT; a line each, number as 1 / Page 1 of 3 / 1 / 3, different first page; a file's own "Σελίδα 1 από 3" is kept) — [ ] richer headers (several lines, pictures), odd/even
- [x] Comment (Insert: on the chosen words or the word at the cursor, your name remembered); comments read and kept in Word and ODT, tinted in the editor, following typing; tablet: cards in a pane beside the page (as Word), phone: View › Comments sidebar + a bar above the ribbon for the one at the cursor; delete, undo — [ ] reply, resolve (Review tab); show them on the pages view
- [x] Symbol (Ω: punctuation, money, signs, maths, arrows), date and time (today in the phone's language and in English, numeric and ISO; typed at the cursor)
- [ ] **Shapes** (Insert, as OnlyOffice/Word/LibreOffice — the user's screenshots, 29 Sep): a gallery by group —
      recently used; basic (rectangle, ellipse, triangle, right triangle, parallelogram, trapezoid, diamond, pentagon,
      hexagon, heptagon/octagon/decagon/dodecagon, pie, chord, frame, L-shape, plus, cylinder, cube, donut, smiley,
      heart, lightning, sun, moon, cloud, arc, braces/brackets); block arrows; maths (+ − × ÷ = ≠); flowchart; stars and
      banners; callouts; lines. Its page (OnlyOffice's "Advanced settings"): Size (width/height absolute or relative to
      margin/page, lock proportions), Rotation, Text wrapping, Position, Lines & arrows, Text box, Alt text. Fill and
      outline colour/width on the ribbon when a shape is selected.
      Done (29 Sep): Insert › Shapes — 17 kinds (rectangle, rounded, ellipse, triangles, diamond, pentagon, hexagon,
      octagon, star, four arrows, plus, heart, line), fill (or none) and outline from six colours; written as Word's
      wps:wsp / LibreOffice's custom shape (its look in an automatic graphic style); drawn in the editor, on the pages
      and PDF; the Picture tab sizes, wraps, replaces… it (wrapping now shows on the pages at once). Shapes in a file
      of those kinds are drawn too. Not yet: the full gallery, moving a floating shape, rotation, the settings page,
      changing fill/outline after.
- [x] Drop cap (Insert › Drop cap: none, dropped, in the margin — Word only; options: lines to drop, distance from text, the letter's font and colour) — Word's framed letter paragraph, read in and written out; ODT's style:drop-cap; drawn on the pages — [ ] shown in the editor
- [x] **Floating objects** (1 Oct): Picture › **Position** drags a floating picture, shape, text box or chart on its page
      (the page as laid out, the text re-flowing on letting go), saved from the page's corner (Word positionH/V
      relativeFrom="page", ODT svg:x/y page-relative); **Square wrap flows the text beside it** on the pages (the side
      with more room; too little room: below it, as Word) — also beside paragraphs before its anchor (a second pass)
      — [ ] text on both sides of a picture in the middle; forward/backward, align, group, rotate
- [x] **Text box** (Insert › Text box, Picture › Edit text): Word's wps text box / LibreOffice's rectangle with words;
      files' text boxes (Word's, LibreOffice's frames and shapes with words) show their words on the pages and in the
      editor — [ ] editing a file's text box keeps only its first words' look
- [x] **WordArt** (Insert › WordArt): its words big, bold, in one of Word's six colours, no frame — [ ] Word's text
      effects (outline, shadow, glow, warp)
- [x] **Line numbers** (Layout, 1 Oct): none / continuous / restart each page / each section (Word; LibreOffice has
      no per-section); drawn on the pages
- [x] **Hyphenation** (Layout, 1 Oct): none / automatic (Word's settings, LibreOffice's fo:hyphenate); the pages
      hyphenate by the paragraph's language (Greek or English) — [ ] manual hyphenation, hyphenation zone/limits
- [x] **Style sets** (Design, 1 Oct): Basic (Word), Classic (Times), Modern (Arial), Elegant (Georgia) — Normal, Title,
      Headings 1–3 at once — [ ] Word's theme fonts/colours, more sets
- [x] **Charts** (Insert › Chart, Picture › Edit data): column, bar, line, area, pie, doughnut; clustered or stacked;
      title, legend, colours; a grid of categories × series with the chart drawn above; Word's chart part (data inside,
      no spreadsheet) and LibreOffice's chart object; files' charts read and drawn (OfficeCharts.kt) ; scatter as x/y,
      axis titles, data labels (14 Oct) — [ ] Word's own Edit data (it needs the embedded workbook)
- [ ] Later: equations (√x,
      kept in files today); SmartArt, charts; forms (content controls: plain/rich text, picture, drop-down/combo list,
      date picker, check box — OnlyOffice's last Insert button)

### Draw (Word's Draw tab; OnlyOffice: Select · 2 pens · highlighter · Eraser)
- [x] A **drawing box** (Draw › Drawing box): drawn in with finger or pen, put in the text as a see-through PNG in a
      paragraph of its own (Word and LibreOffice show it); the Picture tab sizes/wraps it after
- [x] Two pens and a highlighter, each its colour (8) and width in mm (− 0.5 mm +); stroke eraser; undo a stroke —
      [ ] Select/lasso, more colours, editing a drawing again after it is placed (it is a picture then)
- [ ] Later: ink over the text itself (floats on the page: needs floating objects; Word saves it as InkML); Ink to
      Shape / Ink to Math / Ink Replay not planned

### Design (Word's Design tab; OnlyOffice Σχεδίαση)
- [x] APA 7 student paper (Design › APA 7 paper, asked 2026-09-30): Times New Roman 12, 1-inch margins, double spacing with no space between paragraphs, first lines 0.5 in, the page number top right, APA's heading levels 1–5 as Heading 1–5, references on a new page; optionally clears fonts/sizes/spacing set by hand and adds the title page (title, name, department & university, course, instructor, date) with the title again over the text — [ ] APA's abstract page, the professional paper (running head), Level 4–5 run-in headings
- [x] Page background: watermark (Word's six words or your own; remove) — as Word's WordArt in the header / LibreOffice's fontwork in each page style; page colour (Word's light tints); page borders (a box: thin, medium, thick); a picture watermark (washed out, Word's VML imagedata / LibreOffice's frame in colour mode "watermark", 14 Oct) — [ ] border styles and colours, borders on some sides only
- [x] Paragraph spacing presets for the whole document (No space, Compact, Tight, Open, Relaxed, Double) — the Normal style's
- [ ] Style sets, theme colours and fonts, themes — rarely used; last

### Layout (Word's Layout; OnlyOffice Διάταξη)
- [x] Margins (Normal = 1 in/APA, Narrow, Moderate, Wide, 2.5 cm, or custom in cm/in), orientation, size (A4, Letter, Legal, A5, A3) — Layout tab, one Page setup page; every section of a Word file (pgSz/pgMar, header distances kept), every page layout of an ODT (the header band allowed for); a tablet shows a page too wide smaller rather than reflowing
- [x] Columns (Layout › Columns: one, two, three — Word w:cols / ODT style:columns, every section; the pages flow text down each column, a page break still starts a page; the editor keeps one column) — [ ] column break, balancing a continuous section, columns in part of a document
- [x] Page break, blank page (also on Layout) — [ ] column, section breaks
- [ ] Line numbers, hyphenation
- [ ] Indent left/right and spacing before/after as number boxes (as OnlyOffice; Home's ⋯ page has them already)
- [x] Arrange (for floating objects): position, wrap, bring to front / send to back, align to the margins (14 Oct) — [ ] group, free rotation

### References
- [x] Table of contents (References: from Heading 1–3, at the cursor; Update table rewrites it in place with fresh page numbers) — written as plain paragraphs in the file's TOC 1–3 / Contents 1–3 styles with a dot leader, not a field: Word/LibreOffice show it as written — [ ] clickable entries, levels chosen
- [x] Footnotes and endnotes (References › Insert footnote / Insert endnote): the number in the text (superscript, numbered in order — endnotes i, ii…), the note's words in a bar at the cursor and a dialog (Edit, Delete note); on the pages at the foot of their page under a short line, endnotes after the text; kept as each program keeps them (Word's footnotes.xml/endnotes.xml, LibreOffice's text:note) — a file with notes no longer copy-only — [ ] the note's own formatting when its words are edited (edited notes become plain), numbering formats other than 1/i, footnotes in columns
- [x] Citations and bibliography, APA 7 (References › Insert citation, Sources, References list / Update references, Update citations): Word's form — the kind of source, then its fields; APA writes the citation "(Smith & Jones, 2019, p. 47)" and the reference entry. Kept as each program keeps them: Word's CITATION fields and its sources part (Source Manager); LibreOffice's bibliography marks ; report, thesis, video sources (14 Oct); **a citation tapped** shows a bar — Edit each of its sources, Change… its sources or page (15 Oct) — [ ] other styles (the user: APA 7 only for now), a citation's own options (no author, suppress year)
- [x] Captions, cross-references (References › Caption, Cross-reference, Update fields — 14 Oct)

### Review
- [x] Spelling (Android's spell checker, Greek and English): wavy line, suggestions at the cursor, Next mistake, on/off — [ ] Tetra's own dictionaries (works without Gboard)
- [x] Word count (File › About this file) — [ ] counter in the tablet's top bar, the selection's counts, characters (asked 30 Sep)
- [x] Comments: read, add, delete, reply, resolve / reopen (Word's commentsExtended; LibreOffice's loext:parent-name / resolved), edit a comment's words (14 Oct)
- [x] Track changes in Word files (Review tab): shown as Word's All Markup (inserted underlined, deleted struck through, in the author's colour; a bar beside changed lines; ¶ for split/joined paragraphs), the change at the cursor named (what · who · when), Previous / Next, Accept / Reject (then on to the next), Accept all / Reject all (tables' row and formatting changes too); kept on saving — a corrected .docx is no longer copy-only ; **recording** (Review › Track changes, Word files: typing is your insertion, Backspace/Delete strike through and walk on past, Enter/joins are paragraph marks, under your name — asked once) — [ ] tracking remembered in the file (Word's trackRevisions), the typed insertion's colour before the next redraw (underlined at once), recording pasted/Writer-made changes, ODT's tracked changes (still copy-only), reject restoring a paragraph's style/list
- [ ] Read aloud (Android text-to-speech)

### View
- [x] Zoom
- [ ] Fit to width; multiple pages
- [ ] Reading mode (no ribbon, bigger text)
- [x] Navigation pane: jump to a heading

### On the phone
- [x] Ribbon at the bottom; a strip on the keyboard; the tools in pages with dots (asked 2026-09-27)
- [ ] Keyboard strip like Word's phone bar: B · I · U · highlighter · ⋯ · hide keyboard
- [ ] Dictation (Android voice typing into the page)

## Excel (Calc) — after Writer and PowerPoint; a viewer first (the user, 30 Sep)
- [ ] **Viewer first**: an .xlsx / .ods read comfortably on the phone (sheets as tabs, cells, widths, number formats)
- [ ] **Fuzzy search** across the cells (typos and accents forgiven, Greek and English)
- [ ] **Filter rows by a column**: a tap on the column's header opens its filter (values to keep), as Excel's does
- [ ] **Pin columns side by side**: choose columns (e.g. A, J, ST) — they come to the start one after the other, in the
  order pinned, the rest scrolling beside them, to compare them
- Then, later: the editing items below

- [ ] Open .xlsx / .ods: sheets as tabs, cells, column widths, number formats
- [ ] Edit cells; formula bar; insert/delete rows and columns
- [ ] Formulas: SUM, AVERAGE, COUNT, MIN, MAX, IF, VLOOKUP, dates, text; recalculation
- [ ] Cell formatting: bold, colour, fill, borders, alignment, number/currency/date/percent
- [ ] Sort and filter (by value, by colour); freeze panes
- [ ] Charts: column, bar, line, pie, scatter (OfficeCharts.kt reads and draws DrawingML charts: Excel's are the same)
- [ ] New sheet, rename, reorder; Find
- [ ] Save back into the original package (same rule as Writer: copy when something would be lost)

## PowerPoint (Presentation) — after Excel

- [ ] Open .pptx / .odp: slides, text boxes, pictures, notes
- [ ] Slide thumbnails; add, duplicate, reorder, delete slides
- [ ] Edit text on a slide; layouts and themes from the file
- [ ] Insert picture, shape, table
- [ ] Transitions and animations (show; later edit)
- [ ] Present: full screen, swipe to advance, pen and laser on the slide, blank screen, speaker notes
- [ ] Present to a TV (Android's cast / HDMI)
- [x] Export PDF (File): A4, 2 cm margins, beside the document — laid out by Android, not by Word

## To test on the phone, with the user

Each is done on the emulator; tick when checked on the real phone.
- [ ] Home: orange Office group (icon, New document, Documents), purple Text editor group, their Settings cards
- [ ] Office home: header icon; Word blue, Excel green, PowerPoint deep orange; the documents list
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
- [ ] Αναπιρία.odt (or any of his): edit + Enter + Save → open on the computer: same layout, spacing, lines, bullets

## Sources

- [Microsoft Word for Android (and iOS) cheat sheet — Computerworld](https://computerworld.com/article/3481618/microsoft-word-for-android-and-ios-cheat-sheet.amp.html)
- [How to Use Excel on Android Phones — Noble Desktop](https://blog.nobledesktop.com/how-to-use-excel-on-android-phones)
- [Microsoft Excel — Google Play](https://play.google.com/store/apps/details?id=com.microsoft.office.excel&hl=en)
- [PowerPoint for Android — BrightCarbon](https://www.brightcarbon.com/blog/powerpoint-for-android/)
- [Add animation effects in PowerPoint on a mobile device — Microsoft Support](https://support.microsoft.com/en-us/powerpoint/add-animation-effects-in-powerpoint-on-a-mobile-device)
- The user's Word screenshots (27 September): top bar, Home ribbon, Insert and Draw panes, History, the phone keyboard bar
