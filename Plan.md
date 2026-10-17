# Plan — the common notepad (every agent reads this first)

The user, 17 Oct 2026: one place where every agent (Claude session) sees what is planned, what is being done, what
is done and what was decided against. **Read it before starting; write here when you start, finish or drop
something.** Details stay where they live (HANDOFF.md for how things were built, OFFICE_FEATURES.md for what Office
can do, SYNC_PLAN.md for sync's rules, TEST_TREE.md for checks); this file is the list and the state.

How to write here:
- `- [ ]` planned · `- [~] (session name, date)` being done now · `- [x] (commit)` done · `- [-] (why)` dropped.
- One line per item where possible; the date as "17 Oct". Never delete the user's words; move done items down
  rather than erase them. Commit only this file's lines you wrote (other sessions edit it too).
- The user's own original wishlist (Sept 2026) is at the bottom, unchanged.

## Now — who is doing what
- [x] (813e7c4, 18c031f) Office: one tab row for Words, Slides and Tables; Settings › Tab marker: Bubble / Dash; the
      bubble in each app's colour (office tab-marker session, 17 Oct).
- [x] (386b04c) A: tab names and order alike in Words, Slides, Tables (office-parity builder, 17 Oct).
- [x] (4a6d7b1) A: the same File in all three; Slides gets Versions, Export PDF, Print, About, Send log (office-parity builder, 17 Oct).
- [x] (5e8b4e8) A: an icon on every action in Tables' and Slides' ribbons (office-parity builder, 17 Oct).
- [x] (66b4b5a) A: strips open alike, ‹ and a name in the ribbon's place (office-parity builder, 17 Oct).
- [x] (4ddb8da) A: one set of colours, one font button, one size stepper (office-parity builder, 17 Oct).
- [x] (c00f9ff) A: the top bar alike; Slides keeps a recovery draft (office-parity builder, 17 Oct).
- [x] (20e0b17) A: shortcuts alike — Slides' Ctrl+C/X/V, Tables' Delete (office-parity builder, 17 Oct).
- [x] (f65b50a) A: messages alike; Slides' ribbon no longer goes after Save on a tablet (office-parity builder, 17 Oct).
- [x] (1e0160a, be4d2e6, 7549d88, 100300f, 8c052fb) The Office engine, steps 0–4 (office-engine session, 17 Oct); step 5
      (the pure move to OfficeText.kt) left for a quiet moment; the tablet walk waits for the tablet.
- [x] The phone's own fonts (the user, 17 Oct: "draw a document's font with the phone's own when it has it, and add a
      Phone fonts group to the shared font picker"; "next to font a search bar would be nice") (office-engine session):
      `OfficeFonts` finds the phone's families by the name inside each font file (`familyName`, `covers`: the name and
      cmap tables) and uses one of a document's font's name before Word's twins and the guesses (pages and editor alike);
      the shared font strip (Words, Tables) lists "Phone fonts" after the document's and Word's — those with Latin
      letters of their own, ~45 on the tablet (Roboto, Noto Serif, MiSans, Dancing Script, Coming Soon…) — and has a
      "Search fonts" field beside its name (over the cards on a phone). Words' fingerprint unchanged on the emulator and
      the tablet; `OfficeFontsTest`; the user tried it on the tablet ("I used it it works"). Not done: a name typed that
      is not in the list cannot be used as a font (Word allows it); Slides gets the strip with B's Home.
- [~] (calendar session, 17 Oct) **Calendar**, Tetra's last app: designed below (§ Calendar); waiting for the user's
      go-ahead before building. Builds on its own branch `calendar` (worktree Drive-Android-worktrees/calendar, from
      writer-library) and the desktop's `calendar` (worktree Drive-worktrees/calendar), not in the Office checkout.
