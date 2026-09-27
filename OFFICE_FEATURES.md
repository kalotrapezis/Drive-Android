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

### Top bar
- [x] Back, name (• unsaved), Undo, Redo, Save
- [x] Find and Replace — **improve: the match is not highlighted while the Find box has the keyboard; match case / whole words**
- [x] Share the file (as last saved) — as PDF with Export
- [ ] Mobile view (text reflows to the phone) / Print layout (the page as it prints)
- [ ] ⋯ menu: Save as, Rename, Print, Properties

### File
- [x] Save over the file when nothing is lost; else a copy beside it
- [x] New documents: .docx (or .odt); Save as with a name, in Tetra/Documents
- [x] Unsaved changes kept as a draft; leaving asks Save / Don't save
- [x] Versions (File): the file as it was before each of the last 3 saves; Restore keeps the current one as a version too
- [x] Export PDF (File): A4, 2 cm margins, beside the document — laid out by Android, not by Word
- [ ] AutoSave (on by default in Word): save by itself after a pause, for files that are not copy-only
- [ ] Open .doc / .rtf (read; save as .docx)

### Home
- [x] Bold, Italic, Underline, Strikethrough
- [x] Text size −/+, text colour, highlight
- [x] Styles: Normal, Heading 1–3 (**the library fixes heading sizes (H1 = 2× text); show the document's own — workaround needed**)
- [x] Bullets, numbering, indent −/+
- [x] Alignment: left, centre, right, justify
- [x] Fonts kept letter by letter on save (the editor shows its own font) — [ ] choose a font from the ribbon
- [ ] Superscript, subscript; small caps; clear formatting
- [x] Paragraph formatting kept: spacing, indents, tab stops, borders, page break before, styles such as Title/Quote, one font per paragraph, the file's own lists — carried through edits (a new paragraph takes the one before it), checked pixel-identical in LibreOffice on 6 of his files
- [ ] Paragraph formatting *shown* on the page and *changed* from the ribbon (spacing, indents) — the editor library has no paragraph spacing
- [x] Line break inside a paragraph (Shift+Enter; Insert › Line break) — kept in .docx and .odt
- [ ] Format painter (copy formatting)
- [ ] More styles: Title, Subtitle, Quote, Heading 4–6

### Formatting pages (asked 2026-09-27: "set everything once, then type" — for big work like a thesis)
- [ ] **Styles**: long press a style (Normal, Heading 1–3, Title, Quote…) → its page: font, size, bold/italic, colour,
      alignment, line spacing (single · 1.15 · 1.5 · double), space before/after, first-line indent — changes the
      document's own style, so every paragraph with it changes, in Word/LibreOffice too
- [ ] **Line spacing** quick button on Home (1 · 1.15 · 1.5 · 2)
- [ ] **⋯ at the end of Home → Paragraph page**: the same settings for this paragraph only, and "Edit its style"
- [ ] **Page format**: margins, orientation, paper size (Layout)
- [ ] **Table format** (with tables)
- [ ] **Bibliography / citations** (references list, citation style — APA etc.)
- [ ] The editor cannot show space between paragraphs (library limit); what is set is saved and shows in Word,
      LibreOffice and Export PDF

### Insert
- [x] Link
- [ ] Table (insert, add/remove rows and columns, cell text)
- [x] Pictures, charts, shapes, text boxes in a file: kept exactly on save (no longer copy-only)
- [x] Pictures shown on the page (Writer draws them over the text; a chart or shape is a grey box; a floating picture shows in line for now)
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
- [ ] Page break
- [ ] Header and footer; page numbers
- [ ] Footnotes and endnotes
- [ ] Comments
- [ ] Shapes, text box
- [ ] Symbols, date and time
- [ ] Table of contents (from the headings)

### Draw
- [ ] Pen, pencil, highlighter with the finger; colours and thickness
- [ ] Stroke eraser, lasso select
- [ ] Ink saved into the document (Word ink / an image)

### Layout
- [ ] Margins, orientation, paper size (today kept from the file, not changeable)
- [ ] Columns
- [ ] Page break / section break

### Review
- [ ] Spelling (Android's spell checker)
- [x] Word count (File › About this file)
- [ ] Comments: read, reply, delete
- [ ] Track changes: show, accept, reject, record
- [ ] Read aloud (Android text-to-speech)

### View
- [x] Zoom
- [ ] Fit to width; multiple pages
- [ ] Reading mode (no ribbon, bigger text)
- [ ] Navigation pane: jump to a heading

### On the phone
- [x] Ribbon at the bottom; a strip on the keyboard; the tools in pages with dots (asked 2026-09-27)
- [ ] Keyboard strip like Word's phone bar: B · I · U · highlighter · ⋯ · hide keyboard
- [ ] Dictation (Android voice typing into the page)

## Excel (Calc) — after Writer

- [ ] Open .xlsx / .ods: sheets as tabs, cells, column widths, number formats
- [ ] Edit cells; formula bar; insert/delete rows and columns
- [ ] Formulas: SUM, AVERAGE, COUNT, MIN, MAX, IF, VLOOKUP, dates, text; recalculation
- [ ] Cell formatting: bold, colour, fill, borders, alignment, number/currency/date/percent
- [ ] Sort and filter (by value, by colour); freeze panes
- [ ] Charts: column, bar, line, pie, scatter
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
