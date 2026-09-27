# Office — the features, one by one

What Microsoft Office's phone apps do (researched 27 September 2026: Word, Excel and PowerPoint for Android), as a
checklist for Tetra's Office. Tetra is local — nothing that needs Microsoft's cloud (live co-editing, public links,
Copilot). Each item: **done**, **next**, or later. Build them in order, one at a time; each one is tested on the
emulator and on the phone, and a file saved with it opens right in Word / LibreOffice.

Rule for every feature: a file part Writer can show *and* save is no longer "copy-only" — each item below that adds
such a part also moves it out of `OfficeDoc.blocking`.

## Word (Writer)

### Top bar
- [x] Back, name (• unsaved), Undo, Redo, Save
- [ ] Find and Replace (match case, whole words)
- [ ] Share (the file, or as PDF)
- [ ] Mobile view (text reflows to the phone) / Print layout (the page as it prints)
- [ ] ⋯ menu: Save as, Rename, Print, Properties

### File
- [x] Save over the file when nothing is lost; else a copy beside it
- [x] New documents: .docx (or .odt); Save as with a name, in Tetra/Documents
- [x] Unsaved changes kept as a draft; leaving asks Save / Don't save
- [x] The previous version kept before each save (last 3) — **next: a History screen to open or restore one**
- [ ] Export as PDF
- [ ] AutoSave (on by default in Word): save by itself after a pause, for files that are not copy-only
- [ ] Open .doc / .rtf (read; save as .docx)

### Home
- [x] Bold, Italic, Underline, Strikethrough
- [x] Text size −/+, text colour, highlight
- [x] Styles: Normal, Heading 1–3 (**next: the document's own heading sizes**)
- [x] Bullets, numbering, indent −/+
- [x] Alignment: left, centre, right, justify
- [ ] Font name (the document's fonts, kept on save)
- [ ] Superscript, subscript; small caps; clear formatting
- [ ] Line and paragraph spacing (space after each paragraph, as Word shows)
- [ ] Line break inside a paragraph (Shift+Enter) — the commonest copy-only reason in the user's files
- [ ] Format painter (copy formatting)
- [ ] More styles: Title, Subtitle, Quote, Heading 4–6

### Insert
- [x] Link
- [ ] Table (insert, add/remove rows and columns, cell text)
- [ ] Picture (from Gallery or camera; size, move, wrap)
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
- [ ] Word count (words, characters, paragraphs)
- [ ] Comments: read, reply, delete
- [ ] Track changes: show, accept, reject, record
- [ ] Read aloud (Android text-to-speech)

### View
- [x] Zoom
- [ ] Fit to width; multiple pages
- [ ] Reading mode (no ribbon, bigger text)
- [ ] Navigation pane: jump to a heading

### On the phone
- [x] Ribbon at the bottom; a strip on the keyboard
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
- [ ] Export as PDF

## Sources

- [Microsoft Word for Android (and iOS) cheat sheet — Computerworld](https://computerworld.com/article/3481618/microsoft-word-for-android-and-ios-cheat-sheet.amp.html)
- [How to Use Excel on Android Phones — Noble Desktop](https://blog.nobledesktop.com/how-to-use-excel-on-android-phones)
- [Microsoft Excel — Google Play](https://play.google.com/store/apps/details?id=com.microsoft.office.excel&hl=en)
- [PowerPoint for Android — BrightCarbon](https://www.brightcarbon.com/blog/powerpoint-for-android/)
- [Add animation effects in PowerPoint on a mobile device — Microsoft Support](https://support.microsoft.com/en-us/powerpoint/add-animation-effects-in-powerpoint-on-a-mobile-device)
- The user's Word screenshots (27 September): top bar, Home ribbon, Insert and Draw panes, History, the phone keyboard bar