- [~] (office-engine session, 17 Oct) B top-down on the engine, then C, then A's walk (the user: "ok comit and continue to finish office").
      Done here: Home (0ce3c4d), Insert › Text box (17b9bfb), Drop cap strip + Raised (c9c4683); handing on (notes:
      office-engine-17oct.md in the group's notes folder). Tablet in use (Crunchyroll) 15:00: these three seen on the emulator only.
- [~] (office-B session, 17 Oct, from office-engine's hand-over) B on from Dropped drop cap: the rest of Insert, Arrange, Slide,
      Design, Draw, Review, View, File, Slide show, References; then C, then A's walk. Tablet: Crunchyroll in front 15:35 — emulator.
      Done here: Dropped (5bf71af), Chart (1617bd3), Link (6ae8501), Header & footer (9578abf), Symbol/Date/Line break/Equation
      (4e68384). Unit suite 415/0/7; sweep 349 + 29, none failing (after 9578abf). Tablet still Crunchyroll at 16:22: all on the
      emulator only. Handing on (notes: office-B-17oct.md in the group's notes folder); next WordArt, Comment, then Arrange.
- [~] (office-C session, 17 Oct, from office-B's hand-over) B on from WordArt: Comment, Arrange, Slide, Design, Draw, Review, View,
      File, Slide show, References; then C, then A's walk. Done here (19:05): WordArt, Comments, Arrange (order, align, turn, flip, size,
      Shape options, alt text, crop, replace), slide sorter, Layout, sections, Design colours/fonts/background, Draw, Outline, Notes page,
      Gridlines, Find and Replace, Export pictures, handouts, loop, rehearse, show keys, show ink kept, References (sources, citations,
      References and Contents slides), Read aloud, File (Save a copy/as, Rename, AutoSave, template, Inspect), object links with zoom,
      Video — all on emulator-5554 (the tablet was not connected). Unit 430/0/7, sweep 349 + 29. Handing on (office-C-17oct.md).
- [~] (office-D session, 17 Oct, from office-C's hand-over) Words' Online video, then B's two long lists, then C (Tables onto the engine),
      then A's walk. Tablet: Firefox in front at 19:40 — emulator. Done here (20:40): Words' video and in-frame playing (Words and Slides),
      Slides' Selection tab, X²/X₂ and Aa, Table tab, a text box's Layout, Animation pane and painter, show taps while a video plays;
      then (21:00) the Selection pane and Picture styles / Transparency. Unit 435/0/7, sweep 349 + 29 none failing (20:56, after d782c1e).
      Handing on (notes: office-D-17oct.md in the group's folder): the leftovers next touch Words' shared dialogs and drawing.
- [x] (office-F session, 17 Oct evening → 23:40, the only writer in this checkout; took over the stopped office-E's half-done dash/shadow)
      B's leftovers all done: outline dash/shadow (19f4206); several chosen, Select all, Group / Ungroup, Distribute, Reset slide
      (4847685); slide size (396a8f8); gradient backgrounds (1be3318); a deck's own tables (1a9db80); multilevel list and Paragraph
      (ba2ea50). Engine step 5 (103a2b8) and step 6 for .xlsx (7ad47c7). C: Replace, Merge & centre, Copy/Paste formatting, Fill, Symbol,
      Chart from cells, widths/heights/Hide, Link, Remove duplicates, Text to columns, Insert function, sheet Hide/Unhide, Gridlines,
      a protected sheet respected (71c2da6 … f0e7d60). A: the touch-target script and its findings (0fca8e1; a question for the user).
      Final checks (23:40): unit suite 454, 0 failing, 7 skipped; sweep 349 + 29, none failing; Words' fingerprint unchanged (emulator).
      All checked on emulator-5554 only — the tablet was not touched (the user asleep). Hand-over: office-F-17oct.md in the group's notes.
      **For the tablet in the morning (971f6b37), each opened once:** Slides › Shape options Dash/Shadow on a shape; Select all → Group →
      drag → Ungroup; Distribute; Home › Reset; Design › Slide size and Gradient; a deck's own table's Row below; Home › Multilevel and
      Paragraph; Tables › a rich-text cell, Replace, Merge & centre, Chart, Width/Hide, Link, Remove duplicates, Text to columns, Insert
      function, sheet Hide, Gridlines, a protected sheet; Words › Shape options Dash/Shadow. And the touch-target script on the tablet.
- [~] (office-F session, carried on 18 Oct after midnight, still the only writer) C: Page layout (728b2a5), Notes (464a338), Drop-down
      lists (ac405b3), Fill ▸ Series and month/day names (8be8075), Cell styles (a925139), Format as table (dafa7bc), Conditional
      formatting (17b4bbf), Protect / Unprotect (9f2dd9e), Headings (541d8c9),
      Print titles (420575e), Header and footer (b66a9cb),
      engine step 6 for .ods (rich text) (95a08d5), Home's alignment (aec634f), Clear ▸ (e6b8549), sheet copy
      and tab colour (a7f9980), cells shifted (ce1b197),
      Picture and Text box (5001224), Sort (d275c8d),
      AutoFilter kept. Checks at a925139: unit
      suite 460, 0 failing, 2 skipped; sweep 349 documents + 29 decks, none failing. With the .ods rich text (01:00): unit 464, 0
      failing, 2 skipped; sweep 349 + 29, none failing. At 22bebc0 (01:40): unit 471, 0 failing, 2 skipped; sweep 349 + 29, none
      failing. Emulator only. For the tablet too: Tables › Page
      layout, Note, Drop-down list (its values row under the ribbon), Series, Cell styles, Format as table, Conditional formatting, Protect / Unprotect and Lock cell, View › Headings, Page layout › Print titles, Header and Footer (and Export PDF), Home's alignment and Clear ▾, a sheet's Copy and Tab colour, Insert/Delete cells, Insert › Picture and Text box, Data › Sort, Keep filter in file.
- Other sessions: add your line here when you start.

## Waiting on the user
- (office-F, 17 Oct night) **Touch targets** (A's script, details under A › Touch points): the tab row's tabs are 41–43 dp tall and the
  icon-only ribbon buttons 40–46 dp wide, under Android's 48 dp. Make them 48 dp (the ribbon ~5 dp taller; icon buttons' circles
  as now, 8 dp more room round each), or keep the present compact look?
- (none else now — the .odp's sources: answered 17 Oct, see B › References)

## Office: Slides and Tables as full as Words, then a UI/UX pass over all three (planned 17 Oct; moved here from OFFICE_FEATURES.md)

**Why (the user, 17 Oct):** Slides' and Tables' ribbons look empty next to Words'. Words was built over weeks with the
user at every step; the other two were built by Claude alone. On 17 Oct the ribbons held: Words 12 tabs, 127 buttons;
Tables about 7 tabs, 74; Slides 8 tabs, 35. The lists below come from what the desktop apps offer (PowerPoint and
Excel, LibreOffice Impress and Calc), cut to what makes sense on a tablet and phone without a cloud. **Order: the
UI/UX pass's shared pieces first (so new tools land on one base), then Slides, then Tables, each list top-down.**
Every item is checked on the tablet and phone and against LibreOffice, as Words' were.

### A. UI/UX pass — how all three are tested, and what is made the same
**How it is tested** (written up as a TEST_TREE-style walk, ticked per app):
- [ ] **Every tab, every button**, on the tablet (971f6b37) and the phone: ribbon at the bottom and at the top,
      normal and compact, finger and mouse. Each must work, show what it did, undo, and survive a save (the saved file
      opened in LibreOffice).
- [ ] **Lag**: each action timed in writer.log (Tables already logs slow frames; Words and Slides to do the same) and
      `dumpsys gfxinfo … framestats` around it. Aims: a tap answered within 100 ms, no frame over 32 ms while
      scrolling, a slide or sheet switch within 200 ms. The worst ten fixed first.
- [~] **Touch points**: a script dumps each screen's layout (uiautomator) and flags any tappable thing smaller than
      48 dp (compact mode excepted only with a mouse). Plus a hand check of resize handles, small chips and close
      buttons, things near the screen's edges (Android's back gesture), and gestures that fight (pinch against drag).
      Script done and run (office-F session, 17 Oct, emulator 1280×800 at 160 dpi, editing, every ribbon tab): `tools/touch-targets.py
      <device> <tab>…`. Words 143, Slides 125, Tables 52 small targets (lists: touch-targets/ in the group's notes folder). The same few
      shared things every time: the tab row's tabs 41–43 dp tall (`OfficeTabRow`); icon-only tools 40–46 dp wide (`Tool` 40 dp, `Swatches`:
      Bold, Italic, Text colour, Fill, Fewer decimals…); the top bar's Undo, Redo, Save, Find 40 dp; Words' References › 1 2 3 35 dp; an
      unnamed 25–41 dp thing at the ribbon's right edge in Words' Home, Layout, Review and Slides' Insert (the edge: Android's back
      gesture). Not changed: making them 48 dp changes the ribbon's look — asked under Waiting on the user. Hand check: on the tablet.
- [ ] **Inconsistencies**: the checklist below, the three apps side by side on the tablet. Each difference is made
      the same or written down as deliberate.

**Made the same** (one shared component where today each app has its own code):
- [x] **The tab indicator**: Words drew a bubble behind the chosen tab; Tables and Slides a dash under it. Now one
      shared tab row for all three (`OfficeTabRow`, OfficeUi.kt: each app's tabs, its dash colour, the contextual
      tab's own colour, the chosen tab scrolled clear of the edge's shade). **Settings › Office and File › Settings:
      "Tab marker: Bubble / Dash"** (bubble by default, as Words had it). Compact always uses the dash, for the room.
      Checked on the tablet in all three apps, both markers, normal and compact (17 Oct).
- [x] (386b04c) Tab names and order: Selection first when something is chosen, then File, Home, Insert, … as Words'; the
      contextual tab (Picture, Table, Column, Row, Sheet) comes by itself, in one place and colour in every app.
      Done: Slides' "Slide" tab is Home (PowerPoint's Slides group is on Home); what is chosen on a slide has its own
      last tab named by it (Text box, Image, Table, Chart, Shape) — Slides' Selection tab is left for Cut/Copy/Paste
      (B); the contextual tab is a lighter tone of each app's colour. The picture's tab is **Image** in Words and
      Slides (the user, 17 Oct: "It should be Image not Picture" — 386b04c had renamed it, put back). Tablet 17 Oct.
- [x] File holds the same items in every app: Save, Versions, Export PDF, Print, Share, Open in another app, About
      this file, Send log, Settings. Slides lacks Versions, Export PDF, Print, About and Send log today; the checklist
      above says Slides' Export PDF is done, but its File tab has no such button, so check that line.
      Done: Slides had no Export PDF (the ticked line was Words'); now all of them (`Slides.pdf`: a page a slide at the
      deck's size, hidden slides left out; Versions opens one unsaved, Save puts it over the file), one shared
      `VersionsDialog` for Tables and Slides (Words keeps its page previews), the same order in all three, Words gains
      Open in another app. Tablet 17 Oct: PDF of 4 slides; a version opened and saved, LibreOffice opens it.
- [x] Every button has an icon (memory: ribbon icons); the everyday tools on the ribbon and the rest under ⋯ on a
      full page (memory: ribbon ⋯ button) — in Slides and Tables too.
      Done: every action in Tables and Slides has one (new: filter off, fewer/more decimals; A−/A+ as Words' − size +);
      choices stay words as Words' style names do (speeds, sides, transition kinds, number formats, table sizes). No
      Tables or Slides tab overflows yet, so nothing is behind ⋯ — B and C add tools under that rule. Tablet 17 Oct.
- [x] Strips (Words' Page numbers, Tables' Borders and Number) open the same way: ‹ back, a title, sliding in.
      Done: one `StripHead` (‹ and the name) for Tables' Borders and Number and Slides' Transition options, Effect and
      Effect options, which now take the whole ribbon's place and slide in as Tables' do. Deliberate difference: Words'
      galleries (Page numbers, Fonts, Colours, WordArt…) keep their name with Cancel and OK — they preview on the page
      and need OK. Checked on the emulator (1280 dp) 17 Oct; the tablet was in the user's hands.
- [x] One colour picker (Words' swatches; Slides has its own `SLIDE_COLOURS`), one font picker, one size picker.
      Done: the picker (`Swatches`) and the font gallery were already shared; now the lists are too — `TEXT_COLOURS`
      (white added, for dark fills and slides), `HIGHLIGHT_COLOURS`, `CELL_FILLS` (Words' table shading and Tables'
      Fill) and Words' `SHAPE_COLOURS` for a slide's shapes (`SLIDE_COLOURS` gone); `FontButton` and `SizeStepper` in
      Words and Tables (Tables: font then size, as Words'). Slides gets them with B's Home. Emulator 17 Oct.
- [x] The top bar: Undo, Redo, Save and Done in the same places; the status text (counts, Saved / Not saved) worded
      alike. View-only on opening with Edit, the Save / Don't save question on leaving, recovery drafts and versions
      behave the same in all three.
      Done: Words' eye ("Pages", before Find) is Done ✓ at the far right, where Edit stands when viewing, as in Tables
      and Slides; the unsaved dot before the name in all three (Words' "• name"); Words' status ends with the file type
      as the others' (".docx"); **Slides keeps a recovery draft** when sent to the background with changes (it had
      none): reopened, "Your unsaved changes are back", unsaved — Save keeps them, Don't save drops them. View-only on
      opening, the leave question and Versions were already alike. Emulator 17 Oct (draft: hid a slide, Home, killed
      the app, reopened, saved — LibreOffice skips the hidden slide).
- [x] Keyboard shortcuts and right-click menus the same where the action is the same (Ctrl+F, Ctrl+C/V, Delete).
      Done: Slides takes Ctrl+C, Ctrl+X, Ctrl+V for what is chosen on a slide (as its right-click menu); Tables' Delete
      empties the chosen cells, as Excel's. All three: Ctrl+S, Ctrl+Z, Ctrl+Y / Ctrl+Shift+Z. Ctrl+F: Words and
      Tables; Slides gets it with B's Find and Replace. Right-click menus were already one component (`OfficeMenu`) with
      Cut, Copy, Paste first in Tables and Slides; Words' text uses Android's own menu (deliberate: the system's text
      selection). Emulator 17 Oct.
- [x] (16 Oct, `RibbonRows`) A ribbon on rows on a big screen: with the ribbon at the top on a screen 840 dp or wider,
      each tab's tools go on as many rows as they need, a whole group moving to the next row (never one tool). The
      user, 17 Oct, shown it: **keep it in all three apps** — new tools in B and C go through `RibbonLine`, which does it.
- [x] Messages, empty states and dialogs in one style; English only (memory: UI English only).
      Done: one `OfficeMessage` — Words' black pill at the top for 2.8 s, written in the log — in all three (Tables'
      was a grey box at the bottom for 3.5 s, Slides' a see-through box for 2.5 s). Dialogs were already Material's
      AlertDialog everywhere; "Opening…" and "could not be opened" already worded alike; all English.
      Also (the user, 17 Oct: "Slides' ribbon does not stick"): on a tablet Save and Delete slide left no slide
      chosen, so Slides' ribbon went away; now the slide stays chosen. Emulator 17 Oct.

### B. Slides — what PowerPoint and Impress have that Slides lacks
Foundation first: **a slide's words as runs** (today an edited paragraph is one line in its first run's look,
`SlidesEdit.withTexts`) — needed by most of Home and by References. **The user, 17 Oct, changed this into the shared
engine** (below, "The Office engine"): no third text model — Words' paragraphs and runs, their layout, drawing and
effects become one engine that lays text out in any frame (a page, a slide's box or shape, later a cell).

### The Office engine — B's first item (the user, 17 Oct: "build an engine, a base Microsoft feeds off for years; after that we build upon it")
**What it is:** one way to hold, lay out and draw text, used wherever text is — a Words page, a slide's text box or
shape, a table cell on a slide, later a spreadsheet cell. Movement, turning and animation belong to the frame (the
slide's box moves, the text goes with it), as `Slides.draw` already does for items.

**What the study found (17 Oct):** Words already has most of it. `OfficePages.stack()` lays Words' paragraphs and runs
(`DocPara`/`DocRun`) into any width, and `TextBoxDrawable` puts the result in a frame (insets, top/middle/bottom,
WordArt bends) — that is how a document's text boxes are drawn today. Slides has its own simpler model (`SlideText`,
`SlidePara`, `SlideRun`) and its own drawing (Slides.kt `layouts()`/`text()`), which truncates sizes to whole points,
ignores justify, puts centred no-wrap text far off its box, re-lays all text on every frame, and loses mixed looks in a
paragraph when it is edited (`withTexts` keeps only the first run). Tables' cells have one look per cell and flatten an
.xlsx's rich text to plain. Words' typing editor (`WriterBridge`) stays as it is — it is not part of the page drawing.

**Steps, each one commit that leaves Words exactly as it was:**
- [x] 0. A check that Words is unchanged: a device test (installed + `am instrument`, never connectedAndroidTest)
      that lays out the test documents and a few with text boxes, and compares each page's lines and a picture of it
      with a stored copy. No unit test touches the drawing today, so this comes first (~80 lines).
      Done: `WordsFingerprintDeviceTest` — the six Word suite files, Test-Words' TextBox, Art, Gallery and Tetra test,
      group.odt and sections.odt (copies in androidTest/assets/fingerprint): 25 pages, each page's pieces, line ends,
      floats (a text box's lines too) and an MD5 of the page drawn at 2×, against `expected-<model>.txt` (emulator-5554
      stored; the same twice running). Run: assembleDebug assembleDebugAndroidTest, install both, `am instrument -w -e
      class com.kalotrapezis.drive.WordsFingerprintDeviceTest com.kalotrapezis.drive.test/androidx.test.runner.AndroidJUnitRunner`.
- [x] 1. Slides' text boxes and table cells drawn by Words' engine: `stack` shared; the frame gets four insets, no
      clipping (PowerPoint lets text overflow its box) and no default border; a list-marker hook so a slide's own
      bullet character and colour show; a `SlidesText.kt` adapter (a slide's text → `DocPara`s, its own `DocStyles`);
      laid text cached per box (slides are drawn often: thumbnails, animation). Fixes the four bugs above. ~120 new
      lines, ~45 removed, ~15 changed in OfficePages. **Risk:** line height — Words spaces lines as Word does, a few
      percent taller than Slides draws today; checked against LibreOffice's PDFs of real decks before it is committed.
      Done (office-engine session, 17 Oct): `SlidesText.kt` (a SlideText → DocParas with their own looks, stacked by
      `OfficePages.stack`, cached per box and size), `TextBoxDrawable.framed` (four insets, clip or not; Words' text
      boxes draw through it), DocStyles' `marker`/`markerLook` hooks (a slide's bullet character, colour, size share
      buSzPct and face buFont; LibreOffice's list font size and family). Read better too: PowerPoint's lnSpcReduction
      (was guessed from fontScale). Line height: PowerPoint's single line is 1.2 × the letters' size (LibreOffice draws
      it so), Word's the font's own — the adapter turns one into the other. Checked against LibreOffice's PDFs of 11
      decks (99 slides: the user's cronbach, lecture 3–4, counseling, phishing, ethismos, Test-Sheets' four, olive,
      slides): line breaks and line pitch now as LibreOffice's (before: lines 4–15 % apart, bullets as tofu,
      justify ignored, sizes cut to whole points); Words' fingerprint unchanged; unit suite 402 / 0 / 7; sweep 349 + 29,
      none failing. Not the engine's (seen in passing): LibreOffice re-fits normAutofit itself (lecture 3–4 bigger
      there; Tetra keeps PowerPoint's saved fontScale); small caps (cap="small") wait for step 3. Tool:
      `SlidesRenderDeviceTest` draws decks pushed to files/slides-in as PNGs.
- [x] 2. Text effects on slides: shadow, glow and outline read from PowerPoint's `a:effectLst`/`a:ln` into Words'
      `TextEffects`; WordArt bends already shared (`TextWarp`). ~40 lines.
      Done (office-engine session, 17 Oct): `SlideRun.effects` from the nearest effectLst/ln (an empty effectLst — as
      the user's jamovi decks have on every run — takes inherited ones away), LibreOffice's text-shadow/text-outline;
      drawn by Words' `EffectSpan` (the run's raw settings carry Word's w14 form). Also: an empty paragraph shows no
      bullet (PowerPoint's and LibreOffice's way; Slides drew one). `text effects read from a pptx` (text-effects.pptx).
      LibreOffice 26.2 draws a slide's text glow but not its shadow or outline from a .pptx; PowerPoint draws all three.
- [x] 3. Slides read straight into `DocPara`/`DocRun` (`SlideRun`/`SlidePara` gone): slides gain strike, sub- and
      superscript, caps, highlight, links, tabs, exact line spacing. ~150 lines.
      Done (office-engine session, 17 Oct): `SlideRun` gone — a slide's runs are `DocRun`s (face and letter looks in
      Word's form in `font`: rFonts, caps/smallCaps, dstrike, double underline, w14 effects; LibreOffice's attributes
      in an .odp); a `SlidePara` is now a `DocPara` with its `ParaLook` (spacing, indents, tab stops, exact lines) and
      `SlideBullet`. Read: strike (sngStrike, dblStrike), baseline (raised, lowered), cap (all, small), highlight,
      hyperlinks (in the theme's link colour; external targets were turned into package paths — fixed), a:tabLst,
      lnSpc spcPts; .odp: line-through, text-position, text-transform, font-variant, background colour, text:a,
      line-height in points. `letter looks read from a pptx` (text-looks.pptx). Of the 11 decks only the three that
      use these changed (cronbach's small-caps title, counseling's 19ου superscripts, jamovi's link colour), as
      LibreOffice shows them. Unit suite green; sweep 349 + 29, none failing; Words' fingerprint unchanged.
- [x] 4. Words kept per letter when a slide's text is edited (Words' `PictureXml.relaid`/`relook`, already used for
      a document's text boxes): an edited paragraph keeps its bold word and its red word; slide-number and date fields
      and line breaks kept. ~50 lines. This is "a slide's words as runs".
      Done (office-engine session, 17 Oct): saving (.pptx `SlidesEdit.withTexts`, .odp `SlidesOdpEdit` type/words) lays
      the lines over the paragraphs they came from with `PictureXml.relaid`, each letter in its run (a:r's rPr; an
      .odp's spans and links), a typed one in the look of the one before; a field left whole (a:fld, text:page-number,
      date…) stays the field, a:br/text:line-break stay (the typing field holds them as LINE_BREAK); several spaces as
      text:s. The slide shown while editing does the same (`SlidesEdit.slide`). Also: Slides now loads Tetra's fonts
      with Word's letter widths itself (only Words and Tables did: a deck opened first drew in the phone's fonts), and a
      font asked for before they are known is no longer remembered as missing. `an edited paragraph keeps each letter's
      look` (.pptx XML; LibreOffice's text-looks.odp). Emulator: cronbach slide 2 edited inside a bold word — the
      paragraph kept its bold, plain and italic words; saved, LibreOffice shows the same.
      After (17 Oct, steps 0–4): unit suite 405 tests, 0 failing, 7 skipped; sweep 349 documents and 29 decks, none
      failing; Words' fingerprint unchanged at every step (emulator-5554).
      Tablet 971f6b37 (17 Oct, 14:06–14:18): the Words fingerprint stored for it (expected-24091RPADG.txt: the same as the
      emulator's but for 3 page hashes, antialiasing); 117 slides of the 14 test decks drawn there identical to the
      emulator's but for antialiasing (no pixel off by more than 40/255), so they stand beside LibreOffice as above. In
      the app: the user's cronbach deck as LibreOffice shows it where the fonts are the same (slide 6 line for line;
      slide 3 wraps otherwise: LibreOffice has the real Century Schoolbook on the computer); Slides-test.pptx's Plan
      slide edited (a word typed, a new line: it took its level's dash and look), undone, not saved; Words' Art_test
      and TextBox_test and Tables' sheet.xlsx and Slides-test.odp as before. Seen in passing, not the engine's (its
      page is pixel-identical to before): TextBox_test's box has no bottom line on the page, the line after it covers it.
- [x] 5. The engine moved into its own file (`OfficeText.kt`): a pure move, nothing changed.
      Done (office-F session, 17 Oct): `ParaLook`, `MarkerLook`, `TabStop`/`tabGap`, the marker and tab spans, `TextBoxDrawable`, `Block`
      and `LetterLooks` moved from OfficePages.kt (U, topPt/bottomPt and the two spans now internal, as both files use them). The layout
      itself (`OfficePages.stack`, `paragraph`) stays in OfficePages: it shares some forty private helpers with Words' page layout, so
      moving it would not be a pure move. Words' fingerprint unchanged (emulator); unit 442/0/7.
- [~] 6. (with C) Tables' wrapped, shrink-to-fit and rich-text cells drawn by the engine; rich text read and written.
      Done (office-F session, 17 Oct): a cell's rich text (`SheetCell.runs`, Words' `DocRun`s; `rich` only while they still are its words)
      read from Excel's shared strings and inline strings (`Xlsx.RichRuns`: rPr b, i, u, strike, sz, colour, rFont), written back as
      Excel's runs when the cell is written (`SheetSave.runsXml`); rich, wrapped and justified cells laid out and drawn by the engine
      (`SheetPainter.cellBlock`: a paragraph a line, `OfficePages.stack` with a cell's own plain look, `TextBoxDrawable.framed` at the
      cell's vertical alignment; the old one-look StaticLayout gone); Shrink to fit read (Excel's shrinkToFit, LibreOffice's
      shrink-to-fit) and drawn (smaller letters until they fit). `rich text in a cell` (shared, Tetra's inline, LibreOffice's own save);
      emulator: "Na" bold, "me" red italic 14 pt; sheet.xlsx's merged wrapped cell in its row. The user's 31 workbooks all read.
      .ods done (office-F session, 18 Oct): LibreOffice's text:span in a cell read as the same runs (`spanLook`: its text style's own
      weight, slant, underline, strike, size, colour, font over the span it sits in) and written back as spans with text styles of
      their own (`OdsLooks.span`) when the cell is saved (a look put on it). `rich text in a cell` grew: LibreOffice's .ods of it,
      Tetra's save of that, and LibreOffice's .xlsx of Tetra's; emulator: "Na" bold, "me" red italic in an .ods.
      Not yet: rich text typed in Tables (a cell typed in is one look).
- [ ] **Tests at each big step (the user, 17 Oct: "we may have to run all the tests again"):** the whole unit suite,
      the user's document sweep (`UserDocsSweepTest`, TETRA_SWEEP=1), the LibreOffice round trips, and a walk of
      Words, Slides and Tables on the tablet (TEST_TREE.md) — numbers before and after written here.
      Before (17 Oct, after the emoji fix below): unit suite 402 tests, 0 failing, 7 skipped; sweep 349 documents and
      29 decks, none failing, none copy-only. (The first sweep found one deck failing: a Google Slides .odp export
      writes emoji as two halves, "&#55357;&#56397;", which XML forbids — LibreOffice cannot open that file at all;
      Tetra now reads it and saves it whole, and LibreOffice opens Tetra's copy.)
- After the engine: Slides' Home (font, size, colours… on runs), then the rest of B, using Words' pickers and strips.
  A rich typing editor for slides (Words' own) is a later, separate project.

**Rule for A, B and C (the user, 17 Oct: "We made so much work we should use it as much as possible"):** before
writing anything for Slides or Tables, find Words' version (strips and card galleries, pickers, chart editor,
equation, symbol, link, find/replace, versions, export PDF, print, sources and citations, drawing, comments,
spelling, shapes and styles) and reuse it, moved into shared Office code where it is tied to Words. Each item says
which shared piece it uses; a separate implementation only for a real reason, said in one line.
- [x] **Home**: font, size, bold, italic, underline, strike, colour, highlight; align left, centre, right, justify;
      bullets and numbering, list level in and out; line spacing; text direction and anchor (top, middle, bottom);
      autofit (shrink on overflow, the box grows to its words); Clear formatting; Format painter.
      Done (office-engine session, 17 Oct): with a box with words chosen, Home starts with Words' own tools on all its
      words — `FontButton` + Words' font strip (`DesignGalleryStrip` Font: the deck's fonts, Word's, the phone's,
      Search; a font tapped shows at once, Cancel puts it back), `SizeStepper`, B I U S (`Tool`), `Swatches` with
      `TEXT_COLOURS`/`HIGHLIGHT_COLOURS`, Format painter (the next box chosen takes the look), Clear formatting (the
      letters' own looks gone: their level's/master's — `SlidePara.base`), Bullets / Numbering, the four alignments,
      `LINE_SPACINGS`, words at top/middle/bottom, Don't fit / Shrink text / Box fits words. Kept per box in
      `SlidesEdit.Look` (shown by `SlidesEdit.looked`), saved as PowerPoint writes them (rPr, pPr, bodyPr; a box that
      fits its words saved at its new height) and as LibreOffice does (automatic text, paragraph and list styles,
      declared font faces, the shape's graphic style). A box with words chosen keeps Home (others go to their tab).
      Found and fixed on the way: a numbered paragraph under a bulleted master read as "•" (the nearest bullet setting
      now wins); numbers counted per level as PowerPoint and LibreOffice count them (Tetra counted straight through);
      a centred list's bullet a fixed hang from its words; a slide bullet's own face no longer makes its line taller.
      Not here: list level in/out and a letter's own looks need the rich typing editor (later, as planned); text
      direction (vertical text) not done. `Home's looks on a box saved` (.pptx and .odp, read back, LibreOffice's
      conversions read back). Unit suite 407/0/7; sweep 349 + 29, none failing; Words' fingerprint unchanged; emulator:
      slides.pptx's Plan bold, numbered, centred, 1.5, Times — saved, LibreOffice draws it the same. Tablet: not yet.
- [~] **Insert**: Text box (plain; today only shapes) — done 17 Oct (office-engine session): Insert › Text box puts a box of
      words alone (PowerPoint's txBox, no fill or line, growing to its words; LibreOffice's frame with auto-grow), typed
      into at once; anything put on but a picture or table can now be typed into and takes Home's tools; `a text box
      put on and typed into` (.pptx, .odp, LibreOffice's conversions); Chart (Words' chart editor) — done 17 Oct (office-B
      session): Words' `ChartDialog` (WORD_SAMPLE_CHART to start), held as its `DocChart` (`Added.chart`), drawn by `ChartDrawable`,
      saved by Words' `ChartWriter` (.pptx: a chart part + its workbook, as PowerPoint; .odp: LibreOffice's chart object); the
      chart's tab has Edit data. `a chart put on saved` (both, LibreOffice's conversions); emulator: line chart put on, turned
      into a pie by Edit data, saved — LibreOffice draws it. Not yet: Edit data on a deck's own chart (Picture / Shape below); Link (to a web page or to a
      slide) — done 17 Oct (office-B session): Words' link dialog moved into a shared `LinkDialog` (WriterTable.kt; Words uses
      it as before) with Slides' choice of "Web page" or "Slide in this deck" (the slides by their first words); a chosen box's
      words all linked (`Look.link`, the rich typing editor will link a word), nothing chosen: a text box of the words to show;
      saved as PowerPoint's hlinkClick (an outside relationship, or a slide's with ppaction://hlinksldjump) and LibreOffice's
      text:a inside each span ("#" and the page's name for a slide); Remove link; the show follows a tap on linked words (a
      web page opened, a slide gone to; an .odp's "#page" links too). `links put on saved` (both; LibreOffice reads Tetra's
      slide jumps as its own and writes them back as PowerPoint's); emulator: Plan's words linked to Scores, the show jumped there. Header & footer (slide number,
      date, footer text) — done 17 Oct (office-B session): PowerPoint's dialog (date updated or fixed, slide number, footer,
      Don't show on title slide; Apply / Apply to all), opening with what the slide shows; drawn in the layout's own boxes
      (each read as a slide's own placeholder would be: `Slide.footers`), the date as Words' Date and time's first form
      (`dateForms`); saved as PowerPoint's dt/ftr/sldNum placeholders (slidenum and datetime1 fields) and as LibreOffice
      keeps them (footer-decl, date-time-decl, the page style's display flags; the .odp reader now shows the master's
      boxes so). `header and footer saved` (both, LibreOffice's conversions both ways); emulator: on all but the title slide,
      saved, LibreOffice draws them. Seen: the footer's words in LibreOffice's default face (the layout names none). Symbol, Date, Line break —
      done 17 Oct (office-B session): Words' `SymbolDialog` and `DateDialog` (its forms, `dateForms`), and Words' Line break
      (LINE_BREAK, saved as a:br / text:line-break), each put where the typing cursor is — the chosen box's words opened at
      their end, else a new text box; the typing bar has a Line break button. Equation (Words') — done 17 Oct: Words'
      `EquationDialog`; drawn by Words' `MathDrawable` on the slide (sharp at any size); saved as PowerPoint writes one (a14:m
      OMML in a text box, over a picture of it — which is what LibreOffice shows) and as a LibreOffice Math object (MathML,
      `OfficeMath.mathml`) with the picture as its replacement; read back from either with the equation kept (also
      LibreOffice's own a14:m with an EMF fallback), so Edit equation works on a deck's own ones too; the tab is "Equation".
      `an equation put on saved`, `a line break typed saved` (both formats, LibreOffice's conversions); emulator: x²+1=√y put on,
      saved — LibreOffice draws its picture; a date put in Plan's words. WordArt — done 17 Oct (office-C session): Words' WordArt
      strip (`DesignGalleryStrip` › WordArt: `ART_STYLES`, `ArtBend`; glow offered in .pptx only, as Words offers it in .docx only),
      a look and a bend making it as in Words: Words' "Your text here" in a text box (Insert › Text box's) at Words' size, bold,
      centred, as big as Words makes one (room for the bend); with a box with words chosen, its words take the look instead. Held in
      `Look.effects` (Words' `TextEffects`, in the runs in Word's w14 form, drawn by Words' `EffectSpan`) and `Look.warp`
      (`SlideText.warp`, Words' `TextWarp`, drawn along the bend by Words' `TextBoxDrawable.bent` — pulled out of Words' drawing,
      fingerprint unchanged); saved as PowerPoint writes it (rPr a:ln + a:effectLst glow/outerShdw, bodyPr prstTxWarp fromWordArt)
      and LibreOffice (style:text-outline, fo:text-shadow; bent: a fontwork custom shape, mode and scale "path" as Impress writes
      it, the letters in its fill, the outline its line); read back from both (prstTxWarp; fontwork's fill as the letters' colour).
      `WordArt put on saved` (both, LibreOffice's conversions); emulator: outlined blue, arched, saved — LibreOffice draws the arch
      with its outline. Not drawn (as in Words): a bent WordArt's outline/shadow/glow in Tetra; an .odp fontwork's shadow. Unit suite 416/0/7; sweep 349 + 29, none
      failing; Words' fingerprint unchanged (emulator; the tablet not connected). Link (to a web page or to a
      slide); Header & footer (slide number, date, footer text); Symbol; Equation (Words'); WordArt; Icons (later);
      Audio and video (later).
- [~] **Arrange** (on the Selection tab, as PowerPoint's Shape Format): bring forward / send backward / to front /
      to back; align (left, centre, right, top, middle, bottom, to the slide); distribute; group and ungroup; rotate and
      flip; exact size and position; a picture's **crop** (drawn since 780a88d), replace, reset, border; a shape's
      outline width and dash, shadow; Shape styles (Words').
      Done 17 Oct (office-C session), on the chosen item's own tab: Bring forward, Bring to front, Send backward, Send to back
      (`Edit.stack`, a slide's ids from the back: the tree's order in .pptx, the page's in .odp); Align ▾ left/centre/right/top/
      middle/bottom to the slide (Words' Align list); Rotate (Words' `RotateDialog`), Turn left/right, Flip across / up and down
      (`Edit.turns`: xfrm rot/flipH/flipV; .odp: Words' `Arrange.odtTurned` draw:transform, a shape's geometry mirror, a picture's
      style:mirror — a shape mirrored one way turned the other way round in ODF, as LibreOffice converts PowerPoint's); Size and
      position (its own small dialog, in Words' unit cm/inches: Words' size dialog is the picture's in pixels with handles). Found on
      the way: the .odp reader put a turned item at the slide's corner (it ignored draw:transform) and moving a turned one was not
      saved; pictures can now be mirrored (read and drawn); Android's DOM miscounts a child moved within its own parent (taken out
      first). `items turned and reordered saved` (both, LibreOffice's conversions); emulator: a picture turned 30° and saved —
      LibreOffice turns it the same. Not yet: distribute (needs several chosen), group/ungroup, a group turned, the chosen frame
      drawn turned; crop/replace/reset/border; outline width/dash, shadow, Shape styles. Unit 418/0/7; sweep 349 + 29, none failing.
      Then (office-C session, 17 Oct): Words' `ShapeOptionsDialog` for a slide's shape — Words' Shape styles, fill, outline, outline
      width, size, angle, alt text — applied as one step (only what was changed: a gradient shows there as no fill); Alt text for the
      rest (pictures, charts, tables, text boxes). `Look.lineWidth` (ln w / svg:stroke-width) and `Look.alt`, read back as
      `SlideItem.alt` (PowerPoint's cNvPr descr, LibreOffice's svg:desc) — also the plan's Accessibility item. `shape options and alt
      text saved` (both, LibreOffice's conversions); emulator: a style picked, drawn. A picture's Crop: Words' crop editor (`CornerEditor`,
      Photos' corners, straight cuts) on the whole picture, kept as a crop (`Look.crop`: srcRect / LibreOffice's fo:clip by
      `Slides.picturePt`), what still shows staying where and as big as it was; Reset picture brings the whole back (Words makes a
      new cut picture instead: here PowerPoint's own way is kept). `a picture cropped saved` (both, LibreOffice's conversions, Reset);
      emulator: cropped, saved — srcRect in the file. Unit 419/0/7, sweep 349 + 29 none failing (after the disk was cleared: the
      tests' LibreOffice folders had filled /tmp, a 6.8 GB RAM disk — they are now removed: `scratchDir`, and the sweep at once).
      Outline dash and shadow (office-F session, 17 Oct): Words' Shape options dialog gains Dash (Word's list: Solid, Round dot, Square dot,
      Dash, Dash dot, Long dash…) and Shadow (None / Offset: bottom right) — in Words and Slides alike. **Shared piece:** `LineDash`
      (OfficePages.kt: DrawingML's prstDash names ↔ LibreOffice's draw:stroke-dash, drawn as Android dashes) and `ShapePicture.dash/shadow`
      (`dashes()`, `shadowOf()` — Slides' own shape drawing uses them too). Words: .docx a:ln prstDash + effectLst outerShdw (its other
      effects kept), .odt Writer's shape style name `Tetra_20_shape_…_<width>[-dash][-shadow]_<wrap>` with the dash defined in styles.xml;
      Slides: `Look.dash/shadow`, .pptx the same XML, .odp the shape's style (draw:stroke="dash", draw:shadow) and styles.xml's dash. Read
      back from all of them. `a shape's dash and shadow saved` (Words, both formats, LibreOffice's other format of each), `shape options and
      alt text saved` (Slides, both, LibreOffice's conversions); emulator: arrange.pptx's arrow dashed 3 pt with a shadow, saved —
      LibreOffice draws it so. Seen in passing: LibreOffice draws the rounded box's theme shadow (its style's effectRef), Tetra does not.
      Choosing several, Group / Ungroup, Distribute (office-F session, 17 Oct): Selection › Select all (also Home › Select all and Ctrl+A)
      and Choose several (a tap adds a thing or takes it away; each chosen one outlined); with several chosen the tab is "Arrange" — Group,
      Align ▾ to each other (PowerPoint's Align Selected Objects), Distribute across / down (three or more; the first and last stay), Delete;
      dragging moves them all. A group's tab is "Group", Ungroup first on it. Kept as `Edit.groups` (a group made here: its things' ids;
      a group of a group made here becomes one group of all their things) and `Edit.ungrouped` (a file's group taken apart, each piece by
      its own id: `SlideItem.own`); moved as a group, then taken apart: each keeps where the group put it. Saved as PowerPoint's p:grpSp
      (its box its things', chOff = off) and LibreOffice's draw:g; a file's group unwrapped, each piece's place said on the slide (a group
      in it too). **Shared piece:** Words' Group / Ungroup labels and icon (Words groups two by "tap another"; Slides chooses several, as
      PowerPoint does); Words' Align list. `things grouped and ungrouped saved` (both formats, LibreOffice's conversions, then the saved
      group taken apart); emulator: Select all → Group → dragged → saved, LibreOffice draws it moved; Ungroup; three chosen, Distribute down.
      Not done: an animation on a group made here in an .odp (LibreOffice names a group by xml:id: not written), Copy/Cut of several
      (the first only), a group turned (as before).
- [~] **Slide**: change an existing slide's layout, reset it; sections; a **slide sorter** (a grid, dragged to
      reorder, many at once); copy slides between decks.
      Slide sorter done 17 Oct (office-C session): View › Normal / Slide sorter (PowerPoint's place for it) — every slide small in a
      grid (thumbnails as the sidebar draws them), a tap chooses or lets go (many at once), a slide held and dragged moves there with
      the others chosen (one not chosen moves alone), a double tap opens it; Choose all, Duplicate, Hide/Show, Delete on the chosen.
      Also fixed: Duplicate slide now copies a slide's comments, header & footer, turns and order too. Emulator: slide 4 dragged first.
      Home › Layout (office-C session, 17 Oct): the New slide strip of the deck's layouts, tapped: the chosen slide on that one, its words
      and what is on it kept (`Edit.relayout`; `Deck.relaid` reads the slide again on it — PowerPoint's slideLayout relationship; in an
      .odp the page's master page, whose boxes keep their own places as LibreOffice's do). `a slide's layout changed saved` (.pptx,
      the test .odp, LibreOffice's .odp of the deck with its many masters); emulator: the title slide put on Section Header.
      Sections (office-C session, 17 Oct): Home › Section — a new one from the chosen slide (named), renamed, removed; the name above its
      first slide in the sidebar and in the sorter's and phone's numbers; PowerPoint 2010's p14:sectionLst read and written (slides
      before the first in a "Default Section", as PowerPoint makes it; written again whenever the file has some, so moved or deleted
      slides keep it right). Impress has no sections: the button is not there for an .odp. `sections saved`; LibreOffice opens it.
      Reset slide (office-F session, 17 Oct): Home › Reset (PowerPoint's) — the slide's placeholders back to their layout's place, size and
      look (their own spPr, body settings, list styles and their paragraphs' and letters' looks gone; list levels, links and the words kept);
      Impress has no Reset, so an .odp's placeholders take their master page's place and presentation style, their text styles gone.
      `SlidesEdit.resetSlide` / `SlidesOdp.resetPage`, one XML change used both to show the slide (`Deck.relaid` reads it again, reset) and
      to save it; Tetra's own moves and looks on its placeholders dropped (`Slide.placeholders`); `Edit.reset`. `a slide reset saved` (a
      title moved and made bold, saved; read again, reset: where and as its layout has it, shown, saved, LibreOffice's conversions; both
      formats); emulator: Plan's title moved, Reset — back. Not yet: copy slides between decks.
- [~] **Design**: slide size (16:9, 4:3); format background (colour, gradient, picture); the theme's colour
      variants.
      Colour variants done 17 Oct (office-C session) with the theme's fonts (B's "From Words" Design line): Design › Colours and Fonts
      are Words' strips (`DesignGalleryStrip` Colours / Fonts: `ThemeColors`, `ThemeFonts`), a card shown at once, Cancel/OK, on the
      theme chosen or the deck's own (`SlidesThemes.fromFile`) with `SlideTheme.keep`: only the theme part's colour set and fonts
      change — masters, backgrounds and bands stay (an .odp, which has no theme colours: Fonts only, its colours and backgrounds kept).
      `theme colours and fonts on the deck's own design`; emulator: Green — the theme-coloured box went green.
      Format background (office-C session, 17 Oct): Design › Background — a colour (Words' `Swatches`, `SHAPE_COLOURS`) or Background
      picture (the phone's photo picker, as Insert › Picture), Apply to all, Reset background (as its layout has it); `Edit.backgrounds`,
      drawn stretched over the slide (`Slide.backPicture`); saved as PowerPoint's p:bg (solidFill / blipFill with its media part) and
      as LibreOffice's page style (solid, or bitmap stretched with a draw:fill-image in styles.xml). Read too now: a deck's own picture
      backgrounds (PowerPoint's on a slide, layout or master; LibreOffice's bitmap fill) — before, only colours were drawn.
      `backgrounds saved` (both, LibreOffice's conversions); LibreOffice draws Tetra's .odp's colour and picture. Not yet: gradient
      backgrounds (read or made), slide size. Unit 424/0/7; sweep 349 + 29, none failing.
      Slide size (office-F session, 17 Oct): Design › Slide size ▾ Widescreen (16:9) / Standard (4:3), then PowerPoint's question —
      Maximize (as big as the new size allows, cut at its sides) or Ensure fit (smaller, in the middle, its letters too). `SlidesSize`: a
      copy of the deck with every slide's, layout's and master's things scaled (PowerPoint: p:sldSz, each top-level xfrm, sz and spcPts;
      LibreOffice: the master pages' page layout, every svg place and turn on pages and master pages, fo:font-size), shown and saved from
      as a theme's copy is (`Edit.size`; the theme's and size's copies one, keyed by both). Only from a deck with nothing else changed
      ("Save first": the size moves everything). **Shared piece:** Design › Themes' copy (`themed`). `slide size changed` (both formats,
      both ways, LibreOffice's conversions); emulator: arrange.pptx made 16:9, saved — sldSz 12192000 × 6858000. Not: notes pages' size.
      Gradient backgrounds (office-F session, 17 Oct): read — PowerPoint's bgPr gradFill and a theme's background fills (bgRef 1001 on,
      `bgFillStyles`), LibreOffice's gradient page fill (a page's own fill, else its master's) — and drawn under the slide (`Slide.backGradient`,
      the shapes' gradient drawing shared: `Slides.shader`); made in Design › Gradient (a colour: its light tint at the top to it at the foot;
      `Back.to`), saved as PowerPoint's gradFill (lin 90°) and LibreOffice's linear draw:gradient in styles.xml. `a gradient background saved`
      (both formats, LibreOffice's conversions read back as gradients); emulator: the title slide blue, top light. Not: radial gradients made.
      Fixed after (office-F session): a background gradient that cannot be read no longer drops its slide (the sweep found three of the
      user's jamovi decks read as no slides in the JVM, where Android's colours are stubs); on the emulator the jamovi deck's theme gradient
      (Wisp: white to blue-grey) is drawn — before it was not.
- [x] **Draw**: pen and highlighter on a slide while editing (Words' Draw tab), saved as ink in the file.
      Done 17 Oct (office-C session): a Draw tab (PowerPoint's place, after Insert) › Draw on slide opens Words' `DrawDialog` with the slide
      under the pens (it now takes a slide as it takes a page: `slide = width, height, drawing`) — pens, highlighter, eraser, move, undo,
      two fingers to zoom — and the drawing is put on where it was drawn, cut to what was drawn. Saved as Words saves one: a PNG with its
      strokes inside (Word, PowerPoint and LibreOffice show the picture; PowerPoint's own ink part is not written), so the picture's tab
      has Edit drawing (the strokes back in Words' dialog), even after saving and opening again (seen on the emulator with an .odp).
      Also on a picture's tab: Replace (the phone's photo picker; a file's own picture gets a part of its own: `Look.image`).
      `a picture replaced saved` (both formats).
- [~] **Review**: comments (PowerPoint's own, read and written); spelling in the typing field.
      Comments done 17 Oct (office-C session), with Insert › Comment: Words' `NewCommentDialog` (its remembered name), Words'
      `CommentCard`s with their replies (Edit, Reply, Delete — no Resolve: `CommentActions.resolve` now optional, as neither
      PowerPoint's older list nor Impress keeps it) in a pane beside the slide on a tablet, over the slides on a phone; a marker on
      the slide where each stands (tap: the pane at it); Review › New comment, Comments, Previous/Next (round the deck), Reply,
      Delete comment. Read: PowerPoint's older list (+ p15:parentCm replies), PowerPoint 365's threads (replies, resolved), Impress's
      officeooo:annotation; written as PowerPoint's older list (commentAuthors.xml, eighths of a point — what LibreOffice writes and
      PowerPoint 365 shows; a slide's 365 threads become it when its comments are changed) and as Impress's annotations (a reply
      its own annotation: Impress has no threads). `comments saved` (both, LibreOffice's conversions; LibreOffice reads a
      comment's place only roughly — its own 5 cm comes back 4.76). Emulator: read, added, saved; phone size too. Spelling: not yet Unit 417/0/7; sweep 349 + 29, none failing.
- [~] **View**: Outline (every slide's words as one list to edit, good on a phone); notes page; editing the slide
      master; grid and guides with snapping; zoom.
      Outline done 17 Oct (office-C session): View › Normal / Outline / Slide sorter; Outline lists every slide's text boxes top to bottom
      (the first as its title, sections named), each typed into in place — one step of Undo a box visited, an empty placeholder shown
      by its hint; a slide's number opens it in Normal. Saved as any typed words (each line over the paragraph it came from).
      Emulator: "Plan and more" typed there. Not shown there: a paragraph's list level (kept in the file).
      Notes page (office-C session, 17 Oct): View › Notes page — the speaker notes under the slide on a tablet or phone, typed there (the
      desktop's notes pane, which a desktop always shows); emulator: slide 1's notes under it.
      Gridlines (office-C session, 17 Oct): View › Gridlines — a faint grid (½ cm, or ¼ in in Words' inches) and the slide's middle lines
      while editing; what is dragged snaps its corner to the grid, or its middle to the slide's middle when near it (PowerPoint's smart
      guide), and a size dragged ends on the grid. Emulator: the picture dragged, centred on the guide.
      Not yet: editing the slide master, zoom.
- [x] **File**: Find and Replace across slides; export each slide as a picture; Print with handouts; Versions.
      Find and Replace done 17 Oct (office-C session): Words' find bar moved into a shared `FindReplaceBar` (OfficeUi.kt; Words uses it
      as before — checked on the emulator, 1/3 found) and Slides' top bar has it (the search button while editing, Ctrl+F as A planned):
      every slide's words, any case, ‹ › choosing the box a find is in on its slide; Replace and All as one step of Undo, each line
      kept over its paragraph. Emulator: "slides" → "decks" on slides 1 and 2, "Replaced 2". Versions were done in A.
      Export pictures (office-C session, 17 Oct): File › Export pictures — each slide shown (hidden ones left out, as the PDF) a PNG
      1920 px wide, drawn as the slides are, in a folder "<name> slides" beside the deck. Emulator: 4 PNGs, as the slides show.
      Print with handouts (office-C session, 17 Oct): File › Print ▾ Full slides, or handouts as PowerPoint's — 2 or 6 to an A4 page, or 3
      with lines for notes beside each — each slide framed (`Slides.pdf(handouts)`), to Android's print. Emulator: 3-up, 2 pages for 4.
- [~] **Slide show**: rehearse timings; loop until Esc (kiosk); custom shows (later); more animation kinds and an
      animation list (later).
      Done 17 Oct (office-C session): Slide show › Loop until Esc (after the last slide, the first again; kept in the file — PowerPoint's
      presProps showPr loop, LibreOffice's presentation:settings endless; read back from both) and Rehearse timings (the show from the
      first slide with a timer in the corner, slides that go on by themselves waiting; at the end "Keep the new timings?" — each slide's
      time becomes its "after", as Transitions' Next slide sets it). During any show, PowerPoint's keys: N → ↓ Space Enter Page Down on,
      P ← ↑ Backspace Page Up back, B/W/. black, Home/End, a number and Enter to that slide, Esc ends (the plan's PowerPoint list).
      `loop until Esc saved` (both, LibreOffice's conversions); emulator: rehearsed 4 slides, 0:33, kept. Later as planned: custom shows,
      more animation kinds and an animation list. Unit 426/0/7; sweep 349 + 29, none failing.
- [x] **References** (the user's choices, 17 Oct): Sources (Words' dialogs, Import, Paste, Ask Claude), kept in a .pptx
      in Word's bibliography part, which PowerPoint keeps (an .odp needs its own place: to decide); **live**
      citations, inserted from the typing bar and rewritten when the style or a source changes (PowerPoint has no
      citation fields, so they are found in the words by the deck's sources and style); References slide(s) at the end
      in the style (APA 7, MLA 9, Chicago, Harvard, IEEE), spread over more slides when long, with Update; a **Contents
      slide** after the title slide: each slide's title and number, and in a show a tap on a title goes there
      (`ppaction://hlinksldjump`); Update after slides move.
      Sources and citations done 17 Oct (office-C session): a References tab (Words' place, before Review) — Sources (Words'
      `SourcesDialog`/`SourceDialog`, Import a Tetra sources file, Paste Claude's answer, Ask Claude — `SourcesImport`, tags by
      `Citations.tagFor`), Style ▾ (Words' `CiteStyle`: APA 7, MLA 9, Chicago, Harvard), Insert citation (Words' `CitationDialog`, put in
      where one types). Kept in a .pptx in Word's bibliography part by Words' `WordSources` (customXml, as PowerPoint keeps it); in an
      .odp in meta.xml's user-defined properties in Word's form ("Tetra sources", "Tetra citation style" — **my choice, see Waiting on
      the user**; the user, 17 Oct: "Keep the .odp's sources in meta.xml"), which LibreOffice keeps when it saves. Live: changing the style or a source writes every citation in the slides
      again (found by the sources' citations in the old style). `sources saved` (both; LibreOffice's own save of the .odp keeps them);
      emulator: "(Smith)" (MLA) became "(Smith, 2020)" when the style was made APA. Shortcuts: one source and no page a citation
      (Words' fields hold more), IEEE not offered (its numbers follow the order of citing).
      References slide and Contents slide (office-C session, 17 Oct): References › References slide puts the list in the deck's style at
      its end (Words' `CiteStyle.sorted`/`entry`: italic titles kept), on the deck's Title Only layout under the style's heading (Works
      Cited, References…), over more slides when long ("(continued)", laid out by Words' engine to fit); Contents slide puts each slide's
      title and number after the first slide, each title a link to its slide (PowerPoint's hlinksldjump, LibreOffice's #page — a tap in a
      show goes there). Pressed again, each replaces its old slides (Update — found by their titles, so after reopening too). Both are
      text boxes whose words keep looks of their own (`Added.runs`, a hanging indent: a:r rPr i / hlinkClick, LibreOffice's spans and
      text:a). `words with their own looks put on saved` (both); emulator: Works Cited and Contents made, saved — LibreOffice draws the
      italics and the links. Unit 428/0/7; sweep 349 + 29, none failing.
- [x] Accessibility: alt text for pictures (with Arrange's Shape options, office-C session 17 Oct).

**From PowerPoint** (checked online 17 Oct: PowerPoint's ribbon — Home, Insert, Draw, Design, Transitions, Animations,
Slide Show, Record, Review, View — and Microsoft's page comparing PowerPoint on each platform); what is not listed above:
- [ ] (office-C session, 17 Oct: none of the user's 25 .pptx and 4 .odp decks has SmartArt, video or audio, and there is no PowerPoint here
      to make one to check against — left for when such a deck comes; equations and hidden slides are done)
      **Opening other people's decks well** (most important — a deck that looks wrong can't be presented): **SmartArt**
      drawn from the drawing the file saves with it (check: not found in Slides' code); a deck's **video and audio
      played** in the show (today neither is); its embedded fonts used; equations drawn (Word's OMML, as Words');
      hidden slides skipped in the show; an empty placeholder shows "Click to add title/text" and typing fills it.
- [x] **Selection pane**: every object on the slide in a list — choose one under another, hide or show it, rename it,
      reorder by dragging (the only way to reach a covered object on a tablet).
      Done 17 Oct (office-D session): Home › Selection pane (and beside Arrange's order buttons) — the slide's things, front first, each
      by its name (else what it is) with its words under it; tapped, it is chosen (one under another too); the eye hides or shows it (not
      drawn, not tappable on the slide, skipped in the show); rename in place; ↑ ↓ Bring forward / Send backward (`restack`, Edit.stack —
      arrows, not dragging: a list's drag fights the dialog's scroll on a phone). Saved as PowerPoint's cNvPr name / hidden="1" and
      LibreOffice's draw:name / drawooo:display="none"; read back from both (`SlideItem.name`, `hidden`; `Look.name`, `Look.hide`).
      **Shared piece:** none of Words' (Words has no selection pane); the Animation pane's list. `a thing named and hidden saved`
      (a shape: both kept by LibreOffice's conversions; on a placeholder LibreOffice 26.2 loses hidden both ways and its .pptx renames it).
- [~] **Picture**: picture styles (frame, shadow, rounded corners — the tablet app has these); transparency; colour
      and brightness; compress pictures; remove background (later).
      Done 17 Oct (office-D session): the picture's tab › Picture styles (Plain, White frame, Black frame, Shadow, Rounded, Rounded with
      shadow) and Transparency (None, 15–80 %). `PictureLook` (alpha, outline, shadow, rounded) read from both formats and drawn (a soft
      shadow under it, corners clipped, the outline round it); saved as PowerPoint's blip alphaModFix, spPr ln, effectLst outerShdw and
      prstGeom roundRect, and LibreOffice's draw:image-opacity, stroke and shadow (an .odp keeps no rounded picture). **Shared piece:**
      none of Words' (Words' Picture tab has neither yet — Words should get the same `PictureLook` when it does). `a picture's style and
      transparency saved` (both, LibreOffice's conversions keep them); emulator: rounded with shadow at 30 %. Not yet: colour and
      brightness, compress pictures; a strip of cards for the styles (a menu for now).
- [ ] **Shapes**: text effects (shadow, outline, glow) and character spacing; merge shapes (union, combine,
      intersect, subtract — later); edit points (later).
- [ ] **Transitions**: duration; sound (later); advance on click and/or after a time set here (the file's `advTm`
      is played today, not set); **Morph** (later: the most used modern transition).
- [~] **Animations**: start on click / with previous / after previous; duration and delay; entrance, emphasis and
      exit kinds; the animation list (pane) to reorder; animation painter; motion paths and triggers (later).
      Found 17 Oct (office-D session): start, duration (Fast/Medium/Slow), delay, the kinds and Earlier/Later were already in Options and
      Effect. Added: **Animation pane** (the slide's effects in order — click number, what, to what, when, how long; tap to choose, ↑ ↓,
      take away) and **Animation painter** (the chosen thing's effects put on the next thing chosen, in place of its own). **Shared
      piece:** none of Words' (Words has no animations); the format painter's way of working. Emulator: Fade painted from the title onto
      the subtitle, both in the pane. Not yet: motion paths and triggers (later).
- [~] **Slide show**: go to a slide by number or from a grid during the show; keyboard keys as PowerPoint's (N/P,
      arrows, B/W screen, number + Enter, Esc); keep or discard the pen's ink at the end, saved into the deck;
      set-up options (loop, without animations, use timings or not); subtitles (later).
      Done 17 Oct (office-C session): a number + Enter and PowerPoint's keys (with Loop until Esc, B › Slide show above); at the end of a
      show drawn on with the pen (while editing), "Keep your ink?" — kept, each slide's strokes become a drawing on it where they were
      (Words' drawing picture, red as the pen, Edit drawing draws on). Emulator: a line under the subtitle, kept. Not yet: a grid to go to
      a slide during the show, without animations, use timings or not; subtitles later.
- [ ] **Record** (later): record narration and timings per slide; export the deck as a video.
- [ ] **View**: reading view; ruler; notes and handout masters (later); grayscale preview (later).
- [~] **File**: Save as template (.potx, into Tetra's Templates folder, where Design › Themes finds it); Save as
      .odp / .pptx; remove notes and comments before sharing (Inspect); password protection (later).
      Template and Inspect done 17 Oct (office-C session, with From Words' File above). Not yet: saving as the other format.
- [ ] **Text**: right-to-left text and text direction (vertical); highlight (planned in Home above).
- [~] **Insert**: Zoom (summary and slide zoom — later); action buttons (as Link to a slide); photo album (later).
      Object links with a zoom done 17 Oct (office-C session; the user: "link from one page to the next … maps with images that zoom in
      … linking slides and objects together", "click to jump slides or objects, textboxes, zoom in and out effects"): Insert › Link with
      a picture or shape (or chart) chosen links the object itself — to a slide, a web page, or "The slide it came from" (PowerPoint's
      Last Slide Viewed; .pptx only, LibreOffice has none) — the shared `LinkDialog` gaining that choice. Saved as PowerPoint's click
      action on the object (cNvPr hlinkClick, hlinksldjump / lastslideviewed) and LibreOffice's (office:event-listeners, "show" to the
      page); read back from both (`SlideItem.link`). In a show a tap on it (or on a box with linked words) jumps, the new slide growing
      out of what was tapped; going back it shrinks back into it — Tetra's own zoom (PowerPoint and LibreOffice jump plainly).
      `a picture linked to a slide saved` (both, LibreOffice's conversions); emulator: the flag zoomed into Scores, the chart back out.
      Unit 429/0/7; sweep 349 + 29, none failing. Not yet: PowerPoint's Zoom objects (a slide's picture that is itself the link), photo album.
- [~] **Video** (the user, 17 Oct: "Video import, there is no online or offline option on any of the applets"): Insert › Video in
      Slides — offline (a video from the phone, kept in the deck, played in the show: PowerPoint's p:pic videoFile + p14:media, Impress's
      draw:plugin) and online (a YouTube or web address kept as a link with its picture, opened when tapped in the show: PowerPoint's
      Online Video); then Words (Word's Online Video). Excel has none, so Tables not.
      Slides done 17 Oct (office-C session): Insert › Video ▾ From this phone (Android's picker; the film copied, its picture a frame a
      second in) or Online video (an address; YouTube's own picture fetched, else a dark one naming the site). Drawn as its picture with a
      play mark; its tab is "Video" with Play. In a show a tap plays it over the slides (Android's player; a film in the file is taken out
      of it to play) or opens an online one in its app. Saved as PowerPoint's Insert › Video makes one (p:pic with a:videoFile, p14:media
      embedding the film in ppt/media, a tap playing it) and as Impress's (draw:plugin, the film in the package's Media/, its picture
      beside); online ones as a linked address in both. Read back from both (`SlideItem.media`); LibreOffice keeps both films in its
      conversions. `videos put on saved`; emulator: a 4-second film put on, saved, reopened and played in the show; a YouTube address
      put on with its picture. Unit 430/0/7; sweep 349 + 29, none failing. Not yet: Words' Online Video, sound (audio), a film's start/end.
      Words done 17 Oct (office-D session; the user: "Word loads it in word, the same with offline from a path and then pass it to slides",
      "resize rotate line an image like a html embedding a frame"): Insert › Video in Words — Online video and From this phone, in .docx
      and .odt. The video is a picture (its tab "Video": Play, Size, Crop, Wrap text, turn — as an image's), with a play mark; Play or a
      tap on it in the pages plays it **in its own frame** where it stands (its size and turn): a film in Android's player, an online one
      in YouTube's own player in a web frame (an iframe, as Word plays its Online Video) — Slides now does the same, in editing and in the
      show (no more going out to YouTube's app). Saved as Word's Online Video (wp15:webVideoPr with its player, a click to the address)
      and LibreOffice's media frame (draw:plugin: the address, or the film in Media/); a film from the phone in a .docx is kept in
      word/media beside its picture (PowerPoint's media relationship, named in Tetra's own ext — Word has no such video and shows the
      picture). Read back from all of these, and from LibreOffice's copy of Word's Online Video (a picture linked to the address).
      **Shared piece:** OfficeVideo.kt (`OfficeVideo.poster`/`fromPhone`/`film`/`mark`, `VideoFrame`, `VideoButton`), moved out of
      Slides; `DocImage.film`, `PictureXml.video`; `LaidPage.videoAt`. `a video put in Words is saved` (both formats, LibreOffice's
      conversions); emulator: both kinds in a .docx and an .odt, saved, played on the pages and in Slides' show; LibreOffice draws them.
      LibreOffice limit: it writes its own media frame into a .docx as an empty shape. Not yet: the film on a show's second screen.
      Show taps (the user, 17 Oct: a tap going forward "overlaps with actions like play video"; asked, chose "hold while playing"): a tap
      still goes through the slide's clicks and then the next slide (PowerPoint's way), a tap on a video or link plays or follows it, and
      now while a video plays a tap beside it only stops it — the next tap goes on; swipes and keys go on at once. Emulator: checked.
- Not planned: Designer and Copilot (need Microsoft's cloud), co-authoring and @mentions (no cloud), online pictures
  (no network services), Cameo, translate, compare presentations. (YouTube: now wanted, see Video above.)

**From Words** (asked 17 Oct: "transfer the rest of the features from Words to Slides" — Slides' Insert had only
Picture, Shape and Table). Each Words button checked against what a slide can hold; what is already listed above is not
repeated. Words' code is reused wherever it fits (its dialogs, strips, pickers).
- [~] **Selection** (Slides has none of these): Cut, Copy, Paste a shape, picture or table (on another slide, in
      another deck); Paste text only; Select all on the slide; Copy formatting / Paste formatting.
      Done 17 Oct (office-D session): a Selection tab first whenever something is chosen (A's rule), Words' buttons in Words' order — Cut,
      Copy, Paste (what was put on, a picture, a shape, and now a box of the file's with words, pasted as a text box keeping its words'
      size, bold, italic, colour and links), Paste text only (the clipboard's words into the chosen box, else a new text box: `putWords`),
      Copy formatting / Paste formatting (the look kept until replaced, as Words'; Home's Format painter now reads the same `lookOf`).
      **Shared piece:** Words' Selection tab (labels, icons, order), Slides' own copy/paste underneath. Emulator: title copied and pasted,
      its look pasted on the subtitle. Not yet: Select all (needs choosing several things at once — with Group below); a pasted box's
      paragraph alignment (`Added.runs` keeps letters only); pasting into another deck works within one Slides session only.
      Select all done (office-F session, 17 Oct, with Arrange's Group above).
- [~] **Home** (with the runs above): superscript and subscript; Smaller / Bigger text; Aa (change case); multilevel
      lists; Words' paragraph page (More…: indents, spacing before and after, line spacing exactly); a text box's
      border and shading (as Words' Borders, written as the shape's line and fill).
      Done 17 Oct (office-D session): X² and X₂ (Words' buttons) on the box's words — `Look.script`, saved as PowerPoint's baseline 30000 /
      −25000 and LibreOffice's text-position super/sub 58 %, read back from both and LibreOffice's conversions; Aa (Words' Change case:
      `WriterBridge.inCase`, the four cases) on the box's lines, each letter keeping its look as typed words do (one step of Undo); copied
      looks carry X²/X₂ too. Smaller / Bigger: Words has none beyond its − size + stepper, which Slides already has. **Shared piece:**
      Words' X²/X₂ and Aa (`WriterBridge.Case`, `inCase`). `Home's looks on a box saved` (superscript added); emulator: a title made
      UPPERCASE and raised. Not yet: a text box's borders and shading.
      Multilevel list and Paragraph (office-F session, 17 Oct): Home › Multilevel opens Words' Multilevel list strip (`DesignGalleryStrip`
      › Lists, `LIST_SCHEMES`) — each paragraph numbered in its level's form (1. a. i., I. A. 1., i. a. 1.; Word's "1. 1.1." shows each
      level's own 1. 2., as PowerPoint has no such numbers) — and Home › Paragraph opens Words' paragraph page (`ParagraphDialog`: before
      and after, line spacing or exactly, indents) on all the box's paragraphs. `Look.scheme`/`Look.para`; numbers' forms read from
      PowerPoint's buAutoNum type and LibreOffice's num-format and counted per level (`SlideBullet.num`, `SlideBullet.form/text`: a b … aa,
      i ii iii iv …); saved as PowerPoint's buAutoNum per paragraph's lvl and pPr lnSpc/spcBef/spcAft/marL/marR/indent, and LibreOffice's
      list style's num-format per level and paragraph style fo:margin-*/line-height/text-indent (in an .odp a list paragraph's margin is
      on top of its list's indent, LibreOffice's way). **Shared pieces:** Words' Lists strip and `ParagraphDialog`. `a multilevel list and a
      paragraph look saved` (both formats, LibreOffice's conversions); emulator: Plan's body I. A. B. II. A. Seen in passing (Words' engine,
      not this item): a number wider than its hang goes to the next half-inch stop (PowerPoint's default tab is the master's defTabSz, 1 in).
      List level in and out per paragraph: still with the rich typing editor (later).
- [~] **Insert**: **Drop cap** — (office-engine session, 17 Oct: the strip and Raised done, Slides' Dropped next) PowerPoint and Impress have none, so it is either a raised cap (the first letter a
      bigger run of its own, which every app shows) or a dropped one (the letter in its own box, the paragraph's first
      lines indented beside it: looks right everywhere but doesn't reflow). **The user, 17 Oct:** "these are options,
      not one choice: make a modal and add the options", then "it could also be a ribbon of cards with an example
      each, like Words has for pages". So: a strip of cards in the ribbon's place (as Page numbers'), one per kind —
      None, Dropped, Raised — each a small paragraph showing it; a More… card opening the dialog with the finer
      settings (lines to drop, distance from the text, a preview). Words' own Drop cap made the same if it is not
      already, so the two match (one shared piece).
      Done 17 Oct: one strip (`DesignGalleryStrip` › `DesignGallery.DropCap`, a card a kind — `DropKind` None, Dropped,
      In margin, Raised — each a small paragraph showing it, then More…) with a "Lines − n +" setting on it (the user:
      as Word's "Lines to drop" 1–10, LibreOffice's "Lines" 2–9; Tetra 2–10, 3 first). Words: its dropdown gone for the
      strip (None, Dropped, In margin in a .docx), the pages shown while choosing, More… its own dialog. Slides: None and
      Raised (the first letter a run of its own, lines × the words' size, saved as a bigger run in .pptx and .odp,
      made plain again by None), More… Words' dialog in a raised form (lines, the letter's face and colour). Found in
      passing: a Words drop cap in a paragraph whose second line holds a picture came out huge (fixed: a line's step no
      more than the first line). `a raised first letter saved` (.pptx, .odp, LibreOffice's conversions). Slides' Dropped
      (office-B session, 17 Oct): the letter in a text box of its own (Insert › Text box's), N lines tall, its capital from the first
      line's capitals to line N's foot, beside the paragraph; the paragraph split where Words' engine breaks its line N
      (`SlidesText.drop`), the lines before standing in by the letter's width (`Look.drop`: marL / fo:margin-left, no space after),
      the rest under them (no space before); each letter's look kept (`relook` now finds the words inside what was there). Shared
      pieces: the strip (`DesignGalleryStrip`), `DropCapDialog` (Slides: None, Dropped, Raised; distance for Dropped), the text box,
      Words' layout. `a dropped first letter saved` (.pptx, .odp, LibreOffice's conversions); emulator: 3 and 4 lines on a typed
      paragraph, saved, LibreOffice draws it the same (its own line breaks: its font). Doesn't reflow (as planned): None
      afterwards only un-raises; the letter box is deleted as any box. Date and time
      (fixed, or a field that updates, as PowerPoint's); line break inside a paragraph; Comment; a link's Edit
      (beside Link above). Slide numbers come with Header & footer.
- [ ] **Picture / Shape** (Words' Picture tab): Add text to a shape, Edit text; Shape options (fill and outline
      colour, besides width and dash above); Save picture as; Edit data on a chart, Edit equation, Edit drawing.
- [~] **Table** tab when a table is chosen (Words'): row above / below, column left / right, delete row, column,
      table; merge and split cells; borders and shading; table styles; Make chart from the table.
      Done 17 Oct (office-D session), for a table put on in Slides: Make chart (Words' `OfficeCharts.fromTable`: the first row names the
      series, the first column the categories, into Words' chart editor), Row above / below, Column left / right (round the cell last
      tapped; the table a row taller, a column wider — keeping its width when it would leave the slide), Delete row, column, table; its
      fill and words' colour as before. **Shared piece:** Words' Table tab (its labels, icons and order) and `fromTable`; the rows and
      columns are `SlidesEdit.rowAdded/columnAdded/rowTaken/columnTaken` on the table's cells. `a table's rows and columns`; emulator:
      2×2 made 3×3, saved — LibreOffice opens it. Not yet: merge and split, borders, table styles.
      A deck's own tables (office-F session, 17 Oct): the same Table tab on a table that came with the file — a second tap chooses the cell
      the rows and columns go round; Make chart from its words. Kept as `Edit.tables` (rows and columns added or taken, in order;
      `SlidesEdit.tabled` shows them), saved into the file's own table: PowerPoint's a:tbl (a gridCol and each row's a:tc copied with no
      words, their ids dropped; a:tr likewise; the frame grown), LibreOffice's table:table (repeated columns and cells split first; a
      copied column keeps its width style). **Shared piece:** Words' Table tab (labels, icons, order) and `OfficeCharts.fromTable`.
      `a deck's own table's rows and columns saved` (a table saved, read as the file's, a row and column added and a row taken; both
      formats, LibreOffice's conversions); emulator: owntable.pptx, Row below and Column right. Not: merged cells (copied unmerged).
- [x] **Layout** for a text box: inner margins; columns in a text box (PowerPoint's `numCol`); no wrap.
      Done 17 Oct (office-D session): the chosen box's tab › Layout (PowerPoint's Format Shape › Text Box): inner margins in Words' unit,
      "Wrap the words in the box", Columns 1–6 and the room between them. Saved as PowerPoint's bodyPr lIns…bIns, wrap, numCol/spcCol and
      LibreOffice's fo:padding-*, fo:wrap-option, style:columns; read back from both (an .odp's columns and no-wrap were not read before).
      Columns are drawn by the shared engine: the words laid a column wide, going on into the next where the box's room ends
      (`SlidesText.columns`, splitting Words' laid pieces by line). **Shared piece:** Words' engine (`OfficePages.stack`, `Block`) and
      `WriterUnits`. `a box's layout saved` (both formats, LibreOffice's conversions keep all of it); emulator: a box in two columns.
- [x] **Design**: the theme's fonts (Words' Fonts), besides its colours above (office-C session, 17 Oct, with B › Design).
- [x] **Review**: Read aloud (a slide's words, or its notes), as Words'.
      Done 17 Oct (office-C session): Review › Read aloud — Words' `ReadAloud` (Android's speech, each paragraph in its own language)
      reads the slides' words from the chosen slide on, box by box top to bottom, the slides following it; Read the notes reads the
      speaker notes instead; Words' `ReadAloudBar` (what is read, Stop) in the top bar meanwhile. Emulator: read, the bar showed the line.
- [x] **File**: AutoSave, Save a copy, Save as, Rename (as Words', beside A's list).
      Done 17 Oct (office-C session): File › Save a copy ("name (copy)" beside it, the file and its edits left as they are), AutoSave
      (Words' own setting, one for both; in Slides it saves as the app goes to the background, because a save reads the deck again and
      would take Undo away while one works), Save as (a name beside it, Slides going on with the new file), Rename (the file, its
      recovery draft and versions, as Words renames). With PowerPoint's list below: Save as template (.potx into Tetra's Templates, which
      Design › Themes and New presentation offer; a .pptx only) and Inspect (speaker notes and comments taken out before sharing, Undo
      brings them back). Emulator: a .potx made in Templates; Save as went on with "sorter (18) (2).pptx".
- [ ] **While typing**: Words' keyboard bar (bold, italic, underline, highlight, voice typing) over the keyboard.
- Not for slides: page breaks, blank page, watermark, page borders, line numbers, hyphenation, footnotes and
  endnotes, captions and cross-references, track changes, content controls, style sets (PowerPoint has none of these).

### C. Tables — what Excel and Calc have that Tables lacks
- [x] **Home**: merge and centre (and unmerge); column width and row height (typed, and AutoFit to the contents);
      hide and unhide rows, columns and sheets; insert and delete cells shifting the rest; Fill down and right; cell
      styles; **Format as table** (make an Excel Table) — done; **conditional formatting** made here — done;
      Find and **Replace**; Format painter.
      Replace done (office-F session, 17 Oct): Tables' own find (all sheets, Go To) gets Words' replace row (`ReplaceRow`, moved out of
      `FindReplaceBar` and shared) while editing — Replace (the cell shown) and All, in each found cell's words or formula, any case
      (`SheetInput.replaced`), one step of Undo, recalculated. `words replaced in a cell`; emulator: alpha → Gamma, "Replaced 1".
      Merge & centre and Unmerge done (office-F session, 17 Oct): Home › Merge & centre with several cells chosen (Excel's: only the top
      left keeps what it holds, centred), Unmerge with a merged one chosen — a change to the file (`SheetMerge`: Excel's mergeCells in the
      schema's place, LibreOffice's spanned cell over covered ones, repeated rows and cells cut first), as inserting rows is (`fileStep`;
      the centring a second step of Undo). `cells merged and unmerged` (both formats, LibreOffice's conversions); emulator: A4 unmerged,
      A4:C4 merged and centred.
      Format painter, Fill down / right done (office-F session, 17 Oct): the Selection tab gets Words' Copy formatting / Paste formatting
      (a cell's look onto all the cells chosen — a range, which a one-tap painter cannot take on a phone); Home › Fill down / Fill right
      with several rows or columns chosen (the fill handle's own `fillCells`: series and formulas carried as the handle carries them).
      Insert › Symbol: Words' `SymbolDialog`, put in where one types in the cell (typing begun with its words). Emulator: A1's yellow on
      A7:B7; € in C9.
      Column width and row height, Hide and Unhide done (office-F session, 17 Oct): a column's or row's tab (its letter or number tapped)
      › Width / Height (typed in Words' unit, cm or inches), AutoFit (its longest words, its biggest letters), Hide, and Unhide when one
      beside it is hidden — a change to the file (`SheetSizes`: Excel's `<cols>` runs cut and changed, a row's ht/customHeight/hidden, a
      row made where the sheet has none; LibreOffice's column and row styles and table:visibility, repeated ones cut first). Fixed on the
      way: two file changes started together (the second from the file without the first) — now one at a time. `widths heights and hidden
      saved` (both formats; LibreOffice reads Excel's width in characters a few % wider — its own character); emulator: C made 4 cm, hidden,
      shown again. Hiding and showing sheets (office-F session, 17 Oct): a sheet's tab › Hide (while another is shown) and Unhide ▾ the
      hidden ones (`SheetVisibility`: Excel's state="hidden", the open tab moved off it; LibreOffice's table style table:display).
      `a sheet hidden and shown` (both formats, LibreOffice's conversions); emulator: Second hidden, shown again.
      Format as table done (office-F session, 18 Oct): Home › Format as table with the cells chosen (headings in the first row) —
      Excel's gallery (Medium 1–7, Light 8–14) drawn in the workbook's own theme colours (`Workbook.theme`); .xlsx: Excel's table part
      (`SheetTables.xlsx`: unique id and name, headings made its column names as Excel makes them — an empty one "Column n", a repeat
      numbered on, typed into the cells first —, its own filter, a sheet filter over it taken away; a table already there or merged
      cells refused), drawn by the reader's built-in styles (moved to `SheetTables.builtIn`/`lay`); .ods (LibreOffice has no table
      styles): the same looks put on the cells, as its AutoFormat does. `a table made` (LibreOffice reads it as a database range);
      emulator: A1:D3 of a file whose accent 1 is green, Medium 3 saved. Not yet: Table Design (total row, banded columns, resize).
      Conditional formatting made here (office-F session, 18 Oct): Home › Conditional formatting — Excel's menu: Greater than, Less than,
      Between, Equal to, Text that contains, Duplicate values (each asking its value(s) and one of Excel's three looks), Top 10, Above
      average, Data bars, Colour scale (at once), Clear rules (`SheetConditional`: Excel's conditionalFormatting with a dxf, put first
      as Excel puts a new rule; LibreOffice's calcext:conditional-format with a cell style of its own, named as it shows it — it finds
      a condition's style by that name). A value with a decimal comma taken as a number. `conditional formatting made and cleared`
      (both formats, LibreOffice's conversions both ways); emulator: B2:B5 of an .ods > 1000 in light red. shortcut: Clear rules takes
      a rule partly over the cells away whole. Not yet: icon sets, Manage rules.
- [~] **Insert**: **Chart from the chosen cells** (Words' chart editor, the series from the cells); picture; shape
      and text box; link; **notes and comments** (they are only shown today); symbol; a checkbox in a cell; a drop-down
      (data validation list); sparklines (later); PivotTable (later).
      Chart done (office-F session, 17 Oct): Insert › Chart with cells chosen — Words' `OfficeCharts.fromTable` makes the series from them,
      Words' `ChartDialog` edits it, then it goes on the sheet to the right of the cells, Excel's default 8 × 15 cells (`SheetChart`:
      Words' `ChartWriter` part in the sheet's drawing, a two-cell anchor — a new drawing made when the sheet has none —, or LibreOffice's
      chart object in the cell). shortcut: the chart holds its own numbers (not linked to the cells). `a chart put on a sheet` (both
      formats, LibreOffice's conversions); emulator: A1:B3 charted.
      Link done (office-F session, 17 Oct): Insert › Link — Words' shared `LinkDialog` (now naming its places: "Sheet in this workbook"),
      a web page or another sheet's A1, the words to show typed into the cell; Excel's hyperlink (an outside relationship, or a location)
      or LibreOffice's text:a round the cell's words (`SheetLinks`, a change to the file); Remove link. `links on cells saved` (both
      formats, LibreOffice's conversions, one taken away); emulator: A2 linked to the second sheet.
      Notes done (office-F session, 17 Oct): Insert › Note / Edit note / Delete note — Words' `NewCommentDialog` (its remembered name; a
      label of its own: "Note") — saved as Excel's comments part with the VML note shape Excel draws it by (both made when the sheet has
      none) and LibreOffice's office:annotation in the cell (`SheetNotes`, a change to the file). `notes made and taken away` (both
      formats, LibreOffice's conversions; a sheet with notes and one without); emulator: a note on C3. Not yet: threaded comments.
      Picture and Text box done (office-F session, 18 Oct): Insert › Picture (Words' picker and `preparePicture`, now shared; at most
      300 pt wide) and Text box (its words asked; 2 × 0.8 in) at the chosen cell — Excel's pic and txBox shapes in the sheet's drawing,
      LibreOffice's frame with its image (Pictures/, in the manifest) and rectangle shape with its words. `SheetChart`'s drawing code
      shared by chart, picture and text box (`anchored`). `a picture and a text box put on a sheet` (both formats, LibreOffice's
      conversions); emulator: a text box at B7, a picture at C14. Not yet: moving, resizing or deleting drawings on the sheet; shapes.
- [~] **Formulas**: browse functions by category (the typing row exists); named ranges (Name Manager); show what a
      cell uses and what uses it; error checking; more functions — the dynamic ones (XLOOKUP, FILTER, SORT, UNIQUE),
      checked against LibreOffice as the 129 are.
      Browse by category done (office-F session, 17 Oct): Formulas › Insert function — Excel's categories (Maths, Statistics, Logical,
      Text, Date and time, Lookup, Information) over the cell bar's own list (`SheetFunctions.all`, its arguments and words; seven it
      lacked though Tables works them out added: CEILING.MATH, FLOOR.MATH, STDEV.S, STDEVP, VAR.S, VARP, RANK.EQ), a search; tapped, a
      new formula in the cell or put in where one types (`FunctionsDialog`). `functions in categories`; emulator: =MAX( from Statistics.
- [~] **Data**: a sort that changes the file, by several columns; the filter saved as the file's AutoFilter; remove
      duplicates; text to columns; data validation; group and outline rows; subtotals.
      Remove duplicates done (office-F session, 17 Oct): Data › Remove duplicates on the chosen rows, Excel's question (the first row
      headers?), the rows alike after the first gone (any case, spaces round trimmed: `SheetInput.unique`), the rest moved up — cell
      changes, one step of Undo; not on formulas. `duplicate rows found`; emulator: a CSV's two duplicates removed.
      Text to columns done (office-F session, 17 Oct): Data › Text to columns — a column's words cut at a comma, semicolon, tab, space,
      slash or dash (not inside quotes; spaces: runs as one) into the cells to its right, each typed as if there (numbers stay numbers):
      `SheetInput.split`, one step of Undo. `text cut into columns`; emulator: Crete/Rhodes/Corfu into three cells.
      Sort that changes the file done (office-F session, 18 Oct): Data › Sort while editing — Excel's dialog: the chosen cells, else
      the current region round the cell (`SheetSortRows.region`); My data has headers (guessed as Excel does); Sort by and up to two
      Then by, each a column (by its heading) and A → Z or Z → A. Excel's order (numbers, words in any case, TRUE/FALSE, errors, blanks
      last either way; rows alike keep their order), formulas moving with their rows; cell changes, one step of Undo; merged cells
      refused. The view's own sort (A → Z on a column's tab) stays as it was. `rows sorted`; emulator: a CSV by Score Z→A, Name A→Z.
      Filter saved as the file's AutoFilter done (office-F session, 18 Oct): Data › Keep filter in file (the view's filter: Excel's
      autoFilter with its filterColumn values and _FilterDatabase name, sheetPr filterMode, the rows it hides hidden; LibreOffice's
      anonymous database range with its filter conditions and set items, rows visibility "filter") and Remove file filter (every row
      shown again). An .xlsx's AutoFilter values are now read and become the view's filter when the sheet opens (Clear filter shows
      its rows). `SheetAutoFilter`. `an AutoFilter kept in the file` (both formats, LibreOffice's conversions both ways); emulator: Βήτα
      filtered out of A, kept, saved. shortcut: an .ods's filter values are not read (its filtered rows show hidden).
      Drop-down lists done (office-F session, 18 Oct): Data › Drop-down list on the chosen cells — the values typed, comma-separated
      (Excel's Source box), Take away; Excel's list dataValidation and LibreOffice's table:content-validation (`SheetLists`, a change to
      the file), both read (`Sheet.lists`/`listAt`, `SheetList`: a quoted list, or this sheet's cells). While editing, the chosen
      cell's values show as a row under the ribbon to tap; a value typed that is not one of them is refused (Excel's stop).
      `drop-down lists made and taken away` (both formats, LibreOffice's conversions); emulator: E2:E4 Yes/No/Maybe, "No" tapped into
      E3, "Hello" refused, saved and read by LibreOffice. shortcut: a list from another sheet's cells is kept but not looked up.
- [~] **Page layout**: margins, orientation, paper size, print area, rows repeated on every page, fit to pages, header
      and footer, gridlines printed — today Export PDF and Print use the file's own setup, which can't be changed.
      Done (office-F session, 17 Oct): a Page layout tab (Excel's place, after Insert) — Orientation, Size (A4, Letter), Margins (Excel's
      Normal, Wide, Narrow), Scale (actual size, all columns on one page, sheet on one page), Print gridlines, Set / Clear print area —
      each a change to the file (`SheetPageSetup`: Excel's pageSetup, pageMargins, printOptions, sheetPr's fitToPage, _xlnm.Print_Area;
      LibreOffice's page layout on a master page of the sheet's own, its print ranges), and what Export PDF and Print use. LibreOffice's
      page settings are now read too (an .ods printed with Excel's defaults before): its top and bottom count the header's and footer's
      room, as Excel's margins do. `page layout saved` (both formats, LibreOffice's conversions); emulator: page.ods made landscape.
      Rows repeated on every page done (office-F session, 18 Oct): Page layout › Print titles with rows chosen, Clear print titles —
      Excel's _xlnm.Print_Titles (read and printed before), LibreOffice's table:table-header-rows round the rows, now read too (an .ods
      printed without them before). In `page layout saved` (both formats, LibreOffice's conversions); emulator: an .ods's rows 1–2.
      shortcut: columns printed on every page not kept; rows in a row group not wrapped.
      Header and footer done (office-F session, 18 Oct): Page layout › Header and Footer — Excel's ready-made ones (Page 1, Page 1 of ?,
      sheet name, file name…) and Custom… (Left, Centre, Right, with Page, Pages, Sheet, File, Date and Time to put in); kept as
      Excel's codes (`SheetPrint.header`/`footer`, `SheetHeaderFooter`): Excel's headerFooter read and written, LibreOffice's master
      page regions and fields read and written (an .ods's own "sheet name / Page 1" now read, as LibreOffice prints it), and printed:
      Export PDF and Print draw them 0.3" from the edges. In `page layout saved` (both formats, LibreOffice's conversions); emulator:
      "Draft | sheet name" and "Page 1 of ?" in the exported PDF. shortcut: one header for every page (Excel's first/even ones not kept).
- [~] **Review**: protect a sheet (respect a protected one: no typing in locked cells); spelling.
      Respecting a protected sheet done (office-F session, 17 Oct): Excel's sheetProtection and LibreOffice's table:protected read
      (`Sheet.protected`), each look's Locked (Excel's protection locked, LibreOffice's cell-protect; on by default: `SheetStyle.locked`);
      a change to a locked cell refused where every change passes (`SheetHistory.apply`/`locked`, with a message), typing not begun,
      whole-file changes refused on a protected sheet. `a protected sheet's locked cells kept` (Excel's, and LibreOffice's .ods of it);
      emulator: a protected copy of sheet.xlsx refused typing.
      Protect / Unprotect made here (office-F session, 18 Oct): a sheet's tab › Protect (a password twice, or none) and Unprotect (its
      password asked when it has one, checked against the file's hash: Excel's SHA-512 or its old 16-bit one, LibreOffice's SHA-256 or
      SHA-1), and Home › Lock cell (Excel's Format › Lock Cell) to leave cells open to typing — now saved (Excel's protection locked,
      LibreOffice's cell-protect; Shrink to fit, read since step 6, now saved too). `SheetProtect`; `Sheet.guarded`. `a sheet protected
      and unprotected` (both formats; the hashes checked against Python's; LibreOffice's conversions keep the protection, not the
      password — it can't turn one hash into the other); emulator: protected with a password, A2 refused, a wrong password refused,
      unprotected. Not yet: spelling.
- [~] **View**: gridlines and headings on or off; split the window (later).
      Gridlines done (office-F session, 17 Oct): View › Gridlines while editing, kept in the file (`SheetVisibility.gridXlsx`: the sheet
      view's showGridLines; `gridOds`: the sheet's ShowGrid in the view's Tables list, else the view's own — and the reader now takes
      the view's own when a file has no Tables list). `gridlines off and on` (both formats; LibreOffice 26.2 carries them neither way).
      Emulator: off.
      Headings done (office-F session, 18 Oct): View › Headings while editing — Excel's showRowColHeaders (a sheet's view), LibreOffice's
      HasColumnRowHeaders (the whole file's: its headings are one setting); read too (`Sheet.headings`), and off the grid has no
      letters and numbers, the cells from the edge, as Excel. `SheetVisibility.gridXlsx`/`gridOds` take the setting's name.
      `headings off and on` (both formats; LibreOffice carries them both ways); emulator: an .ods's off and on.
- [ ] **References**, where it makes sense: a **contents sheet** listing every sheet as a link (`#'Sheet'!A1`), for
      workbooks with many sheets; a **Sources sheet** of where the data came from, in the chosen style, with Words'
      dialogs. In-text citations in cells: not planned unless asked.

**From Excel's ribbon** (checked online 17 Oct: desktop Excel's tabs and groups, and the tablet app's shorter ribbon);
what is not already above:
- [x] **Home**: vertical alignment (top, middle, bottom); text angle (rotated, vertical); indent in a cell; Clear ▸
      contents, formats, all; Fill ▸ series (1, 2, 3…, dates, months) besides down and right; AutoSum on Home as
      Excel has it; **cell styles** (Good, Bad, Heading, Total…); sheet ▸ rename, tab colour, move or copy.
      Fill ▸ Series done (office-F session, 18 Oct): Home › Series with cells chosen — Excel's dialog: Linear, Growth, and for a date
      Day, Weekday, Month, Year, a step value; down each column (or along the row) from its first cell's number or date to the chosen
      cells' end (`SheetFill.stepped`; cell changes, one step of Undo). The fill handle now also runs Excel's own lists: month and day
      names, long and short, in their capitals and step (Jan, Mar → May). `month and day names and Fill series`; emulator: 15/03/2023
      by month down C2:C8.
      Cell styles done (office-F session, 18 Oct): Home › Cell styles — Excel's gallery (Normal, Good, Bad, Neutral, Calculation, Check
      cell, Explanatory, Input, Linked cell, Note, Output, Warning, Title, Heading 1–4, Total; Office theme colours), each drawn in its
      look; tapped, the parts Excel's style includes (font, fill, border) put on the chosen cells, the rest of their look kept
      (`SheetLook.STYLES`/`styled`; cell looks, one step of Undo). Saved as looks, not as named styles (Excel shows them as Normal).
      Fixed on the way: a double border in .ods written as LibreOffice writes Excel's (1.76pt double-thin — a bare "double" it took
      as thin) and read back as double. `cell styles xlsx/ods` (LibreOffice's conversions: its .xlsx of an .ods with a themed
      Default style drops every font colour — a LibreOffice quirk, it shows ours); emulator: Heading 1 on A1:D1.
      Vertical alignment, indent, text angle done (office-F session, 18 Oct): Home › Top / Middle / Bottom, Decrease / Increase indent,
      Orientation ▾ (Excel's: horizontal, angle counterclockwise / clockwise, rotate up / down) — cell looks; .ods now writes the angle
      (style:rotation-angle) and indent (paragraph margin, 0.2965 cm a level as LibreOffice turns Excel's) and reads the indent; a cell's
      angle is Excel's number everywhere now (an .ods angled down was read negative). `alignment xlsx/ods` (LibreOffice's conversions);
      emulator: Alpha angled, Βήτα indented twice.
      Clear ▸ done (office-F session, 18 Oct): Selection › Clear ▾ — Excel's Clear all, formats, contents, notes, hyperlinks (contents
      and formats as cell changes, one step of Undo; notes and links one change to the file, Words'-dialog parts `SheetNotes`/`SheetLinks`
      cell by cell). Emulator: A2's note cleared, B3 cleared of all (the total recalculated). No new logic beyond those tested parts.
      Sheet ▸ copy and tab colour, AutoSum on Home done (office-F session, 18 Oct): a sheet's tab › Copy (Excel's Move or Copy › Create a
      copy: "Name (2)" after it — .ods the whole table, .xlsx its cells, looks and links; shortcut: an .xlsx copy leaves its pictures,
      charts, notes and tables behind) and Tab colour (Excel's tabColor, LibreOffice's tableooo:tab-color; `SheetVisibility.colourXlsx/
      colourOds` share Hide's table-style code); Home › AutoSum. `a sheet copied and a tab coloured` (both formats; LibreOffice shows the
      copy); emulator: Numbers (2) made and its tab pink.
      Insert and delete cells shifting the rest done (office-F session, 18 Oct): Selection › Insert ▾ (Shift cells down / right) and
      Delete ▾ (up / left), and in the right-click menu — cell changes, one step of Undo (`SheetCellShift`): the cells after the chosen
      ones in their columns (rows) move, every formula on the sheet reading them follows (`SheetShift` given `within`: only those columns
      or rows; whole rows/columns and ranges partly in them stay); merged cells in the way refused, as Excel. `cells shifted`; emulator:
      D2 shifted down (B2's merge below refused). shortcut: merges, rules, validations, links, notes and other sheets' formulas stay.
- [ ] **Insert**: Equation and WordArt (Words'); header and footer (with Page layout); icons (later); slicers
      (later, with PivotTables).
- [ ] **Formulas**: Recently used; Insert function by category (as Excel's dialog: a search, the arguments explained);
      Calculate now and automatic / manual; trace precedents and dependents as arrows; evaluate a formula step by step.
- [ ] **Data**: import a CSV or text file into the sheet; Reapply and Clear the filter; advanced filter (later);
      Flash fill (later); Goal seek; consolidate (later); forecast (later).
- [ ] **Review**: next / previous comment, show all; notes (Excel's older comments) apart from threaded comments;
      workbook statistics (as Words' counts); protect the workbook's structure; accessibility check (later).
- [ ] **View**: page break preview and page layout view (with Page layout); freeze the first column, and rows and
      columns together; zoom to the chosen cells; show or hide the formula bar, headings, gridlines, sheet tabs.
- [ ] **Draw**: pen and highlighter over the sheet (Words' Draw tab), saved as ink.
- [ ] **From Words**: AutoSave, Save a copy, Save as, Rename; Find and Replace (above); Format painter (above); the
      keyboard bar while typing in a cell.

### D. Words
Its own open items stay in THE PLAN's Phase 2 (HANDOFF.md). Anything the UI/UX pass finds in Words goes into A.


## Calendar — the last app (designed 17 Oct, calendar session; not built yet)

**The user, 17 Oct:** "Everything to be handled as a file on phone, tablet and PC so any AI with the skill is able to
manipulate the events." Claude should organise, create and manage the calendar by editing files on the computer, and
the phone and tablet follow through sync. Many calendars, each with its colour; create, edit, delete; all-day and
timed; repeats; several reminders each, repeating ones ("notify for 3 days at 9:00 before"); import .ics and Google
Takeout, export .ics. Big screen like Google Calendar on the web (dark), phone like Outlook mobile. Pink icon
(~/Λήψεις/Calendar.png).

### The files (the heart of it)
```
Tetra/Calendar/                      (~/Tetra/Files/Calendar on the PC, /sdcard/Tetra/Calendar on a phone)
  Personal/                          one folder = one calendar; the folder's name is the calendar's name
    color                            one line: #F06292 (the calendar's colour; vdir's own convention)
    dentist-3f9a2c.ics               one event = one iCalendar file
    mum-birthday-81be07.ics
  Work/
    color
    staff-meeting-c2d410.ics         a repeating event is one file (its RRULE; a changed or cancelled day inside it)
```
- **Why this layout**: it is "vdir", the layout khal, vdirsyncer and todoman already use — a standard, not ours. One
  event per file means an AI (or a person) adds, changes or deletes one event by touching one file, and sync carries
  only that file. A whole-calendar .ics would make every edit a change to one big file, and two devices editing two
  different events would be a sync conflict.
- **Why visible, not a hidden `.calendar` like Notes**: Files sync already carries everything under Tetra that does not
  start with "." (DriveManifest.walk), per file, edits replacing the other side's copy (SYNC_PLAN §6ak). So the Calendar
  needs **no new sync code** on either side, and Claude finds it where the user sees it. Notes needed their own
  endpoint because one note file changes often from both sides; events rarely do.
- **Inside an event file**: standard iCalendar (RFC 5545) — `VCALENDAR` › `VEVENT` with `UID`, `DTSTAMP`, `SUMMARY`,
  `DTSTART`/`DTEND` (all-day as `VALUE=DATE`, the end the day after), `LOCATION`, `DESCRIPTION`, `RRULE` + `EXDATE`
  for repeats, `VEVENT`s with `RECURRENCE-ID` in the same file for one changed day, `VALARM`s for reminders,
  `LAST-MODIFIED`, `SEQUENCE`. Times carry an IANA zone (`DTSTART;TZID=Europe/Athens:20261020T093000`); Tetra does
  not write VTIMEZONE blocks inside the folder (an AI need not either) but keeps any a file has, and adds them when
  exporting for other apps. Reading is forgiving (LF or CRLF, long or folded lines, UTC `Z` and floating times);
  writing is strict (CRLF, folded at 75). **Anything Tetra does not understand in a file is kept as it was** (attendees,
  X- properties, Google's ids): an edit changes only the lines it changes.
- **File names**: Tetra names a new event after its title plus six letters of its UID (`dentist-3f9a2c.ics`) and never
  renames it (a rename would be a move in sync for nothing). The name means nothing to the reader: the UID inside is
  the identity, and a file an AI writes may be called anything ending in `.ics`.
- **Deleting** = moving the file into `Tetra/Trash/` (sync never deletes; a move into Trash crosses as a move, §6h).
  An AI deletes with `mv`, not `rm` — an `rm`'d file would come back from the other device.
- **Moving an event to another calendar** = moving its file to the other folder. **A calendar** = a folder: make one
  with `mkdir` + a `color` file; rename = rename the folder; delete = the folder to Trash.
- **Conflicts**: an event changed on two devices between syncs arrives as `x (2).ics` beside `x.ics`, both with the
  same UID. The Calendar shows one (the newer `LAST-MODIFIED`/`DTSTAMP`) and marks it "2 versions"; the event's card
  offers "Keep this one" (the other goes to Trash). Nothing is thrown away by itself.
- **Which calendars are shown** (the checkboxes) is per device, not in the files — like Google, a choice about this
  screen. `Calendar` becomes a required folder (emblem; cannot be renamed, moved or trashed — DriveRules), on the
  desktop too.

### Repeats and reminders
- **Repeats** in the editor: Does not repeat · Daily · Weekly on Tuesday · Monthly on the 3rd Tuesday / on day 20 ·
  Yearly on 20 October · Every weekday · Custom… (every N days/weeks/months/years, which weekdays, ends never / on a
  date / after N times) — all plain RRULEs. Changing a repeating event asks: **This event · This and following · All
  events** (one day = a `RECURRENCE-ID` override; this-and-following = an `UNTIL` on the old one and a new file from
  that day; delete one day = an `EXDATE`).
- **Reminders**, several per event, each a `VALARM`: At the time · 5, 10, 15, 30 min · 1 h · 1 day before · Custom
  (N min/h/days/weeks). For all-day events: "On the day at 9:00", "N days before at 9:00". **Repeating reminder**
  ("for 3 days at 9:00 before") = one VALARM with `TRIGGER` at the first one (3 days before, 9:00), `REPEAT:2` and
  `DURATION:P1D` — RFC 5545's own repeat, so other apps read it too (Google drops the repeats on import, keeps the first).
  Moving a timed event in Tetra keeps such a reminder at 9:00.
- **New event defaults**: 30 min before (timed), the day before at 9:00 (all-day); a setting.

### Notifications
- **Phone/tablet**: one exact alarm for the **next** reminder only (`AlarmManager.setExactAndAllowWhileIdle`,
  `USE_EXACT_ALARM` — allowed for a calendar app — and `SCHEDULE_EXACT_ALARM` on older Android). When it fires: post
  that reminder (channel "Reminders", high; title, time, place, the calendar's colour; **Snooze 10 min** · tap opens
  the event), then plan the next. The plan is always made **from the files** (an index cached by path + size + time, so
  a thousand events cost a glance), never from a list that could be stale. It is re-planned when the app opens, after
  any edit in Calendar, **after every sync that brought a Calendar file** (also the background SyncJob, app closed),
  on boot, on a time or time-zone change and after an update. A reminder missed while the phone was off is shown on
  boot (up to a day late, marked "missed").
- **Xiaomi**: alarms survive, but a reboot reaches the app only with **Autostart** allowed, and battery saver should be
  "No restrictions" for Tetra; the Calendar asks once, with a button to each setting, and shows a line if notifications
  or exact alarms are off.
- **PC**: the desktop's own `notify()` (Electron notification + the Notifications page), from a timer in the main
  process — it runs while Tetra sits in the tray. The desktop does not start at login yet (SYNC_PLAN §H); Calendar is
  the reason to add that (an autostart .desktop file, a setting).
- Every device reminds (as Google does); Settings › Calendar › "Reminders on this device" switches one off.
- **How fast an AI's edit reaches the phone**: as fast as sync — the background sync runs every 30 min by default (a
  setting), or at once when Tetra is opened. An event Claude adds for "in 10 minutes" will ring on the PC, and on the
  phone only if a sync happens first.

### Import and export
- **Import .ics** (Calendar › Import, or opening an .ics from Files / another app): into a chosen calendar or a new one
  named from the file (`X-WR-CALNAME`, else the file name). Split into one file per UID (overrides stay with their
  event, the VTIMEZONEs they use come along). Importing the same file again **updates** events by UID, no duplicates.
- **Google Takeout**: the Takeout zip (or its `Calendar` folder): each `<name>.ics` becomes a calendar of that name,
  colours from the palette (Takeout carries none). Shows the count per calendar before writing.
- **Export**: a calendar → one .ics (with VTIMEZONEs, `X-WR-CALNAME`, its colour as `X-APPLE-CALENDAR-COLOR`) to share
  or save; all → a zip of one .ics per calendar. Opens in Google Calendar, Outlook, Thunderbird, Apple.
- Not planned: subscribing to an online calendar by URL, Google/CalDAV live sync (no cloud — ask if wanted).

### Screens
- **Big screen** (tablet ≥ 700 dp wide, the PC) — Google Calendar web: top bar ☰ · Today · ‹ › · "October 2026" ·
  Day / Week / Month / Agenda; **left panel** (☰ hides it): **Create**, a small month (days with events dotted, tap to
  go), **My calendars** — each with its coloured checkbox, ⋯ for colour / rename / export / delete, + New calendar,
  Import; **main area**: the month grid (default; events as coloured chips, all-day as bars, "+3 more"), the week and
  day grids (hours down the side, events as blocks, the now-line in red, all-day row on top; drag to move or resize on
  the tablet with a finger, on the PC with the mouse; tap an empty slot to create there). An event tapped opens its card
  (popover on the PC, side sheet on the tablet); Edit opens the editor as a dialog.
- **Phone** — Outlook mobile: the top is the **week row** of the chosen day (days with events dotted), swipe for the
  next week, **pull it down to the whole month** and back up; below it the **agenda**: days one after another from the
  chosen one, each event as time · length · coloured dot · title · place, all-day ones first; scrolling the agenda moves
  the week row along. + (bottom right) creates; ☰ opens the calendars (the same checkbox list) as a drawer. Views
  menu: Agenda (default) · Day · 3 days · Month. The editor is full-screen. A tablet in portrait is a phone layout
  only if under 700 dp.
- **Editor** (both): title; calendar (coloured chip); All day; start and end date/time (a zone line only if not the
  device's); Repeat; Reminders (list + Add); Place; Notes. Delete in the event's card and the editor's ⋯.
- Dark and light follow Tetra's theme; English only (strings in code like the rest, localisation later).
- **Home**: a pink Calendar group — the big card (the user's icon) with **Today's next event** and **New event**
  beside it; the launcher icon as the other applets (Settings › App icons). Colour `applet_calendar` = the icon's pink.

### The skill (`tetra-calendar`)
In `skills/tetra-calendar/` (repo) and `~/.claude/skills/tetra-calendar/`: SKILL.md — where the folder is, the layout,
ready-to-copy templates (timed, all-day, repeating, with reminders, a moved or cancelled day), how to move, delete
(to Trash), make/rename a calendar, the rules (UID unique, DTSTAMP, bump `LAST-MODIFIED`/`SEQUENCE` on edit, IANA
zones, never `rm`), and that the phone sees it after the next sync. Plus `calendar.py` (standard library only):
`agenda FROM TO` lists events with repeats expanded (so Claude can answer "what's on next week" and find free time
without doing RRULE in its head), and `check` validates every file (missing UID or DTSTAMP, end before start, duplicate
UIDs, unknown zone, bad RRULE) — run after every edit.

### Code (smallest that holds)
- **Android**: `Calendar.kt` (the vdir: list calendars, read/write/move events, the content-line reader/writer that keeps
  unknown lines, the occurrence expander), `CalendarScreen.kt` (both layouts, editor), `CalendarAlarms.kt` (planner,
  receiver, boot). Repeats expanded with **lib-recur** (dmfs, the RRULE library of Android's OpenTasks, 0.17.1, small, pure Java) rather
  than our own — RFC 5545's RRULE (BYSETPOS, negative BYMONTHDAY, WKST…) is where hand-written code goes wrong.
  Hook: `SyncClient.receiveFile`/sync result → re-plan alarms when a path under `Calendar/` changed.
- **Desktop**: `calendar.js` (main: the vdir, `fs.watch` on Calendar so an AI's edit shows at once, the reminder timer),
  `src/Calendar.tsx` (the Google-web layout), `CALENDAR_CALLS` IPC like Notes; **ical.js** (Mozilla's, Thunderbird's)
  for parsing and repeats; `calendar.js` added to `build.files`.
- **Shared test fixtures** (`test/resources/calendar/`): Google's, Outlook's, Thunderbird's and hand-written (AI-style)
  files, and RRULE cases with their expected dates — run on both sides so the two engines agree.

### Steps (each a commit, each checked on the tablet, then the phone and PC)
- [ ] 1. Android: the files — `Calendar.kt` read/write/expand + the fixtures, unit tests; `Calendar` a required folder.
- [ ] 2. Android: the screens read-only — big-screen month/week/day + side panel; phone week row + agenda; Home card,
      launcher icon (tools/applet-icons.py with Calendar.png).
- [ ] 3. Android: create, edit, delete; calendars (new, colour, rename, delete, show/hide); repeats with the three-way
      question; drag on the tablet.
- [ ] 4. Android: reminders and notifications (planner, exact alarm, boot, snooze, after-sync re-plan, Xiaomi prompts).
- [ ] 5. Android: import .ics and Takeout, export.
- [ ] 6. The skill + `calendar.py`; Claude makes and changes events on the PC, the tablet shows and rings after sync.
- [ ] 7. Desktop: `calendar.js` + `Calendar.tsx` (view, edit, import/export), reminders, start at login.
- [ ] 8. The real pair: an event made on each device and by Claude, edited on two at once (conflict), deleted, all
      three agree; a reminder rings on the tablet with the app closed and after a reboot.


## The user's original wishlist (Sept 2026, unchanged)

- The desktop and Android notes we made work like a charm
- I want to make the same thing (replasment of keep) but for google Drive
- the same functionality of Notes with the sync fuction on deferent networks, but with a file manager files and folders, tags, 
- the program will setup a local folder "Drive" like the sync thing does but I dont want the coplex UI, permitions and stuff.
- We could have a file hystory, a .history copy of all files edited on the computer, with a setting for how meny edits back disabled, 5,10,100. Time based check for changes every 5/10/15min
- sync progerss bar, files remaining.
- smart popup keep rename replse, like a file manager
- open all files with local programs this app will do nothing more than sync and organize in tags folders and "backup"
- I would like to have an Android and Linux version
- an extra thing I would like on the android side is document scanning with filtering, marking, page detection with save as PDF or open document.
- a small algorithm that can detect duplicates and organize in to proper folders after analizing the stracture or sugest deletions, (thats an Idea, it could be an algorithm or a small AI) for example when I scan a document and I name it with a number and date and name it Αίμα ή Αιματολογικές I would like for it to automaticaly sugest the correct folder where the other fiels of the same name are. So I don't have to change location.
- And later I would like it to have a photo viewer so I can get rid of my Google Photos as well. so 2 tabs at the bottom files/photos something like that.
- Did I say on option to set pholder location  at the beginig (on android will be localstorage thing/LocalDrive)
- Take a look at the nots app and take what you need.
- I made a git repo https://github.com/kalotrapezis/Drive, https://github.com/kalotrapezis/Drive-Android
- USB drive handling for Android.
- Automatic Backup ones the app detects a spesific predeterment USB device name.
- Files tab ( File manager - Local Google Drive) Photos tab (Google Photos Galery with metadeta and basic edditing)
- Photo Import (From Google Takeout, file muging handling, show, edit)

Files
copy, move, rename, Delete(trashbin), tag. More (quick share, Bookmark, compress, properties, settings)
Settings (Use resicle bin by default, Show resicle confocmation, Use bookmarks, Show home, hide/unhide,   Show open as)
Toolbar (Search, Setting pannel ((toggle) Apply to all folders), view list, list info, grid,small medium large, short name, time created, time eddited, size, type, asseding desending, Show hidden files)
At the left side of the toolbar back button up forword.
Navigation Address bar (clickabble) "Home>mainstorage>downloads" long click to copy.
Home Page grid (Main storage/Drive), Mounted Devices(with a database and loading symbol if is the designated drive for buckup and it is active), Storage analysis (the file types and how much storage conume how much is left), Images,Audio,Videos,Documents,Apps,New Files,Local Drive,Photo Galery.

Photos
Google Photos Clone without the Google stuff.
Photo gallery with day,month,year pinch in and out to change, or zoom
Collections, New (), Search, Notifications,
Documents are an analysed local collection; see `AI_COLLECTIONS_ARCHITECTURE.md`.
Collections examples (camera, viber, pictures, documents, downloads, messemger, moveis, videos)
Filters by file types, favorites, new files, Private, tags, resicle bin, screenshots.
To me it looks a Photo Gui file manager so Its not that deferent.
What could we add? document detection, Face detection, Symantic search? Like clouds, sky, smile face, (system tags) for the search.
Open in Photo galery mode, Photo galery Icon to open directly there.

See what do we need to borrow from the dirve app for the photos app vew thing.


Floating bar with Files/Photos/Drive/New PDF scanning for Android
