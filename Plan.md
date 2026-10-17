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
- Other sessions: add your line here when you start.

## Waiting on the user
- (none now — Slides' drop cap answered 17 Oct, see B › Insert)

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
- [ ] **Touch points**: a script dumps each screen's layout (uiautomator) and flags any tappable thing smaller than
      48 dp (compact mode excepted only with a mouse). Plus a hand check of resize handles, small chips and close
      buttons, things near the screen's edges (Android's back gesture), and gestures that fight (pinch against drag).
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
- [ ] 5. The engine moved into its own file (`OfficeText.kt`): a pure move, nothing changed.
- [ ] 6. (with C) Tables' wrapped, shrink-to-fit and rich-text cells drawn by the engine; rich text read and written.
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
- [ ] **Insert**: Text box (plain; today only shapes); Chart (Words' chart editor); Link (to a web page or to a
      slide); Header & footer (slide number, date, footer text); Symbol; Equation (Words'); WordArt; Icons (later);
      Audio and video (later).
- [ ] **Arrange** (on the Selection tab, as PowerPoint's Shape Format): bring forward / send backward / to front /
      to back; align (left, centre, right, top, middle, bottom, to the slide); distribute; group and ungroup; rotate and
      flip; exact size and position; a picture's **crop** (drawn since 780a88d), replace, reset, border; a shape's
      outline width and dash, shadow; Shape styles (Words').
- [ ] **Slide**: change an existing slide's layout, reset it; sections; a **slide sorter** (a grid, dragged to
      reorder, many at once); copy slides between decks.
- [ ] **Design**: slide size (16:9, 4:3); format background (colour, gradient, picture); the theme's colour
      variants.
- [ ] **Draw**: pen and highlighter on a slide while editing (Words' Draw tab), saved as ink in the file.
- [ ] **Review**: comments (PowerPoint's own, read and written); spelling in the typing field.
- [ ] **View**: Outline (every slide's words as one list to edit, good on a phone); notes page; editing the slide
      master; grid and guides with snapping; zoom.
- [ ] **File**: Find and Replace across slides; export each slide as a picture; Print with handouts; Versions.
- [ ] **Slide show**: rehearse timings; loop until Esc (kiosk); custom shows (later); more animation kinds and an
      animation list (later).
- [ ] **References** (the user's choices, 17 Oct): Sources (Words' dialogs, Import, Paste, Ask Claude), kept in a .pptx
      in Word's bibliography part, which PowerPoint keeps (an .odp needs its own place: to decide); **live**
      citations, inserted from the typing bar and rewritten when the style or a source changes (PowerPoint has no
      citation fields, so they are found in the words by the deck's sources and style); References slide(s) at the end
      in the style (APA 7, MLA 9, Chicago, Harvard, IEEE), spread over more slides when long, with Update; a **Contents
      slide** after the title slide: each slide's title and number, and in a show a tap on a title goes there
      (`ppaction://hlinksldjump`); Update after slides move.
- [ ] Accessibility: alt text for pictures.

**From PowerPoint** (checked online 17 Oct: PowerPoint's ribbon — Home, Insert, Draw, Design, Transitions, Animations,
Slide Show, Record, Review, View — and Microsoft's page comparing PowerPoint on each platform); what is not listed above:
- [ ] **Opening other people's decks well** (most important — a deck that looks wrong can't be presented): **SmartArt**
      drawn from the drawing the file saves with it (check: not found in Slides' code); a deck's **video and audio
      played** in the show (today neither is); its embedded fonts used; equations drawn (Word's OMML, as Words');
      hidden slides skipped in the show; an empty placeholder shows "Click to add title/text" and typing fills it.
- [ ] **Selection pane**: every object on the slide in a list — choose one under another, hide or show it, rename it,
      reorder by dragging (the only way to reach a covered object on a tablet).
- [ ] **Picture**: picture styles (frame, shadow, rounded corners — the tablet app has these); transparency; colour
      and brightness; compress pictures; remove background (later).
- [ ] **Shapes**: text effects (shadow, outline, glow) and character spacing; merge shapes (union, combine,
      intersect, subtract — later); edit points (later).
- [ ] **Transitions**: duration; sound (later); advance on click and/or after a time set here (the file's `advTm`
      is played today, not set); **Morph** (later: the most used modern transition).
- [ ] **Animations**: start on click / with previous / after previous; duration and delay; entrance, emphasis and
      exit kinds; the animation list (pane) to reorder; animation painter; motion paths and triggers (later).
- [ ] **Slide show**: go to a slide by number or from a grid during the show; keyboard keys as PowerPoint's (N/P,
      arrows, B/W screen, number + Enter, Esc); keep or discard the pen's ink at the end, saved into the deck;
      set-up options (loop, without animations, use timings or not); subtitles (later).
- [ ] **Record** (later): record narration and timings per slide; export the deck as a video.
- [ ] **View**: reading view; ruler; notes and handout masters (later); grayscale preview (later).
- [ ] **File**: Save as template (.potx, into Tetra's Templates folder, where Design › Themes finds it); Save as
      .odp / .pptx; remove notes and comments before sharing (Inspect); password protection (later).
- [ ] **Text**: right-to-left text and text direction (vertical); highlight (planned in Home above).
- [ ] **Insert**: Zoom (summary and slide zoom — later); action buttons (as Link to a slide); photo album (later).
- Not planned: Designer and Copilot (need Microsoft's cloud), co-authoring and @mentions (no cloud), online pictures
  and YouTube (no network services), Cameo, translate, compare presentations.

**From Words** (asked 17 Oct: "transfer the rest of the features from Words to Slides" — Slides' Insert had only
Picture, Shape and Table). Each Words button checked against what a slide can hold; what is already listed above is not
repeated. Words' code is reused wherever it fits (its dialogs, strips, pickers).
- [ ] **Selection** (Slides has none of these): Cut, Copy, Paste a shape, picture or table (on another slide, in
      another deck); Paste text only; Select all on the slide; Copy formatting / Paste formatting.
- [ ] **Home** (with the runs above): superscript and subscript; Smaller / Bigger text; Aa (change case); multilevel
      lists; Words' paragraph page (More…: indents, spacing before and after, line spacing exactly); a text box's
      border and shading (as Words' Borders, written as the shape's line and fill).
- [ ] **Insert**: **Drop cap** — PowerPoint and Impress have none, so it is either a raised cap (the first letter a
      bigger run of its own, which every app shows) or a dropped one (the letter in its own box, the paragraph's first
      lines indented beside it: looks right everywhere but doesn't reflow). **The user, 17 Oct:** "these are options,
      not one choice: make a modal and add the options", then "it could also be a ribbon of cards with an example
      each, like Words has for pages". So: a strip of cards in the ribbon's place (as Page numbers'), one per kind —
      None, Dropped, Raised — each a small paragraph showing it; a More… card opening the dialog with the finer
      settings (lines to drop, distance from the text, a preview). Words' own Drop cap made the same if it is not
      already, so the two match (one shared piece). Date and time
      (fixed, or a field that updates, as PowerPoint's); line break inside a paragraph; Comment; a link's Edit
      (beside Link above). Slide numbers come with Header & footer.
- [ ] **Picture / Shape** (Words' Picture tab): Add text to a shape, Edit text; Shape options (fill and outline
      colour, besides width and dash above); Save picture as; Edit data on a chart, Edit equation, Edit drawing.
- [ ] **Table** tab when a table is chosen (Words'): row above / below, column left / right, delete row, column,
      table; merge and split cells; borders and shading; table styles; Make chart from the table.
- [ ] **Layout** for a text box: inner margins; columns in a text box (PowerPoint's `numCol`); no wrap.
- [ ] **Design**: the theme's fonts (Words' Fonts), besides its colours above.
- [ ] **Review**: Read aloud (a slide's words, or its notes), as Words'.
- [ ] **File**: AutoSave, Save a copy, Save as, Rename (as Words', beside A's list).
- [ ] **While typing**: Words' keyboard bar (bold, italic, underline, highlight, voice typing) over the keyboard.
- Not for slides: page breaks, blank page, watermark, page borders, line numbers, hyphenation, footnotes and
  endnotes, captions and cross-references, track changes, content controls, style sets (PowerPoint has none of these).

### C. Tables — what Excel and Calc have that Tables lacks
- [ ] **Home**: merge and centre (and unmerge); column width and row height (typed, and AutoFit to the contents);
      hide and unhide rows, columns and sheets; insert and delete cells shifting the rest; Fill down and right; cell
      styles; **Format as table** (make an Excel Table); **conditional formatting** made here (it is only read today);
      Find and **Replace**; Format painter.
- [ ] **Insert**: **Chart from the chosen cells** (Words' chart editor, the series from the cells); picture; shape
      and text box; link; **notes and comments** (they are only shown today); symbol; a checkbox in a cell; a drop-down
      (data validation list); sparklines (later); PivotTable (later).
- [ ] **Formulas**: browse functions by category (the typing row exists); named ranges (Name Manager); show what a
      cell uses and what uses it; error checking; more functions — the dynamic ones (XLOOKUP, FILTER, SORT, UNIQUE),
      checked against LibreOffice as the 129 are.
- [ ] **Data**: a sort that changes the file, by several columns; the filter saved as the file's AutoFilter; remove
      duplicates; text to columns; data validation; group and outline rows; subtotals.
- [ ] **Page layout**: margins, orientation, paper size, print area, rows repeated on every page, fit to pages, header
      and footer, gridlines printed — today Export PDF and Print use the file's own setup, which can't be changed.
- [ ] **Review**: protect a sheet (respect a protected one: no typing in locked cells); spelling.
- [ ] **View**: gridlines and headings on or off; split the window (later).
- [ ] **References**, where it makes sense: a **contents sheet** listing every sheet as a link (`#'Sheet'!A1`), for
      workbooks with many sheets; a **Sources sheet** of where the data came from, in the chosen style, with Words'
      dialogs. In-text citations in cells: not planned unless asked.

**From Excel's ribbon** (checked online 17 Oct: desktop Excel's tabs and groups, and the tablet app's shorter ribbon);
what is not already above:
- [ ] **Home**: vertical alignment (top, middle, bottom); text angle (rotated, vertical); indent in a cell; Clear ▸
      contents, formats, all; Fill ▸ series (1, 2, 3…, dates, months) besides down and right; AutoSum on Home as
      Excel has it; **cell styles** (Good, Bad, Heading, Total…); sheet ▸ rename, tab colour, move or copy.
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
