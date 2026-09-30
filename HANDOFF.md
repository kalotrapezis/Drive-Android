# 30 September — end of the long day: where things stand (read this first)

**State.** Branch `writer-library`, everything committed. All unit tests pass
(`ANDROID_HOME=~/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug`).
The latest build is on the **tablet** (971f6b37). The **phone** (208c8192) has the build from before spelling, footnotes,
the icons and comment threads — install `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` there first and check
those at phone size (every feature is for both screens — memory `writer-tablet-first`). The untracked `Assets/`
screenshots are the user's: leave them. Every device check today was done in new, unsaved documents (or my own test
files), all removed after; none of the user's files were touched. Test prefs I set on the phone were removed.

**Done today, after the morning's Draw/Design/References/APA** (details in the entries below, newest first):
- **Review › Comments: replies and Resolve** (last thing, see "Comment threads" below) — Word's commentsExtended,
  LibreOffice's loext:parent-name / loext:resolved; checked on the tablet and through LibreOffice both ways.
- **Icons** on Layout, References, Picture, Table; Delete row / Delete column drawn as the add icons with an ×
  (the user: delete actions get their own icon, not one bin everywhere — memory `ribbon-icons`).
- **References › Footnotes and endnotes** (both kinds, pages show them at the foot / after the text).
- **Review › Spelling** (the system spell checker, Greek + English; worked after a tablet restart — Gboard's link was stuck).
- **The phone:** every ribbon tool reachable (long groups split, a too-wide page scrolls).
- **Review › Track changes**: show, accept/reject, record (Word files).

**Asked by the user and not done yet — do this next:**
1. **Word and character counter** (asked 30 Sep evening): on the **tablet** in the top bar, counting the whole document,
   and the **selection** when text is selected (as Word's status bar). On the **phone** there is no room: in
   **File › About this file** (it already shows the word count) — add characters (with and without spaces) and,
   when text is selected, the selection's counts too. The user asked for exactly this split.
2. Then the list's smaller items: the APA abstract page and run-in Level 4–5 headings; tapping a citation to edit it;
   more source kinds (report, thesis, video); a picture watermark; page border styles.
3. Offered, not asked for yet: Tetra's own Hunspell dictionaries (spelling without Gboard); receiving shares from the
   Claude / ChatGPT apps into Tetra (the user asked how the apps could connect; the share menu is the way on mobile).

**Known gaps worth remembering**
- Nothing checked in Word itself (no Word here); every Word form was written as Word writes it, and LibreOffice reads
  them all. The user can check with Word when convenient.
- ODT tracked changes still make a file copy-only; Word fields other than citations (TOC field, PAGE in the body,
  cross-references) too.
- Editing a footnote's words makes it plain text in its style; a new document's footnotes show at text size until saved.
- Tracking (Track changes on) is not remembered in the file (Word's w:trackRevisions).
- The editor (typing view) does not show drop caps, watermarks, page borders — the pages view does.

---

# 30 September, night — Review › Comment threads: replies and Resolve

State: `writer-library`, committed; all unit tests pass; the build is on the **tablet**, not the phone.

- `DocComment.parent` (a reply: the comment it answers) and `.done` (resolved). `OfficeFormats.withReply` (the reply
  on the same letters as its comment), `withDone` (a thread resolved or reopened), `withoutComment` now takes a
  comment's replies with it (Word's).
- **Word:** read from commentsExtended.xml (w15:commentEx paraId / paraIdParent / done) by each comment's last
  paragraph's w14:paraId; written anew — a paraId given to a comment's last paragraph when it needs one (w14 declared
  on the comments root), the part and its relationship made when missing. **ODT:** LibreOffice 7's
  `loext:parent-name` and `loext:resolved` on office:annotation (loext declared when used) — the forms taken from a
  file LibreOffice itself wrote through UNO (a reply and a resolved comment).
- Cards (tablet pane, phone sidebar): replies under their comment; Reply (name + text, the name remembered),
  Resolve / Reopen, Delete; a resolved thread greyed with "Resolved", its words not tinted unless the cursor is in them.
  Replies get no tint or card of their own.
- Checked: `CommentThreadsTest` (both kinds: reply linked, parent resolved, again the same, reopened, deleted with its
  reply); LibreOffice reads Writer's .docx and .odt threads as linked and resolved; tablet: comment → Reply →
  Resolve, as described. Not checked: the phone's sidebar with a thread; Word itself.

---

# 30 September, night — References › Footnotes and endnotes

State: `writer-library`, committed; all unit tests pass; latest build on the **tablet**, not the phone.

- Model: `DocRun.note` = `DocNote(end, paras)`; the reference's letters are its number (`OfficeFormats.numberNotes`:
  footnotes 1, 2…, endnotes i, ii… — Word's and LibreOffice's defaults), set on reading and before the pages.
  Carried through typing like citations (typed letters never join a note).
- Word: footnotes.xml / endnotes.xml read (separators skipped, the space after the number dropped) and written anew
  (separators kept or made, each note's paragraphs written as the body's with its footnoteRef first, the part and its
  relationship made when missing); references written as w:footnoteReference with superscript. `FootnoteText` /
  `EndnoteText` added to styles when a new note uses them. ODT: `text:note` read and written inline; `Footnote` /
  `Endnote` styles likewise. **Fixed on the way:** the new ODT builtin styles used a `text:` attribute the blank's
  styles.xml does not declare — every ODT's page setup read as default until removed (caught by the unit tests).
- Pages: a line's footnotes must fit on its page with it (`limitWith`); each page's footnotes at its foot under a
  short line; endnotes after the text under a short line. Tables leave the footnotes' room.
- Editor: References › Insert footnote / Insert endnote (at the cursor; the dialog opens for its words); the cursor at a
  note's number shows "Footnote 1 · its words · Edit"; the dialog edits or deletes it.
- Checked: `NotesInDocsTest` (both kinds round trip, again unchanged; the suite's 04 file keeps both notes, not
  copy-only); LibreOffice renders Writer's files with the notes numbered 1 and i; tablet: a new document, Insert
  footnote after "1905.", its words typed, the page shows ¹ in the text and the note at the foot under the line.
- Not yet: a new document's notes show at text size until saved (the blank's styles lack Footnote Text); editing a
  note's words makes it plain text in its style (its own bold/italics are kept only while unedited); the phone.

---

# 30 September, late evening — Review › Spelling (built; the tablet's checker does not answer)

State: `writer-library`, committed; unit tests pass; latest build on the **tablet** and not the phone.

- `WriterSpelling.kt`: the system spell checker (Gboard's) through `TextServicesManager`, a session per language
  (Greek words → el-GR, the rest → en-US), words asked in batches of 40, answers remembered for the session. Android
  12+ sessions (`SpellCheckerSessionParams`, answers on the main thread).
- Editor: a red wavy line under words that look misspelled (not the one being typed); the cursor in one shows its
  suggestions above the tabs (tap one to put it in; Ignore). Review › Spelling (on/off, remembered, pref
  "spelling") and › Next spelling mistake. Nothing in the file changes; the pages do not show the lines.
- **Not working yet on the tablet:** Gboard's spell checker never answers. `dumpsys textservices` shows the system's
  bind group to it `mConnected=false`, our request pending — the system never reached the service (spell checking off
  in Gboard/system settings, or HyperOS blocking it). Asked the user to check the settings (not changed by me —
  system settings). Writer now says so on the screen ("The phone's spell checker is not answering — turn on…") and
  stops asking after the first silence.
- **After the user restarted the tablet it works:** the stuck bind cleared; red wavy lines under speling, tset,
  documnt…; the cursor in one showed "spelling · speaking · spellings · Ignore"; a tap replaced it. Greek not tried
  from here (adb cannot type Greek) — the user can check. If it goes silent again, a restart fixes it; bundling our
  own Hunspell dictionaries (LibreOffice's el_GR / en_US, ~5 MB) would remove the dependence on Gboard — offered, not
  asked for yet.

---

# 30 September, evening — the phone at last: every ribbon tool reachable

State: `writer-library`, all committed; unit tests pass; the latest build is on the **phone** (208c8192). The tablet
was unplugged before the last two builds (it lacks the between-changes fix and this ribbon fix) — install there next.

- Phone walk (new unsaved documents, discarded): Review (all pages, name dialog, recording, Accept/Reject), Design ›
  APA 7 paper (dialog fits; pages show TNR, number top right, indent), Draw on page (tools row scrolls to Move/Undo;
  the stroke lands where drawn). Drop cap / citations dialogs not opened on the phone.
- **Found on the phone:** Design was one page wider than the screen — Watermark, Page colour and Page borders could
  not be reached at all; Insert's Comment and Symbol, Layout's Columns and References' list were cut off.
  Fixed two ways: groups split where their dividers were (Design: APA | spacing | watermark·colour·borders; Insert:
  pictures | link·comment, drop cap·date·line break | symbol; Layout: margins·orientation | size·columns;
  References: citation·sources | list·update) — the tablet looks the same, the dividers now between groups; and a
  phone page wider than the screen scrolls sideways before the swipe turns the page.
- Removed from the phone after: the test drafts, the test reviewer name, the pens the test drawing saved.

---

# 30 September, evening — Review › Track changes: recording my own changes

State: `writer-library`, all committed; all unit tests pass; the build is on the **tablet** (971f6b37); phone still
not connected.

- **Track changes** button first on the Review tab (Word files only): asks the reviewer's name once (prefs
  "writer"/"author", shared with comments). On: every edit is compared with the document before it
  (`TrackChanges.record`): typed letters are my insertions; deleted letters come back struck through as my deletions
  (my own insertions just go); Enter / a join are a paragraph mark inserted / deleted; a table deleted is deleted.
  The cursor lands before letters struck through, so Backspace walks on past them, as in Word.
- The edit is found by common start and end, **bounded by the cursor** (found on the tablet: in "well" Backspace
  struck the wrong l — two equal letters side by side are ambiguous without it); letters that stayed keep their
  inserted/deleted state from before the edit. The baseline is taken when tracking is switched on (found on the
  tablet: text typed before counted as my insertion).
- Checked: `TrackChangesTest` (typing, Backspace twice, over a deleted letter, the doubled letter, own insertion,
  Enter and join through a Word file, typing over a selection); tablet, a new unsaved document: "The cat sat well.",
  Track changes on, Backspace ×4, "badly" → the draft's Word XML has w:ins "badly", w:del "well"; LibreOffice reads
  them as insertion/deletion by the name. The test draft was removed after.
- **Phone (208c8192), same evening:** installed; Review fits the paged ribbon (Track changes | ‹ › Accept Reject |
  Accept all Reject all | Comments), the name dialog, recording with Gboard — all as on the tablet. **Found there:** with
  the cursor exactly between two changes (after typing "badly" before a struck "well"), Accept/Reject took both —
  rejecting the deletion too. Now one: the change the cursor is inside, else the one just before it (`changeAt`, also
  the bar's). The phone's test draft and the test reviewer name ("Test") were removed after; the phone has no Writer
  name saved, so the user is asked the first time.
- Not yet: tracking is not remembered in the file (Word's w:trackRevisions in settings.xml) nor across reopening;
  newly typed insertions are underlined at once but take the author's colour only at the next redraw; changes made by
  Writer's own tools (paste is typing, but a citation, a style…) are recorded only as far as their letters go;
  someone else's insertion deleted goes outright (Word marks it deleted-inserted).

---

# 30 September, late afternoon — Review › Track changes (Word files)

State: `writer-library`, all committed; all unit tests pass; the build is on the **tablet** (971f6b37). The phone was
still not connected — first thing next time: install there and check Review (and the 30 Sep features) at phone size.

- The next item on the 30 Sep list (the user had picked APA first): **Review › Track changes** — a professor's
  corrected .docx was copy-only.
- Model: `DocRun.change` = `DocChange(deleted, author, date)`; Word's w:ins/w:del (and moveTo/moveFrom) read and
  written (`delText`, ids from 90000); rPrChange carried raw in `DocRun.font` (written last in rPr); pPrChange a kept
  paragraph part; a paragraph mark's w:ins/w:del stays in the kept "rPr" part. Carried through typing like comments
  (`carryFonts`: typed letters inside someone's insertion join it; never a deletion).
- `TrackChanges.kt`: `list` (for the editor), `decide` (chosen changes), `all` (tables' rows and table/row/cell
  property changes too), `color(author)`. Rejecting a formatting change reads the old rPr (`OfficeFormats.docxLook`);
  a paragraph's old spacing/indents/alignment come back (its style and list stay — ponytail note).
- Editor: the author's colour as a mark with alpha 0xFD (never saved); underline/strike, a margin bar and ¶ drawn over
  the text. Pages (and PDF): coloured underline/strikethrough. **Review tab**: ‹ › Accept Reject | Accept all Reject all |
  Comments; a bar above the tabs names the change at the cursor.
- Fixed on the way: `comparable` ignored neither `cite` nor `change` — a file with citations could have opened as
  "formatting it cannot show exactly" (copy-only) in the app.
- Checked: unit tests (`TrackChangesTest`: read, write the same, all/one accept/reject, carry); a LibreOffice-recorded
  file (insert, delete, bold, joined paragraphs, new paragraph) — every change listed, accept/reject all right,
  rewritten file seen by LibreOffice with the same changes; fixture 05 no longer notes tracked changes. Tablet: the
  test file showed the markup on the pages and in the editor, Next ×2, Reject, Reject, Accept all, Save over the
  file; LibreOffice read the saved file right with no changes left. The test file was mine and was removed after.
  A crash found there (Accept/Reject read the changes after changing them) was fixed.
- Not yet: recording my own changes (typing as insertions, deleting as deletions, the author's name); ODT's tracked
  changes (still copy-only); comments reply/resolve (the other half of the Review item); Word itself not checked.

---

# 30 September, afternoon — Design › APA 7 paper

State: `writer-library`, all committed; all unit tests pass; the build is on the **tablet** (971f6b37), not the phone.

- The user chose (of: APA paper, track changes, spelling, footnotes): **APA 7 paper in one tap**. Next time, the list
  offered then: Review (track changes — a Word file with tracked changes is copy-only today; spelling), footnotes,
  the icon pass.
- `ApaPaper.kt`: `file()` — Normal (and an ODT's Standard + Text body), Title, Heading 1–5 by `StyleEdit` (TNR 12,
  automatic colour, double, 0 before/after, first line 0.5 in; L1 centred bold, L2 left bold, L3 left bold italic, L4
  indented bold, L5 indented bold italic), the page number top right (`HeaderFooterEdit`, no running head), margins of
  an inch (`PageSetup`, after the header so an ODT's header band is allowed for). `clean()` — paragraph spacing/indents
  and justification, letters' fonts and sizes set by hand, taken off (tables and the reference list kept). `titlePage()`
  — three blank lines, the title (Title style, bold centred), a blank line, name, department & university, course,
  instructor, date; then the title again over the text on the next page. `ApaPaperDialog`. The reference list's
  heading now starts a page (APA).
- Checked: unit test both kinds (margins, page number, the styles' looks, clean); LibreOffice renders both; tablet: a
  new document with text → APA 7 paper with a title page: page 1 as APA's, page 2 numbered, title, indented double text.
- Not yet: the abstract page; run-in Level 4–5 headings (as paragraphs of their own now); the professional paper.

---

# 30 September, afternoon — References: citations and the reference list (APA 7); Times New Roman 12

State: `writer-library`, all committed; all unit tests pass; the build is on the **tablet** (971f6b37), not the phone.

- **The user (2026-09-30):** like Word — a form: choose the kind of source, fill what you know, the style stitches it
  together; **APA 7 only for now**; **Times New Roman 12 the default**. LibreOffice's bibliography database confused them.
- **References tab**: Table of contents | Insert citation · Sources · References list (Update references once there)
  · Update citations · "APA 7".
- `Citations.kt` (from `parked/`): `DocSource` + `Apa` (as parked), `WordSources` (customXml/itemN.xml b:Sources, +
  itemProps + rels + content type; unknown children kept in `extra`, Word's other source types in `wordType`),
  `Citations` (CITATION instruction parse/make, APA text, the list found by its entry style Bibliography /
  Bibliography_20_1 after a "References" line, `update`, `insertList`, ODT mark attributes). `CitationsUi.kt`: the
  source form (fields per kind; not closed by a stray tap), the citation chooser (page for Word files; one source per
  citation in ODT), the sources list.
- Letters carry `DocRun.cite`; carried through editing like comments (`carryFonts`); `OfficeFormats.withCite`.
  **Word**: CITATION fields read (no longer "fields" → copy-only; a citation's content control too) and written
  (begin/instr/separate/result/end); other fields still make the file copy-only. **ODT**: `text:bibliography-mark`
  holding the source; LibreOffice shows a mark as its identifier, so an ODT's tags are the citations themselves
  ("Smith & Jones, 2019", renamed on Update) and the document's brackets set to ( ) (`bibliography-configuration`).
- New documents: **Times New Roman 12** (both blanks; `basePt` 12).
- Fixed: a draft restored without its sources.
- Checked: unit test both kinds (sources, citations with page, list, reopen not copy-only, update after a year
  change, written again the same); LibreOffice renders both (citations and list alike); tablet: new source form,
  citation with and without page, list after the paragraph, restart, Sources › Edit year → citation and entry follow.
  Not checked: Word itself (no Word here).
- Not yet: other styles; more source kinds; tapping a citation to edit it; the editor shows citations as plain text.

---

# 30 September, afternoon — Insert › Drop cap

State: `writer-library`, all committed; all unit tests pass; the build is on the **tablet** (971f6b37), not the phone.

- The user asked (OnlyOffice's Αρχίγραμμα screenshot): **Insert › Drop cap** (Word's Text group, before Date):
  None · Dropped · In margin (Word files only — ODF has none) · Drop cap options… (`DropCapDialog`: position, lines
  to drop 2–10, distance from text in cm/in). The paragraph at the cursor.
- Model: `DropCap` (lines, margin, distance, length) read from the paragraph's `DocKeep` — Word: part "dropCap" =
  "length|<w:framePr …/>"; ODT: the "child" `<style:drop-cap>` that paragraph styles already carry. `withDropCap`
  sets/takes it; a new paragraph (Enter) does not inherit it (`carryKeep`).
- **Word files**: the drop cap is a framed paragraph of its own (framePr dropCap) holding the letters. Read: joined
  into the paragraph after it (`joinDropCaps`, letters at the text's size). Written: split out again with Word's
  pieces (keepNext, framePr, exact line, big sz, lowered position). **Fixed on the way:** before this the frame was
  dropped on reading — saving a Word file with a drop cap turned it into a paragraph with one huge letter.
- **Pages** (`OfficePages.paragraph`, body only): the letters taken out of the first line, sized so their capitals run
  from the first line's cap top to the last dropped line's baseline, drawn as a float (`DropDrawable`); the first
  lines indented by their width + distance (`DropMargin`, a LeadingMarginSpan2); in the margin, drawn left of it.
- Checked: unit test both kinds (written, read back as one paragraph, again the same, taken off, not inherited);
  LibreOffice renders both as drop caps; tablet: Dropped (3 lines), then In margin / 2 lines / 0.2 cm, survives a
  restart.
- Font and colour of the letter (asked 2026-09-30, same window): `withLettersLook` restyles the first letters (Word rFonts / ODF fo:font-family; colour); unit-tested, seen on the tablet (red Times O).
- Not yet: the editor shows the paragraph plain (the pages show the drop cap); drop caps in
  table cells/headers are left as text; Word itself not checked (its own XML form was followed).

---

# 30 September, midday — the Design tab

State: `writer-library`, all committed; all unit tests pass; the build is on the **tablet** (971f6b37), not the phone.

- **Design** (between Draw and Layout, Word's order inside): Paragraph spacing | Watermark, Page colour, Page borders.
- **Paragraph spacing**: Word's six sets as the Normal style's spacing (`editStyle`), headings keep their own.
- **Page colour** (`PageDesign`): Word's `w:background` + settings' `displayBackgroundShape` (a settings part made when
  the file has none — Writer's new documents); ODT page layouts' `fo:background-color` (+ draw:fill). Read into
  `DocPage.color`; drawn on the pages, the PDF, and as the editor's page.
- **Page borders**: a box — Word's `pgBorders` offsetFrom page, 24 pt in, every section; ODT `fo:border` at the
  margins, text 4 pt inside (`fo:padding`; the reader counts it). `DocPage.border` = `PageBorder` (line + insets).
- **Watermark** (`Watermark.kt`): Word's own WordArt VML (PowerPlusWaterMarkObject) in every header the sections use, a
  header made for a section without; ODT — copied from LibreOffice's own Format › Watermark file (made with UNO,
  2026-09-30): the fontwork custom-shape in every page style's header, graphic + text styles. Read into
  `DocSection.watermark`; the shape is left out of the header's text; the pages draw the word diagonally, grey, half
  see-through. Checked: unit tests (set, replace, remove, both kinds); LibreOffice renders both alike; tablet: DRAFT on
  a coloured, boxed, double-spaced page; it survives a restart (draft).
- Not yet: a picture watermark; the editor does not show the watermark (the pages do); border style/colour/sides;
  Word itself not checked (no Word here) — the XML is Word's own form.
- Next: References (citations — `parked/Citations.kt`), then Review, then the icon pass for the other tabs.

---

# 30 September, late morning — Draw: any page, drawings drawn on again; a blank page's break kept

State: `writer-library`, all committed; all unit tests pass; the build is on the **tablet** (971f6b37), not the phone
(not connected).

- **Draw on page: ‹ Page n of N ›** — opens on the cursor's page; another page while nothing is drawn yet. The drawing
  is anchored in the first paragraph of text that starts on its page (`OfficePages.layout(pageIndexOf=)`: each
  paragraph's page by index); pages no paragraph starts on (inside a long one) are left out. The cursor stays put.
- **Edit drawing** (Picture tab, for drawings made in Writer): the strokes ride inside the PNG (a `tetra-ink` tEXt
  chunk: aspect | colour,mm,highlighter:x y…, in the picture's widths — `InkPicture.withStrokes/strokesOf`), so it works
  after saving and reopening too (unit test: through .docx and .odt). A drawing on a page opens on its page (the page
  laid out without it), else in its box; "Done" replaces it in place (same key, anchor and wrap). Turn/Flip/Crop
  re-encode the PNG and drop the strokes (it becomes a plain picture).
- **Fixed (older than today):** after Blank page and typing on the page after it, any Writer change (a picture, a
  style…) moved the page break off that text — the page after the blank page merged into it. `pairParagraphs` matched
  the two empty paragraphs crosswise; empty paragraphs are no longer matched as unchanged, they pair in order.
- Checked on the tablet (new unsaved documents, discarded): 3 pages, drawn on page 1 from page 3 (3 pages kept);
  Edit drawing: the circle back where it was, a highlight added, one drawing on the pages in the same place.
  Not checked on the device: reopening a saved file and editing its drawing (the unit test covers the file side).
- **Move** (a tool beside Eraser): drags the whole drawing; with Edit drawing that is how one is moved on its page.
  The pens' colours and widths are remembered between drawings (prefs "writer"/"pens"). Checked on the tablet.
- Not yet: the phone; undo of a move inside the drawing (Undo takes the last stroke); dragging a shape/picture from a
  file (Move is for Writer's drawings).

---

# 30 September, morning — draw on the page; icons on Insert and Borders

State: `writer-library`, all committed; all unit tests pass; the build is on the **tablet** (971f6b37), not the phone.

- **Draw › Draw on page** (the user: draw on the document, the page underneath to see the size): `DrawDialog(page=)`
  shows the page the cursor's paragraph starts on, as the pages lay it out, across the whole width; one finger or the
  pen draws, two fingers zoom (1–6×) and move it. Insert cuts the PNG to what was drawn (`InkPicture.cut`) and puts it
  **in front of the text** at that place on the page: `DocImage.onPage` (pt from the page's corner) → docx
  `wp:anchor` positionH/V relativeFrom="page" (`PictureXml.docxOnPage`), ODT a char-anchored frame with svg:x/y and the
  style `Tetra_20_wrap_20_InFront_20_page` (from-left/from-top, page-relative), read back by `placeOf`/`wrapOf`.
- **Drawings go on top of the text by default** (the user): the drawing box too is now in front of the text (at its
  paragraph's top left), not a paragraph of its own. Both are anchored at the start of the cursor's paragraph
  (`putDrawing`), as Word does. Resize/wrap/delete: tap it → Picture tab.
- **Editor**: a picture in front of / behind the text shows as a thumbnail a line tall (it moves no text; the pages
  show it in place) — was full size inline, splitting words. Applies to files' floating pictures too.
- **Fixed:** a new picture restored from a draft lost its wrapping (came back in line) — now keeps wrap and place.
- New pictures that are floating but not on a page (raw == null) are now drawn floating at their paragraph on the
  pages (as they are written), not in line.
- **Icons** (Material Symbols, fetched as SVG from google/material-design-icons → vector drawables): every Insert
  button, Insert regrouped in Word's order (Pages, Table | Picture, Shapes, Link, Comment | Header & footer, Page
  numbers | Date, Line break, Symbol); Borders button + each item of its menu; Draw's two buttons.
- Checked on the tablet (a new unsaved document, discarded after): circle + arrow drawn over a word land on the same
  word on the pages, also after the app restarts (draft); drawing box zigzag in front of the text. Unit test both
  kinds; LibreOffice renders both at 200 × 300 pt as written.
- Not yet: choosing another page to draw on (only the cursor's); editing a drawing again; dragging floats to move them;
  the phone.
- **Insert vs Word, still missing** (agreed "later"): text box, WordArt, drop cap, equation, chart, SmartArt, icons,
  bookmark/cross-reference, cover page, signature line.

---

# 29 September, afternoon and evening — Home and Insert nearly done; the sync "(2)" bug fixed; Beta 3 desktop

State: `writer-library`, all committed; all unit tests pass (Android and the desktop's 86). Today's debug build is on
the **phone** (208c8192) and the **tablet** (971f6b37). The computer runs the installed **desktop 0.3.0-beta.3**
(`Drive/bidirectional-sync`, e42b506; the user installed the .deb). The phone-side sync fix is on both Android
branches (`writer-library` and `bidirectional-sync`).

**Comments (evening, done):** `DocRun.comments` (ids) + `OfficeDoc.comments` (`DocComment`: author, date, text, the
file's own XML kept raw so Word's replies/resolved stay). Read: docx commentRangeStart/End + comments.xml; ODT
office:annotation … annotation-end (`CommentedRuns` tags runs while a range is open). Written: ranges around the runs,
one stretch per comment, ending across paragraphs by look-ahead (`nextFirst`); comments.xml rewritten (commentsExtended/
Ids entries of deleted comments removed); ODT annotations with dc: declared. Through editing they ride `carryFonts`
(letters typed inside join, all its letters deleted = gone). A comment on a point (no range) still makes the file
copy-only. UI: tinted words; tablet — a pane of cards beside the page at their text's height (page shifted/zoomed to
fit, View › Comments hides it); phone — View › Comments opens a right sidebar, a bar above the ribbon shows the comment
at the cursor; Insert › Comment adds (name remembered in prefs "writer"/"author"). Checked: unit test both kinds +
05_Review_and_Forms round trip; LibreOffice reads both comments (author/date/initials) from the tablet's saved copy;
tablet pane and emulator sidebar seen. Not yet: reply/resolve, comments on the pages view, the phone itself.

**Layout tab (night):** Margins / Portrait / Landscape / Size → `PageSetupDialog` (PageSetup.kt): Word presets
(Normal = APA's inch), custom in the chosen unit, A4/Letter/Legal/A5/A3. `PageSetup.docx` rewrites pgSz+pgMar in every
sectPr (header/footer/gutter kept, Word's order); `.odt` every page layout (top/bottom less the header band, so the
text lands where asked). Page break / Blank page there too. A tablet now always fits the page on screen (shownZoom),
so a landscape page stays a page. Checked: unit test both kinds + a two-section docx; LibreOffice: Letter landscape
text at 1.5 in / 1 in (both kinds); tablet: 01 copy → APA + landscape, saved, LibreOffice A4 landscape at 1 in.
Columns: `DocPage.columns/columnGap` (read from w:cols / style:columns, written by PageSetup), `DocPage.column(k)`; the
layout flows `page` = the current column inside `frame` (the sheet); `breakPage()` for page breaks and sections.
Checked: unit test; tablet (2 columns on the landscape copy) and LibreOffice (w:cols num=2, 3 pages alike).

**References › Table of contents (night):** Contents.kt — the headings 1–3 with their page, in TOCHeading/TOC1–3 (docx,
added from DOCX_BUILTIN) or Contents_20_Heading/1–3 (ODT_BUILTIN), a right tab with dot leader at the text width;
page numbers from `OfficePages.layout(…, pageOf)` (laid out once with the table in place); "Update table" when one is
there (found by its styles). Checked: unit test both kinds; LibreOffice renders both; tablet (TOC test.docx, copy of
02): pages 1, 1, 2 — the same as LibreOffice's layout of the saved file. Not a Word field: Word's "Update field"
does not know it (Tetra's Update does).

**Order (the user, night):** the tabs go in Word's order — Home, Insert, **Draw**, Design, Layout, References.
Layout and the table of contents were done out of order; **citations/bibliography are parked** in
`parked/Citations.kt` (APA 7 formatting + a source model, not wired, not built) until References comes round.

**Drawings in Word files (night, the user's keyboard handout — Το Ελληνικό πληκτρολόγιο.docx):** pen strokes (Word ink,
w14:contentPart, drawn from their fallback PNG) and shapes were placed a keyboard too low: a top-and-bottom picture
pushed the *next paragraph's start*, which its floating objects are placed from. Now such a picture is a band that
only lines crossing it are pushed below (`bands`/`clear` in OfficePages.layout); floats are placed from where their
paragraph naturally starts. Ink uses its fallback's box (VML margin/width/height: the pen's width around the line —
a flat underline was 0 tall). Plain shapes (ellipse, rect, roundRect, line) are drawn with their fill (and alpha) and
outline (`ShapePicture`) — the yellow circles around ! @ # were grey boxes. Checked on the tablet against Word's own
PDF of it: the keyboard's top 124.6 pt (Word 124.5), strokes on < > ? at 250.6–251.7 pt (Word 250.4–255.4).

**Insert › Shapes (night):** `ShapePicture` (OfficePages.kt) = kind + fill + outline, `path()` draws each kind (used by
the pages, the editor's overlay, the gallery); `PictureXml.newShape` writes docx wps:wsp inline or ODT
draw:custom-shape whose style name `Tetra_20_shape_<fill ARGB>_<line>_<width×100>_<wrap>` carries its look
(`odtShapeStyle` makes the automatic style). `PictureXml.withWrap` now rewrites the XML when wrapping changes, so the
pages show it at once (was only on save). A shape in a line: its outline scaled to the line's twentieths of a point.
Checked: unit test both kinds (LibreOffice renders both alike); tablet: a yellow-outline circle added, set in front of
text, saved — LibreOffice's page matches Tetra's.

**Draw tab (night):** Draw › Drawing box → `DrawDialog` (WriterDraw.kt): pen, pen 2, highlighter (colour + mm each),
stroke eraser, undo; `InkPicture.png` renders the strokes (widths in mm of the page) to a see-through PNG placed as a
picture in a paragraph of its own. Checked on the tablet (strokes, highlighter) and in LibreOffice (the PNG shows).
Not yet: ink over the text itself (needs floating objects), editing a drawing again.

**Next:** Design tab (watermark, page colour, page borders), then back to References (citations — parked), Review,
then the ribbon-icon pass. Known from the handout: shapes/pictures in front of text cannot be moved by dragging yet. in the agreed order (OFFICE_FEATURES.md top): Layout, References (TOC, citations, APA
bibliography), Draw (a drawing box first), Design. **When the tabs are done: an icon for every ribbon button**
(the user asked — it helps remember placement).

The user's Test-Words folder has many test copies (HF test (2)/(3)/(4).odt, Sync edit test*.txt, Break/Border/Undo
test…): **leave them be** (the user, 29 Sep: "not the problem").

**Sync: an edit replaces, only a real conflict keeps both** (SYNC_PLAN.md 6ak, both repos). Found today: a Writer save
on the phone reached the computer as "HF test (2).odt", then "(3)", "(4)" on every sync, and the tablet got only part
of the folder. Now: changed on one side since both last agreed → it replaces the other side's copy, the old version
kept in `Files history/` in each app's own data; changed on both → both kept, once; a copy a device keeps under another
name still reaches it; the computer remembers per device (`self:<device>`), including what it offered and received.
Writer stamps the save time on the file. Checked both ways with the tablet and the phone (md5s compared; history shows
"updated"); a second sync moves nothing. How to run the desktop from source against the real library: memory
`desktop-test-run`.

- **Phone check** (HF test.odt, a copy of Πάτερ Ημών Άσκηση.odt in Test-Words): opens clean in the editor; Insert ›
  Header & footer on phone size works; header right + page number bottom-centre shown on the pages at once; saved;
  LibreOffice shows both on all 3 pages; reopened, the dialog reads them back. That is also the ODT-through-the-UI
  check the noon entry left open.
- **UI is English only** (the user: languages come later with string files): the "Σελίδα 1 από 3" choice is gone.
  A file that already says "Σελίδα 1 από 3" keeps its own words (read, not offered).
- **Undo for Writer's own changes** (style, spacing/indents, picture, table, header/footer, a style's definition, line
  break, super/subscript on a selection): each keeps the whole document before it (`WriterSnapshot`, 50 kept) —
  the editor's own undo knows only typing and forgot it whenever the document was shown again. Typing between two
  changes is the editor's to undo, then one step back as a whole. Checked on the phone (Undo test.docx, a copy of 01):
  typed "xy" + Heading 1 → undo, undo → as opened; redo, redo; a header and line spacing 2 undone (pages agree).
  Known: typing undone just before one of these changes is not redone after it.
- **Clear formatting** (Home, the eraser after Highlight): the chosen letters — or with none chosen, the paragraph
  at the cursor — lose their own bold/italic/underline/strike/size/script/colour/highlight; the style's look, links
  and pictures stay (`WriterBridge.clearFormatting`, unit-tested). Checked on the tablet: a red/highlighted line
  cleared and undone; a heading keeps its style. Known: a letter's own font (DocRun.font, carried by position) stays.
- **Fixed (found writing the test):** the editor shows a picture/table as U+FFFD, its block holds U+FFFC — so
  `paragraphsAt` (styles, spacing, the ruler, the style lit on the ribbon) found nothing from the first picture or
  table on. `blockSpans` now matches them.
- **Top bar hides on a phone** while scrolling down (pages and editor), back on scrolling up (nested scroll on the
  screen; under 700 dp wide only). Checked on the emulator (phone size). Seen on the tablet, not from this: the
  floating keyboard strip sometimes pans the whole screen up for a moment (top bar off screen), then it settles.
- **Page break / Blank page** (Insert): the paragraph is split at the cursor (its style kept on both halves) and the
  part after it gets "page break before" (`OfficeFormats.withPageBreak`, both kinds); Blank page = an empty Normal
  paragraph between two breaks. The editor draws a dashed "Page break" line above such paragraphs (also the file's
  own breaks). Checked on the tablet (copies of 02 in Test-Words): pages view, saved → LibreOffice breaks at the same
  word (5 → 6 pages), XML `<w:pPr><w:pageBreakBefore/></w:pPr>`; blank page inside a heading; undone in one step.
- **Paragraph borders**: read (docx pBdr, ODT fo:border*/padding/join-border → `ParaLook.box`), drawn on the pages
  (`boxLines`: alike paragraphs side by side are one box, the top line where it starts, the bottom where it ends; a
  line alone spans the text), outlined in the editor. Home › Borders (after the line spacings): Bottom/Top/Left/Right
  (on/off), No border, All, Outside, Inside horizontal, Horizontal line. Written by `OfficeFormats.withBorders`
  (0.5pt single, auto colour). Checked: unit test both kinds; tablet (Border test.docx) pages vs LibreOffice alike;
  JVM-written ODT/DOCX in LibreOffice (colour, width). Not yet: style/colour choice; "No border" does not override a
  style's own borders; headers/footers/cells do not draw paragraph borders.
- **Change case** (Home, Aa beside the eraser): Sentence case / lowercase / UPPERCASE / Capitalise Each Word on the
  chosen letters or the word at the cursor; letter for letter, so each keeps its look; Greek UPPERCASE drops the tonos
  (keeps the dialytika), lowercase ends words in ς (unit-tested). Checked on the tablet; undoable.
- **Symbol and Date** (Insert): a grid of symbols a phone keyboard lacks; today as 29/09/2026, 2026-09-29,
  29 Σεπτεμβρίου 2026, Τρίτη 29 Σεπτεμβρίου 2026, 29 September 2026…, time — the long forms in the phone's language and
  in English (document text, not UI). Typed at the cursor (the editor's undo). Checked on the tablet.
- **Plan agreed with the user** (29 Sep): finish Home and Insert first. Home: border style/colour behind ⋯ (later); no font name on the ribbon (space).
  Insert: comment. Later: text box/shape/WordArt, equations, SmartArt, charts,
  forms (content controls). Then a References tab: table of contents, citations, APA bibliography.

---

# 29 September, noon — header, footer and page numbers; a false "changed"; closing here

State: `writer-library`, all committed; all unit tests pass; the build is on the **tablet** (971f6b37) and emulator,
**not the phone** (never connected today — first thing next time: install there and walk through today's features).

- **01_Text_and_Paragraphs opened as changed**: the editor re-lays a list item's indent after drawing it; the change
  detector (draft/dirty snapshotFlow) now leaves paragraph indents out. Checked on the tablet: opens clean, no draft.
- **Header, footer, page numbers** (Insert › Header & footer / Page numbers → `HeaderFooterDialog`): one line of text
  at top and bottom, left/centre/right; page number top or bottom, left/centre/right, as `#`, `Page # of N`,
  `Σελίδα # από N`, `# / N`; different first page. `HeaderFooter.of(section)` prefills from the file (zones read from
  tab stops). Written by `HeaderFooterEdit.docx` (new header/footer parts + rels + content types, body sectPr refs,
  titlePg) / `.odt` (Standard master page's header/footer, TetraHF automatic paragraph styles with tab stops, page
  layout header/footer-style). Applied to `original` at once, so the pages show it; the draft is now the whole
  package the file is written from (styles and headers survive a restart).
- ODT headers/footers are now **read** for the pages too (`OfficeFormats.odtMaster`; room below the top margin as
  LibreOffice gives it).
- Checked: unit test both formats (set → read back → saved with new text → still there); Word on the tablet
  (02 copy: number top right, footer centred) and LibreOffice; ODT: Πάτερ Ημών Άσκηση.odt in LibreOffice (JVM-made).
  **Not yet**: the ODT path through the tablet's UI.
- Known: a header with several lines/pictures is replaced by one line (the page says so first); only the main page
  style of an ODT (Standard) gets it; odd/even headers not offered; old header parts stay unused in a .docx.
- Next: phone check; ODT header through the UI on the tablet; then (user's list) undo for style/spacing changes,
  comments, table of contents, APA bibliography.

---

# 29 September, day — ODT tables finished, the desktop-like view with rulers, styles

State: `writer-library`; all unit tests pass; the build is on the **tablet** (971f6b37) and the emulator, not the phone.
The user's ask (mid-morning): finish pictures/tables, then viewing (a proper page, rulers as on desktop) and styles
(Normal, Title, H1, H2… on the ribbon, the rest in a modal page), cm or inches (APA uses inches).

- **ODT tables**: rows/columns/merge/split now work in .odt too (`TableEdit.odtModel`: spans + covered cells, one
  table-column each); Insert › Table in .odt (Writer's own `Tetra_20_table/cell` automatic styles). Looks, shading and
  the ⋯ page are still Word-only. Checked: Πάτερ Ημών Άσκηση.odt edited → LibreOffice.
- **View**: `PagesView` on a tablet: page at the editor's text size, centred, `HorizontalRuler` (page in view) and a
  `VerticalRuler` beside each page (WriterRuler.kt). The editor on a tablet is the page (its width and margins),
  `IndentRuler` above: first-line / left (hanging) / right marks, dragged, snapped to ticks → `setSpacing`. Indents are
  shown in the editor (RichTextBlock.textIndent). A phone keeps reflow and no rulers. `WriterUnits` (View tab): cm/in.
- **Styles**: the editor draws each paragraph's style (size, bold, italic, colour, alignment) with tagged marks that
  never come back as the letters' own (Writer.kt `STYLE_*`; heading looks of the library emptied by reflection,
  `documentHeadings`). Ribbon: Normal · Title · H1–H3 · Styles… (all styles in their look; Change… opens a style's
  page: font, size, bold/italic, colour, alignment, line spacing, before/after, indents) → `StyleEdit.docx/odt` edits
  styles.xml, kept in `original` (and the draft). Title/Subtitle/Quote added to a document without them.
  A style's alignment now counts on the pages too (`ParaLook.align`).
- Checked: the whole Έγγραφα corpus + Word suite round-trip through the editor with styles drawn (unit test); on the
  tablet: Quote/H2 applied, Heading 1 changed (Times, 20pt, blue, centre) → XML and LibreOffice agree.
- **Known**: applying a style or changing one clears undo (the editor is re-shown); explicit "left" in a centred style
  cannot be expressed; the editor still uses its own font (not the style's); ODT table looks/shading.
- Next on the list: header/footer/page numbers, then comments, table of contents.

---

# 29 September, morning — tablet connected; line spacing and the Paragraph page

State: `writer-library`; all unit tests pass; the build is on the **tablet** (971f6b37) and the emulator, not the phone.

- **Tablet check** (02_Tables): pages fine; in the editor a table stayed page-wide while the text used the whole
  screen — now its columns widen with the screen (`tableRows(grow=)`, only the editor's `tableBlock`); tapping a cell and
  typing works on the tablet.
- **Line spacing on Home** (last ribbon page): 1 · 1.15 · 1.5 · 2, then **More…** = the Paragraph page (line spacing,
  space before/after in pt, left indent and first line indented/hanging in cm; the paragraph's own value, e.g. Word's
  1.08, shown as an extra chip). Written in the file's own words (`OfficeFormats.withSpacing`: docx w:spacing/w:ind,
  ODT fo:line-height/margins/text-indent), kept on `meta.paras` (the keep source), so pages and file show it; the
  editor does not (its own lines). Which paragraphs: `WriterBridge.paragraphsAt` (the editor's text = its blocks with a
  space between and "• "/"1. " before list items; tested with a real RichTextState). Checked on the tablet (a copy of
  01): pages, saved XML, LibreOffice render agree; on the emulator (phone size, a new unsaved document).
- New documents now lay out with the blank file's own styles (not `Plain`), so what is set shows before saving.
- **Fixed:** a restored draft lost its paragraphs' formatting (it was taken from the file) — now from the draft.
- **Known:** undo does not undo a spacing change; 01_Text_and_Paragraphs opens as "changed" (dot, save prompt) without
  any edit — happened before today's work too, not yet looked into.
- Next on the user's list: styles pages (long-press a style → its page), then header/footer/page numbers.

---

# 28 September, night — notes fixes, the table work reviewed and finished, ODT tables, picture tools

State: `writer-library`; all unit tests pass (TableEditTest new); the build is on the emulator, **not on the phone or
tablet** (neither was connected). Desktop notes changes are on Drive `bidirectional-sync`.

**Notes (both apps, asked tonight):** tags are called labels everywhere; a label no note outside the Trash carries is
gone (drawer, label sheet, chosen label); search and a chosen label look through every note but the Trash from any
page (Trash page's "Empty"/"Delete for good" only on the Trash page itself); desktop cards keep a header row for the
select circle (no re-flow on hover). Android: Settings › Text editor › "Open .txt files here" (on) — Files opens .txt
in the Text editor.

**ChatGPT's table work, reviewed** (commits 7613a78, 7ba893d): usable start, several real bugs, all fixed here — the
cell box opened over the wrong cell (a centred Box plus an offset); editing a cell flattened every run to the first
one's look; merge/add column counted cells, not Word's grid, and wrote tcPr children out of schema order (Word may call
that damaged); ribbon actions after typing in a cell lost the typing (a table's key was its content hash — now its
place, `t<index>`) and the tap itself (buttons took focus → keyboard closed → ribbon moved: RibbonButton and Tool are
now `canFocus = false`).

**Tables now** (`TableEdit.kt`: a grid model of blocks, every structural change goes through it; `WriterTable.kt`: UI):
- Editor draws a table at the body's text size, as wide as the screen (columns keep their longest word —
  `fittedTableColumns(least=)`), vector-drawn (`tableLaid`), no gap under it (its letter's line = its height).
- A cell: tap → typed in place (its shading, margins, vertical alignment); Home formats the cell's text while typing
  (selection or whole cell); only what changed is replaced (`withCellText` diff).
- Table tab (3 to a page): Row above/below · Merge (tap first then last cell; the rectangle lights up, grown around
  merged cells) · Column left/right · Split (a merged cell back, or evenly into more) · Delete row/column/table ·
  4 styles · Shading · More… = Table properties with tabs Table/Row/Column/Cell/Borders (the user's ⋯ rule, see
  memory). Units: cm.
- Pages: vertical text (btLr/tbRl, aligned across as Word), a nested table's edge kept (cell lines under content),
  Word table-style conditional formatting (header/first col/bands/last row/corners from tblLook; a cell's own shd —
  even fill=auto — wins).
- ODT tables kept (no longer copy-only): attributes/columns/rows/cells raw (`ODT_HEADER`, `ODT_COVERED` marks),
  drawn from their column/row/cell styles. Only text editing for now: structure tools are Word-only (they write w: XML).
  Pater Imon Askisi written back: LibreOffice identical; a cell edited on the emulator and saved: identical but the edit.

**Pictures:** Turn left/right, Flip (a new picture, like Crop), Replace. No grey block behind a selected object.

**Checked:** LibreOffice renders of 02_Tables after merge/add column/edit (XML inspected: grid widths, tcW, order);
a hand-made conditional-style table vs LibreOffice. **Known:** Tetra lacks DejaVu Sans (02_Tables' font), so its pages
fit more per line than this machine's LibreOffice; floating pictures still sit in line in the editor (the rich-text
editor cannot flow text around them); floating pictures inside cells are left out on the pages; ODT structure editing.

---

# 28 September, late evening — Word table editing complete

State: branch `writer-library`; 133 unit tests pass; debug build installed on the phone (`208c8192`) and emulator.

- A table is rendered in the editor with the same `OfficePages` geometry as page view. Tapping a cell now consumes
  the touch before the rich-text layer, places an editor directly over that cell, focuses it, and opens the keyboard.
  The cursor starts at the end; leaving the cell, saving, or opening Layout commits it while preserving the first
  paragraph/run look. Cells containing nested tables or pictures are kept but not flattened.
- Insert › Table opens the requested 5×5 picker. Tapping or dragging selects the rectangle and inserts a bordered
  Word table of that size.
- Table › Layout is structural only: its drag grid selects a rectangle starting at the tapped cell; it can merge
  simple unmerged cells, add a row/column, or remove the last row/column. Existing complex merges are rejected rather
  than guessed at. The Table ribbon also exposes the common row/column actions and Delete table.
- Live emulator acceptance: edited a real cell, inserted a dragged 5×5, merged 2×2, added a row and a column, saved,
  pulled the DOCX, verified its XML, and opened/converted it with LibreOffice (5 A4 pages). The original nested table
  and vertical merges remained. Unit coverage writes/reads cell edits, new rows/columns, and a rectangular merge.
- Nested tables now fit to the parent cell instead of keeping an oversized explicit grid and drawing into the next
  cell. Checked in both page view and edit view with `02_Tables.docx`; the inner 2×2 ends at the Description border.

Next table work: ODT table reader/writer. Word rotated cell text and conditional table-style formatting remain page
rendering gaps, not editor data-loss issues.

---

# 28 September, evening — tables next (user: "make a handoff and try to complete the tables"; the viewer: "nice")

State: everything up to here is committed on `writer-library`; 131 unit tests pass; today's build is on the phone
(208c8192), not the tablet. The page view (OfficePages.kt) matches LibreOffice closely on the user's documents.

**Done tonight (stopped here — weekly limit):**
1. ✅ Word tables kept: `DocPara.table: DocTable` (rows/cells of DocParas, props raw); writer writes them back; no
   longer copy-only. LibreOffice: 610-ΤΕΛ 16/16 pages and Τούρκικα identical; suite 02 tables identical.
2. ✅ Tables on the pages (`tableRows`, `stack`, `DocxStyles.tableSpec`): widths, spans, vertical merges, margins,
   borders (style → table → cell; a row holds its border's width), shading, vAlign, heights, heading rows repeated,
   rows whole. Line height now includes the font's line gap (from the font most of the text is in) — Τούρκικα row for
   row as LibreOffice; DPSYC610 20 pages. Not done: rotated cell text (textDirection), conditional table-style
   formatting (first row/banded), floating pictures inside cells (left out).
3. ⬜ **Next: the editor.** Today a table is one character there (DocImage.table, carried through untouched) drawn as a
   grey box labelled "Picture". To do: draw it (lay the table out with OfficePages into a bitmap in `picturePainter`;
   real size via a measure function WriterBridge can call), and a **Table tab** when it is selected (in the Picture
   tab's place, `WriterTab.Picture` code ~line 924): Edit cells (grid of text fields; a changed cell keeps its first
   paragraph's look/keep), add/delete row, delete table.
4. ⬜ ODT tables (Πάτερ Ημών Άσκηση.odt is copy-only for them): reader/writer for table:table (styles kept via the
   automatic-style scan), page drawing from ODT table/column/cell styles.

Build with all of the above: on the phone (208c8192) and the emulator.

**Original plan for tables (in this order):**
1. Model: `DocPara.table: DocTable` — rows (trPr raw) of cells (tcPr raw) holding ordinary DocParas (nested tables
   too); tblPr/tblGrid raw. Reader builds it (no more flattening + "tables" note); writer writes it back from the
   model (cell paragraphs through the normal paragraph writer) → tables are no longer copy-only. Word first, then ODT.
   Check: the round-trip test + LibreOffice pixel compare (suite 02, 03, 06; the user's Τούρκικα, 610-ΤΕΛ, Πάτερ Ημών).
2. Pages: column widths (tblGrid), gridSpan / vMerge, cell margins, borders (table style → tblBorders → tcBorders),
   shading, row heights, vertical alignment, header rows repeated on each page, rows kept whole across pages.
3. Editor: the table as one object (like a picture: a rendered image of it); tap → Table tab: edit cells (a grid of
   fields), add/delete row and column, delete table.

---

# 28 September — the Picture tab built, checked on the emulator (not on the phone/tablet yet)

Tap a picture → it is selected (blue frame) and a **Picture** tab appears: **Size** (a window: horizontal × vertical px,
a preview with handles on the right side, bottom side and corner — dragging updates the numbers — and a chain toggle
for proportions; user asks 28 Sep), **Crop** (Photos' CornerEditor with a new "Straight cuts (90°)" switch, on by
default; off, free corners straighten as Photos does; the cut picture becomes a new picture, same scale, same wrap),
**Wrap text** (in line · square · top and bottom · behind · in front, written to the file: docx wp:inline ⇄ wp:anchor,
ODT anchor-type + a Tetra graphic style — `PictureXml` in OfficeFormats.kt, tested), **Save picture as** (Pictures/Tetra),
**Delete**. Checked on the emulator + LibreOffice (square wrap shows). Charts, shapes and VML drawings: only Delete/Save.
Known: floating pictures still drawn in line; a cropped picture's old media part stays in the package (unused);
the space after an inline picture can be wider than the picture (the placeholder glyph's width).

Later the same day:
- **Superscript / subscript** kept and shown (`DocRun.script`; docx w:vertAlign, ODT style:text-position), X² / X₂ on
  Home. Shown at 0.7 of the size; a size the file sets vs the document's own are told apart by the factor (0.7 / 0.69,
  `SCRIPT_SIZE*` in Writer.kt). WriterBridgeTest checks the round trip.
- Charts, shapes, text boxes, SmartArt, embedded objects: the grey box says what it is (`PictureXml.kind`).
- **The user's Word App Test Suite** (~/Έγγραφα/Claude/Projects/Defaulty/Word App Test Suite, 6 files) is in the
  "real documents read" test; also on the emulator in Tetra/Documents. All six still save as a copy: tables (02, 03, 06),
  page breaks, sections/columns (03), fields/footnotes/equations (04), comments/tracked changes/content controls (05),
  small caps/caps/double strike/double underline + hidden text (01, "other text effects").
- **Line breaks inside a paragraph now show as new lines**: each line after a break is the editor's own
  `isLineBreak` block, joined back by `fromEditor` (the break takes the look of the text after it; `comparable` ignores a
  break's look). Insert › Line break re-renders at once. Checked on the emulator with file 01 → saved `<w:br/>`.
- **Letter settings Writer does not edit are carried, raw, letter by letter** (as fonts were): docx rPr elements in
  `RPR_CARRIED` (small caps, caps, double strike, spacing, language, shading, borders, szCs/bCs…), a non-single
  underline, and Word 2010 extensions (w14:ligatures); written back in `RPR_ORDER`. ODT: every text attribute not in
  `ODF_WRITTEN`. "Other text effects" no longer forces a copy (hidden text still does). LibreOffice check of all 27
  documents: nothing worse; Απαντήσεις, Μελέτες, Το Ελληνικό πληκτρολόγιο (both), DPSYC's last pages now pixel-identical;
  Το Ελληνικό πληκτρολόγιο.docx now saves in place.
- **Lists numbered by their paragraph style** (docx List Bullet / List Number: numPr in the style, `docxStyleLists`) are
  read as the lists they are and show bullets/numbers; written back with the file's own numId; a list taken off such a
  paragraph is written `numId 0` (else the style numbers it still). Test with suite file 01. Nested level shown at 0
  (List Bullet 2 indents by its style).

- **Drag to move (snap)**: a selected picture drags; let go, it goes into the text between the letters nearest its
  middle. Moving and Insert › Picture no longer use the editor's insertHtml (it scrambled the text after it — spaces in,
  letters lost): a mark (`PICTURE_SPOT`, U+E000) is typed and replaced by the picture in Writer's own document
  (`WriterBridge.putPicture`), then read back in.
- **The picture character is now exactly as wide as the picture** (LetterSpacing; doubled at a line's start, where
  Android drops the half before a letter) — the text after a picture no longer runs under it or leaves a gap.
  Known: a picture followed by a word with no space is one "word" to Android's line breaker (U+FFFD is a letter to it):
  a too-long line breaks inside that word.

**Proper pages — built (28 Sep afternoon), `OfficePages.kt`:** a document opens view only as its pages (Edit at the
top; the eye button in the editor goes back); Export PDF uses the same layout. Read from the file (`DocStyles`:
DocxStyles / OdtStyles, resolved at layout time from the package + each paragraph's raw DocKeep): sections (page size,
margins, headers/footers per type with Word's link-to-previous, title page, even pages, page-number start/format),
spacing before/after (contextual), line spacing (Word spaces the last line too), indents in Word's order
(style → list level → paragraph), negative indents, tab stops (right/centre, dot leaders — laid out by hand), each list
level's numbering (%1.%2., roman, letters; Symbol bullets), heading/style fonts, sizes, colours; floating pictures in
place (page/margin/paragraph; behind/front; top-and-bottom pushes text — **square wrap does not flow text beside
yet**). Laid out in twentieths of a point (`U`) — whole points broke lines/tabs. **Fonts bundled** (assets/fonts, OFL):
Liberation Serif/Sans, Carlito, Caladea = Times/Arial/Calibri/Cambria metrics → DPSYC610 gives 20 pages like LO.
Checked on the emulator against LibreOffice: DPSYC610 (cover, TOC line for line), suite 01/04/06.
Also now kept on save (no longer copy-only): page breaks (as pageBreakBefore; at a paragraph's end → the next one),
section breaks (sectPr carried raw, not handed on by Enter), List Paragraph style (was dropped: lost TOC gaps).
Office home: the 50 newest documents (asked). Today's build is **on the phone** (208c8192), not on the tablet.

Not yet: tables (the biggest copy-only reason left), square-wrap text flow, columns, footnotes, ODT headers/footers,
two pages side by side on a landscape tablet, show page breaks in the editor. Suggested to the user
(not yet decided): tables and page breaks first — they are what makes most real files copy-only.

---

# Next session (28 Sep) — continue the picture work, then proper pages

**Budget (user, 27 Sep):** ~10% of the week left until the reset on 28 Sep at 22:00. Tomorrow: only the quick,
certain part of the Picture tab — the tab itself, Delete, Save picture as…, **Resize in a window: horizontal xx px ·
vertical xx px** (not by dragging — the user, 27 Sep), and the wrapping choices written correctly to .docx/.odt.
**Crop in a window, reusing Photos' crop** (`CornerEditor` in ScanScreens.kt, as PhotoEditor.kt uses it: a
DocumentQuad over the picture; for a document picture a plain rectangle → docx a:srcRect, ODT fo:clip). After the
reset: drag to move (snapping, and free placement with live re-flow), floating pictures shown in place.

The user's choice for tomorrow: **pictures first**. Where they stand: a file's pictures show on the page and are saved
back untouched; Insert › Picture works (checked on the emulator + LibreOffice). Still to do, in order:
1. **Picture tab** (the user's full list, 27 Sep): appears when a picture is selected —
   - **Resize** (drag the corners, and a width in cm), **Crop**, **Save picture as…** (take a picture out of a document
     someone sent, into the phone's photos/Files), **Delete**.
   - **Wrapping**: in line with text (standard) · square / around text · top and bottom (nothing at its sides) ·
     behind text · in front of text.
   - **Moving**: in line — drag it and it lands where it fits in the text (left, centre, right, between lines), not
     exactly where the finger lets go; free placement — drag it exactly where you want, no snapping, the text re-flowing
     around it live as you drag (Word's "fix position on page" with wrapping); in front of / behind text for overlays.
   - Files: docx wp:inline ⇄ wp:anchor (positionH/positionV offsets, wrapSquare / wrapTopAndBottom / wrapNone +
     behindDoc), a:srcRect for crop, wp:extent + a:ext for size; ODT draw:frame svg:x/y, text:anchor-type,
     style:wrap / style:run-through on its graphic style, fo:clip for crop.
2. Floating pictures (docx wp:anchor, ODT anchored frames) are drawn in line with the text for now — show them where
   they are anchored.
3. Charts and shapes are grey boxes — a label ("Chart", "Shape") at least.
4. Check on the **tablet** (971f6b37) and the phone (208c8192): both have today's build except the keyboard-island
   change, which only the tablet has (the phone was not plugged in).

Then, as agreed: **proper pages** — documents open view only as pages, Edit at the top (OFFICE_FEATURES.md top).
Decided today: every feature goes on both tablet and phone (phone layout is Claude's call); the ribbon on a tablet is
one centred line; with a physical keyboard (the user's keyboard case) the ribbon stays a floating island, a flat strip
only on the screen's keyboard.

---

# 27 September, late night — Writer keeps what a file has; pictures on the page UNFINISHED (stop asked here)

Branch `writer-library`. Everything below is committed; 125 unit tests pass. **Not on the phone yet** (never plugged in
today) — install first, then go through OFFICE_FEATURES.md › "To test on the phone" with the user.

**Done since the last entry** (OfficeFormats.kt, Writer.kt, OfficePdf.kt; checked with LibreOffice, see below):
- Writer carries what it does not edit through edits, raw, as the file had it (`DocKeep` per paragraph): spacing,
  indents, tab stops, borders, page break before, the exact paragraph style (ODT: Standard vs Text body matter), the
  paragraph's own text settings, the file's own lists (docx numId / ODT list style, when still of that kind), an empty
  line's height. New paragraphs take the one before (as Enter in Word). `OfficeFormats.pairParagraphs` matches
  paragraphs old→new (LCS on text, gaps paired in order).
- Fonts letter by letter (`DocRun.font`, raw rFonts / ODF font attributes), carried by `carryFonts` (per-character
  LCS inside a paragraph; typed letters take the font before them). User asked: "a font may change even in half a word".
- Sizes set on purpose are kept (even the normal size — inside a heading it matters).
- Pictures, charts, shapes, text boxes, OLE objects: `DocImage`, one OBJECT_MARK (U+FFFC) character in the text, the
  file's own XML kept (`raw`) and written back as is; new pictures (`bytes`) written into word/media or Pictures/ with
  rels/content types/manifest. ODT keeps its own automatic styles that the text still refers to (frames' position and
  wrap). Tests: new picture in both formats; every real document round trip keeps text, DocKeep and pictures.
- Headings: the editor draws them all bold; an unchanged heading keeps the file's exact runs (`keepHeadingRuns`).
- Also today: Versions (File; last 3 per document, Restore), Export PDF (File; A4; pictures drawn), Find and Replace,
  Share, word count, Insert › Line break, ribbon tools in pages with dots, Word blue / Excel green / PowerPoint deep
  orange, Office icon by the title.

**LibreOffice check** (tools: the "real documents read" test writes app/build/office-roundtrip/; convert originals and
those with `soffice --headless --convert-to pdf`, compare pages with PIL): of the user's 21 documents in ~/Έγγραφα,
14 have nothing Writer cannot keep; those come back pixel-identical (a few dozen pixels on Απαντήσεις, ασχαμπ,
Μελέτες, 610-Ασκηση7). Still copy-only: tables (3 files), page breaks inside text, fields/TOC/content controls,
superscript/subscript.

**Pictures on the page — finished after the handoff (same night):** the editor holds a picture as U+FFFD (its own
choice, not our U+FFFC — that was why nothing showed). Writer gives that character the picture's height as its size
(×1.12, so the line makes room) and no colour, and draws the picture itself over it from `onTextLayout` (left of the
character, standing on the line's baseline). Paragraph lineHeight does not work in the editor (tried). The editor-wide
TextStyle lineHeight was removed (it capped every line). Word's older VML drawings (a horizontal rule) take their size
from their style. Checked on the emulator: Το Ελληνικό πληκτρολόγιο.docx shows its pictures; Insert › Picture into a new
document, text typed after it is normal, saved → LibreOffice shows it; a second save does not add it again.
Asked next (user): a **Picture tab** when a picture is selected — size, placement, text wrapping.

**Next, in order (user, 27 Sep night):** PROPER PAGES FIRST — the document opens as pages (Print layout, from OfficePdf's layout), viewing before editing; see OFFICE_FEATURES.md top → then Picture tab → styles/line spacing → tables → header/footer/page numbers →
comments → table of contents (OFFICE_FEATURES.md has the whole list and the phone test list).

---

# 27 September night — Office and Writer built, checked on the emulator; not yet on the phone

**Save rule, option (b)** (the user did not choose; recommended, say so when reviewing): `OfficeDoc.blocking` (content
Writer does not show — images, tables, comments, tracked changes, footnotes, fields, page/line breaks, other styles…)
→ saving makes "name (copy).ext" beside it and Writer switches to the copy; `OfficeDoc.simplified` (spacing and
indents, fonts, tab stops) → a note, Save goes over the file. Also copy-only: any file whose paragraphs, read back from
the editor, differ from the file ("formatting Writer shows differently" — a safety net for bridge bugs).

Built (`Writer.kt`, `OfficeFormats.kt`):
- Home: an **orange Office group** (office_art.png from Assets/Office.png, cropped to the disc; Office · New document ·
  Documents) and the **purple Text editor group**. `Category.Office` orange #F76002 with `ic_office`, new
  `Category.Text` purple; Settings' editor card is "Text editor"; desktop `--cat-office`/`--cat-text` changed too
  (Drive repo, styles.css).
- **Office home**: Word · New document, Excel and PowerPoint "Coming later", the .docx/.odt under /sdcard/Tetra (not
  Trash or hidden folders), newest first.
- **Writer**: a white page, `BasicRichTextEditor`; top bar back · name (• unsaved) · undo · redo · save; the banner
  (orange: copy-only; grey: simplified); the ribbon at the bottom — File (Save, Save a copy, About this file), Home
  (B I U S, size −/+, text colour, highlight, Normal/Heading 1–3, bullets, numbering, indent −/+, alignment), Insert
  (Link; Table and Picture "later"), View (zoom). Floating solid island with the keyboard down, a strip on it when up.
- Safety: saves write a temp file then rename; before overwriting, the old file goes to `files/writer-history` (last 3
  per name); unsaved changes are a draft in `files/writer-drafts` and come back on reopening; leaving asks Save /
  Don't save.

Checked on the emulator (then with LibreOffice on the pulled files): the user's Αξιολόγηση τριμήνου.odt opens with its
numbered list and bold, no banner; an edit + Bold saved over it — LibreOffice shows only that change. Τούρκικα (tables)
→ banner → Save made "(copy)", the original untouched. New document → Heading 1 + bullets → Shopping.docx, clean XML.
Fixed on the way: sizes the editor gives in em (after a heading) were written as tiny sizes; Enter after a heading now
starts a Normal line like Word; a trailing space in ODT paragraphs is kept; empty borders/spacing no longer count.

**Next:**
1. Install on the phone (not plugged in tonight) and go through it with the user.
2. Line breaks inside a paragraph (Shift+Enter) are the commonest copy-only reason in his files — support them.
3. Heading sizes: the editor's H1–H3 are bigger than the document's own heading styles — show the document's.
4. Paragraph spacing: the page has none between paragraphs; Word shows space after each.
5. Then OFFICE_FEATURES.md, one item at a time (the full checklist: Word, Excel, PowerPoint).

---

# 27 September evening — Writer's file formats built; the screen next (after the usage reset)

The user OK'd the `writer-library` build on the phone ("everything is in order"; backup of its data first:
`Drive-Android-backups/2026-09-27-before-writer-library/app-data.tar`). Then asked: carry on, check on the emulator,
review together later; keep this file current (usage was at 80 %).

**Done — `OfficeFormats.kt`** (pure Kotlin, tested on the computer, `OfficeFormatsTest`):
- `OfficeFormats.read(bytes, kind)` → `OfficeDoc` (paragraphs of runs: bold, italic, underline, strike, size, colour,
  highlight, link; headings 1–6, bullet/numbered lists with start numbers, alignment) and `unsupported`: plain-word
  names of what the file has that Writer cannot keep.
- `OfficeFormats.write(doc, kind, original)` writes the body **into the original package** (styles, headers, footers,
  page setup untouched), or into a blank .docx/.odt when there is none. Lists get new numbering definitions; missing
  heading/Hyperlink styles are added.
- Tests: round trip for both formats, a docx with a table (named, other parts kept), the ODT mimetype, and **every
  .docx/.odt under ~/Έγγραφα read and written back with the same text** (29 files, in memory only).

**Open question for the user (ask before building the save rule on it):** the strict rule makes most real files
"save as a copy" — of the 29, only 1 had nothing to report. The usual reasons are *paragraph spacing and indents*,
*fonts*, *borders and shading*; tables, images, page breaks come next. Options: (a) stay strict; (b) treat spacing,
indents and fonts as "simplified" (warn, still allow Save) and keep copy-only for content (images, tables, comments,
tracked changes, footnotes, fields); (c) keep the original's paragraph formatting for paragraphs the user did not touch
(more work, best result). Recommend (b) now, (c) later.

**Next, in order:**
1. `Assets/Office.png` → `res/drawable-nodpi/office_art.png` (crop the disc: its ragged red fringe must go — a circle
   mask a little inside the orange). `Category.Office` → orange (desktop `--cat-office` too); a new `Category.Text`
   keeps purple for the Text editor; two Home groups.
2. Office home screen: New Word · New Excel (later) · New PowerPoint (later); the .docx/.odt found under /sdcard/Tetra.
3. Writer screen (`Writer.kt`): `RichTextState` + `BasicRichTextEditor`; OfficeDoc ↔ `RichTextDocument`
   (`state.toRichTextDocument()` / `setRichTextDocument`; blocks, `RichTextBlockType.ListItem(ordered, indent,
   startNumber)`, marks `Bold/Italic/Underline/Strikethrough/FontSize/TextColor/Highlight/Link(range)`; ranges are
   `start until end`). A white page (document colours must show). Banner + Save-as-copy when `unsupported` is not
   empty; saving writes a temp file then renames (never a half-written original).
4. The ribbon, from the user's Word screenshots (27 Sep): tabs **Home · Insert · Layout · View** (File first), tools
   B I U, highlighter, text colour, bullets, numbers, indent −/+, alignment; with the keyboard up, one strip of the
   most used (B I U highlighter …) on the keyboard, like Word's phone bar. Start from commit `1b8d17e`'s ribbon.
5. Later, from the user's Word screenshots (asked 27 Sep: "add these to the plan"), in rough order:
   - **Top bar** like Word's: back, file/save state, undo, redo, **Find** (search in the document), **Share**, more (⋯).
   - **Home**: font name and size box, B I U, more text effects (A…), highlighter, text colour, bullets, numbering,
     indent −/+, styles (Normal, Heading 1–3…).
   - **Insert**: Table, Pictures, Shapes, Text box (each means Writer must then *keep* that part — today they are
     copy-only), link, page break.
   - **Draw**: draw with the finger — pen, highlighter, stroke eraser (ink over the page).
   - **Layout**: margins, orientation, page size, columns; **Review**: comments, track changes; **View**: page / reading
     view, zoom.
   - **History**: earlier versions of a document with time and who changed it, open or restore one (Tetra keeps a copy
     on each save, like Notes' history of 3).
   - Phone keyboard strip: B · I · U · highlighter · ⋯ · hide keyboard (first screenshot).
6. Emulator check of each step (temporary `"x86_64"` in `splits.abi.include`, reverted before committing).

---

# 27 September — Android 0.3.0 Beta 3 released (the computer stays on Beta 2); on to Office

Beta 3 adds only: a new photo or video starts a sync ~10 s later (`SyncJob` NEW_PHOTO, a MediaStore trigger job, Wi-Fi,
re-armed each run and on app open). Small things left, none blocking:
- `deleted_here` keeps rows for photos that came back to the library on a drive (the /have cleanup only looks at
  `here_now`) — harmless (have() wins), one line: `DELETE FROM deleted_here WHERE sha256 IN (SELECT sha256 FROM media)`.
- 163 imported photos in "Φωτογραφίες από YYYY" folders still dated 21 Aug 2026 (the copy day): the user was asked
  whether to give them the folder's year — no answer.
- 4 photos loose at the top of /mnt/T7/Sync/Phone-Camera were never imported (IMG_20260726_144507/144514,
  IMG_20260804_213828, MVIMG_20260804_124520) — the user can Import them straight to T7.
- A favorite deleted on the phone and emptied from its Trash is gone there; one was put back by hand (MVIMG_20260827).
  A "favorites cannot be deleted" rule was discussed, not asked for.

---

# Next: the Office applet (Writer first) — start here, on branch `writer-library`

**Branch:** `writer-library` (Drive-Android only), made from `bidirectional-sync` after Beta 2. It carries the
toolchain the rich-text library needs, and nothing else yet:
AGP **9.4.1** (built-in Kotlin: the `kotlin-android` plugin is gone), Gradle **9.8.0**, Kotlin **2.4.20**,
compileSdk **37** (`platforms;android-37.0`, installed from sdkmanager's channel 3), Compose BOM **2026.09.00**
(Compose 1.12, **Material 3 1.5.0-alpha22** pulled in by the library), and
`com.mohamedrejeb.richeditor:richeditor-compose:1.2.0` (Apache 2.0). Checked: 117 unit tests pass, and every screen
(Home, Photos, viewer, Files, Notes, Text editor, Settings, Sync, Scanner, Codes) opened on the emulator with no
crash. **Not yet installed on the real phone** — do that first and have the user use it for a while before building
on it. If it misbehaves, the fallback agreed with the user is Android's own EditText with spans, on
`bidirectional-sync`, without the upgrade.

**What the user asked for (27 September), in order:**
1. **Office group on Home, orange**, with his icon `Assets/Office.png` (not yet in res/ — crop and add like
   `text_editor_art.png`). Two groups: Office (orange) and Text editor (purple) stay separate. `Category` in
   MainActivity has Office purple today — Office becomes orange (desktop `--cat-office` too); the text editor keeps
   purple (a new Category entry, e.g. `Text`).
2. **Office home**: at the top **New Word · New Excel · New PowerPoint**; below, the documents found in Tetra's
   Files (.docx/.odt, and later .xlsx/.ods, .pptx/.odp). Excel and PowerPoint can show as coming later.
3. **Writer**: opens and saves **.docx and .odt** (new documents .docx by default). What-you-see editing (bold looks
   bold — no Markdown marks). **A file with parts Writer cannot handle (images, tables, comments, tracked changes)
   opens, says so, and saves only as a copy — the original is never overwritten.**
4. **A ribbon like Word's, at the bottom on the phone**: one line of tabs (File, Home, Insert, Layout/View…) and one
   line of that tab's tools, full width, scrolling sideways when needed; a floating island with the keyboard down,
   a strip on it when up — Tetra's own look (islandColor, driveNavigationSelectedColor pill). Commit `1b8d17e` (reverted
   on the text editor) is a working ribbon to start from.
5. Later: Excel/Calc and PowerPoint/Presentation. Android only — the computer has its own office apps.

Build: `./gradlew testDebugUnitTest assembleDebug` as before (JDK 17). The emulator (`notes_phone`, x86_64) needs a
temporary `"x86_64"` in `splits.abi.include` — revert it before committing.

**Backlog the user added (27 Sep evening), not yet ordered, ask before starting each:**
- Text editor: an option to be the **default app for .txt** ("Open with" → always Tetra).
- Settings: **Reset to defaults** for each category separately (Gallery, Files, Notes, Scanner, Office…).
- Notes: **Search and tag filters show every note** — archived, pinned or on Home — all but the Trash.
- Office: Tetra as the **default app for Word, Excel, PowerPoint** files (.docx/.odt, later .xlsx/.ods, .pptx/.odp).
- **Import from Google Takeout**: notes (Keep), photos and videos, Drive files, Maps locations — a guided import modal.
- A **Windows** app (the desktop is Linux-only today).
- A proper **light theme** on Android (Roadmap F; today dark only, see TetraColors in MainActivity).
- **Map of disability parking spots**, with the route exported to Google Maps.

---

# 27 September afternoon — 0.3.0 Beta 2 released

Both repos merged into `main`; `v0.3.0-beta.2` (APK) and `desktop-v0.3.0-beta.2` (.deb, AppImage). PC runs Beta 2.
Since Beta 1: motion photos (both; Autoplay off by default, crossed-out Motion button), the Android **Text editor**
(TextEditor.kt: tabs + drafts in `office_editor` prefs / `files/editor-drafts`, CodeKeys symbols row, TextFileRules
keeps CRLF/BOM, TextEditorActivity for Open with), category symbols in Settings, Notes' Tag + chip.

**Writer (docx/odt) is on hold** (user: "don't move forward if we can't do it correctly"). Agreed so far: .docx and
.odt, a file with parts Writer can't handle saves only as a copy, a Word-like ribbon at the bottom (tabs line + tools
line; the reverted commit 1b8d17e has a working ribbon to start from), an orange **Office** group (Assets/Office.png)
separate from the purple Text editor group, an Office home listing New Word/Excel/PowerPoint and the documents in
Files. compose-rich-editor needs compileSdk 37 and Kotlin 2.4 — not usable on this toolchain; the plan was Android's
own EditText with spans.

Assets/Screenshot_*.jpg are the user's raw phone shots: they show a Wi-Fi printout (SSID, router MAC) — never commit
them; the README uses blurred copies.

---

# 27 September, past midnight — motion photos built, to test in the morning

Both apps (SYNC_PLAN: the rule is library.pairMotion / MotionRules, the same stem rule as the importer):
- **Show as one** (default): a picture and its seconds of video in the same folder are one photo; the video half
  never shows as an item. **Remove**: PC — import leaves the half out (the old behaviour); phone — Gallery tools
  shows "Move N motion videos to Trash" (Android's Trash, 30 days). The 23 halves in the purgatory stay there
  (user: "don't bring them back, save space").
- **Video inside the file** (Pixel/Samsung/Xiaomi, ~8% of the library on T7): found by `ftyp` after the picture
  (embeddedVideoOffset), served as media://motion/<id> on the PC, cut into cache/motion on the phone. HEVC plays in
  Electron here (checked).
- Viewer: a Motion button at the top right (PC: right end of the island) plays/stops it. **Autoplay motion photos**
  is a Gallery/Settings switch, **off by default**. No badge on thumbnails (asked).
- Notes on a phone: Tags left the island; solid black chips over it with **Tag +**; hidden while typing.

Tested on demo files only (hidden desktop copy, emulator). Not yet on the real devices: install the new .deb
(`desktop/release/`) and the debug APK; the phone and tablet had the build before the Autoplay switch.

---

# 26 September night — 0.3.0 Beta 1 published

Both repos merged into `main`; releases `v0.3.0-beta.1` (APK) and `desktop-v0.3.0-beta.1` (.deb, AppImage), both
pre-releases. READMEs lead with a showcase card from demo content (`Assets/screenshots/`; the demo generators were
throwaway scripts: painted landscapes, a small Files tree, seven notes, shot hidden with `DRIVE_DATA/PHOTOS/FILES`
and on the `notes_phone` emulator with a temporary x86_64 split). The old C++/Qt app is removed from `Drive`
(history: `1b71025`); only `desktop/` is developed there.

Fixed tonight: **Open / Open with failed for every file** since the Tetra rename — the FileProvider still named
`Drive/` (test in DriveFilesTest). The opener is now remembered per file type too (`/type/<ext>` in drive_openers).
Phone and tablet run the latest debug build; the PC runs the beta .deb.

Next is still motion photos (below).

---

# 26 September evening — deleted files came back

Files deleted on the PC came back from the phone: the PC had no "deleted here" memory for Files (photos do). Now
`file_deleted_here` (files.js reconcile) — what the PC held at the last sync and holds nowhere now is never asked for
again, until the same bytes are back. Also: a name already in Trash failed the delete silently (both apps now use
"name (2)"), and the phone listed `.notes`/`.templates` (hidden now). Built: beta .deb (not installed; needs
`sudo dpkg -i`), debug APK (phone not plugged in). The phone still holds Document 📃, LocalDrive-Tag-Check and the
LocalDrive-* folders in its Trash; delete them there once the new builds are in.

---

# Next, first thing (asked at the close of beta-1, 26 September): motion photos, as one or not at all

A motion photo is a picture plus a few seconds of video; an export (Google Takeout) splits it into two files of one
name — MVIMG_….jpg + MVIMG_….MP4, 20230529_201908.heic + 20230529_201908(2).MP4, Pixel's PXL_….MP. Today: PC import
leaves the video half out (`withoutMotionVideos`, importer.js), and the 23 that had come in were removed to the
purgatory. Asked for, **on desktop and mobile**, a setting:
- **Show as one** — the picture in the gallery with a Motion button that plays its seconds (the viewer); the video half
  never a second item. Pairing rule: same folder, same name stem (drop the extension, a trailing `.MP`, `(n)`, `~n`).
- **Remove on import** — as the PC does now; on Android, the same for photos that arrive by sync or sit in DCIM.
Android also has true motion photos (the video inside the JPEG/HEIC, Samsung and Pixel): Show as one should play those
too, where the file carries it (XMP `MotionPhoto` / `MicroVideo`).

Also open: the 163 imported photos in "Φωτογραφίες από YYYY" folders still dated 21 Aug 2026 (the copy day) — the user
was asked whether to give them the folder's year; no answer yet.

---

# End of 26 September — the suite is complete; 0.3.0-beta.1 not published yet

Since the morning's entry below: Notes in both apps (D7, sync on a pause, history of 3, labels only); Both ways trash
(D6: declined → the phone's Trash, the phone's Trash → `/trashed` → the PC's Trash); Import photos/files on the PC,
straight to T7 in checked 1 GB batches; Offload by itself (T7 `auto`); Notifications and Settings (Appearance) pages;
a right-click menu; the sidebar as one entry each with bottom islands; Android sync every 30 min.

Fixed today, with tests: `/have` crashed on the empty first question after a fresh start (every sync failed); the
first notes import swept 85 trashed notes (the user later deleted them on purpose — **never restore without asking**).

State: PC runs the latest beta .deb; the phone has the latest debug build (**Media management not granted** — the Sync
page asks, or the "Allow Media management" button); the tablet has an older debug build (not connected since noon).
Not yet: publishing the beta (ask first), the phone's own History view, Files showing a drive as a location.

---

# 26 September — 0.3.0-beta.1 built, not published

Built today: **Notes** in both apps (SYNC_PLAN D7), a drive as a folder album with its own Screenshots/Documents and
Delete to the purgatory (D5b), the PC sidebar as one entry per part with bottom islands (Photos, Files, Notes, T7,
Devices), a two-column tablet home with a yellow Notes group, Android sync every 30 min on Wi-Fi (`SyncJob`), a
note changed on the PC nudges the devices, split screen no longer resets the app to Home.

**Fixed: a photo trashed on the PC came back** — `/have` now answers `declined` for what was deleted here on purpose
(`deleted_here`, filled by the PC's Trash and a drive's Delete; cleared when the photo is back in the library). A
device neither re-sends it nor counts it as safe on the computer, so a Move never lets its copy go because of it. Still
open: whether a device should trash its own copy (Both-ways trash, D6).

**Trap found today**: the first import swept 85 notes that were in the old app's Trash (their dates were months old);
restored from `~/.local/share/Notes`, and an imported trashed note now gets its 30 days from moving in. The tablet's
`deletions.json` had to be cleaned too, or its next sync would have deleted them again — a tombstone travels.

State: PC runs 0.2.0-alpha.10 (installed); the beta .deb/AppImage are in `Drive/desktop/release/`; the tablet has the
latest debug build; the phone has not had any of today's builds (not plugged in). Release APK: `app-arm64-v8a-release.apk`
(installing it over a debug build needs an uninstall, which wipes app data — keep debug on the two devices).

---


# Evening of 25 September — beta from here; next: a Notes applet

The user starts using Tetra for real now ("most of it works fine"). Tomorrow: **a Notes applet**, then beta.

State: both repos pushed on `bidirectional-sync`; PC runs the installed 0.2.0-alpha.4 .deb (16:13 build); phone and
tablet run the latest debug build. Read SYNC_PLAN **D3 built**, **D6 built**, **Move, as agreed and built** first —
the rules there were each asked for by the user; do not change what syncs, shows or deletes without asking.

What was done today, in one line each:
- Storage drive as a library location; Free space to T7 (read back, then deleted — a Trash frees nothing); 2,795
  photos (21 GB) live on T7-TEO; the old Trash copies were let go (Trash 43 → 22 GB, disk 94 → 92 %).
- Move: always keeps a window (default 1 month + favorites), dialog with numbers first, photos and files; the
  tablet went from ~4,500 photos to ~410. Deletes, never trashes, what the computer confirmed.
- Trash → Purgatory → gone (30 + 30 days, purgatory on the PC or a chosen drive; now on T7). History page.
- Folder albums are folders (Remove moves the file; Move to folder); On anywhere in a two-way chain is On everywhere.
- Map: red pins, group grid panel. Devices: copies colours by halving, holdings on cards, this PC's counts,
  storage drive counted as in the library; the same overview on the phones' Sync page.
- Fixed today: fast-scroll strip took Empty Trash's tap; >2,000 photos per Android request; a device's holdings
  now drop at once after a complete (numbered) inventory; empty-looking collections on a Move device are hidden.
- **Trap**: a syntax error in `main.js` shipped once (no window, no sync). `test/syntax.test.js` parses every file
  the package runs now — keep it. Always check the PC is running the build you think (`stat` the dpkg list).

Still open: Both-ways trash/restore mirroring; Hidden mirrored to the PC; long-press deselects without a drag;
Trash lists oldest first; first-run guide (download → pair by QR → rules card by card → Sync now); showing the
computer's photos inside a collection on a Move device.

---

# Morning of 25 September — Move is built, test it together at noon

Built on the computer (SYNC_PLAN "D3, built"): a photo's **location** (this PC or a storage drive), opening from
the drive or "Plug in T7-TEO", a drive card with **Backup / Storage**, **Offload on (a line) / off (a window)**,
copies required and favorites, the **"Free 21 GB?"** dialog with *Try 10 first*, notifications and a tray item,
and the copies bar with one band per count. 55 desktop tests. Built **0.2.0-alpha.4** (.deb + AppImage in
`desktop/release/`), not released, not committed.

**The old alpha.3 must never run after a Move**: its scan reads moved photos as deleted (and forgets their faces).
Install alpha.4 before moving anything, and do not go back.

Noon test, in order:

```bash
cp ~/.local/share/local-drive-desktop/library.db ~/Έγγραφα/Claude/Coding/Drive-Android-backups/library.db.before-move-2026-09-25
sudo dpkg -i ~/Έγγραφα/Claude/Coding/Drive/desktop/release/local-drive-desktop_0.2.0-alpha.4_amd64.deb
```

1. Quit Tetra from the tray, start the new one. T7-TEO plugged in. Devices → T7 card → **Storage**, Offload
   off, keep the last 1 year. The card should say ~2,664 photos, ~21 GB.
2. **Free 21 GB… → Try 10 first.** Check: 10 files in the system Trash, the 10 still in Photos (oldest, 2008),
   they open (from `/mnt/T7`), Details says "On T7-TEO".
3. Unmount/unplug T7: open one of them → "Plug in T7-TEO to open this" over the thumbnail. Plug back in.
4. Sync the phone and the tablet: they must not send those 10 back, and the Devices bar must not call them missing.
5. Restore one from the Trash (Photos → Trash): it is back on this PC (Details loses "On T7-TEO").
6. If all is right: the full Yes. Wait a minute for the notification path (tray item "Free …").

Also this morning:
- **Map** (desktop): red pins drawn by the map itself (the photo markers lagged every pan), light style, a group
  opens a grid panel from the bottom and the viewer pages only that group. Phone pins are red too.
- **A folder album is a folder** (both apps): in a collection that is an included folder, Remove *moves the file*
  out of the folder (a picker, Camera first); **Move to folder…** is in the selection bar (and the desktop viewer).
  Never overwrites (a taken name becomes "(2)"). Desktop: `folders.move`, test in folders.test.js. Phone:
  `movePhotosToFolder` (FolderAlbums.kt) after Android's write consent; the key includes the folder, so
  `rekeyPhoto(…, sameContent = true)` carries favorites, collections, location, faces and labels to the new key.
  Not tested on a device yet. Installed on the tablet; the phone was not plugged in.
- **Folder rule over Both ways** (user, 2026-09-25): a folder that is On on any device in a two-way chain is On on
  every device in it; a No given on a device does not hide it there. A device that only sends keeps its own answers.
  The folder's album is the yes that travels (`folderChoices` on the phone, `Folders.choices` with `bothWays` on the
  computer). The tablet had hidden DCIM, so the synced DCIM album arrived empty and its member was dropped for good;
  METADATA_EPOCH 6 pulls everything once more. Open: how an Off travels (today only deleting the album does).
  A device still *sends* only the folders it shows — I changed that to "every folder" by mistake and undid it.

Found on the phone, 25 September afternoon (not fixed yet):
- **Long-press selects, then deselects on release**; it only sticks if the finger moves a little.
- **Photos Trash lists oldest first** (user confirmed: "the view counts backwards, the latest is at the bottom"), so
  what was just trashed is at the bottom and looks missing.
- **Empty Trash "did not work"**: the fast-scroll strip covered the whole right edge, top bar included, and took the
  tap. Fixed (the strip is only as tall as its track) and installed on the phone; the user then emptied the
  phone's Photos Trash on purpose (broken copies from an earlier buggy sync). It deletes for good with no Android
  prompt (Media management) — Purgatory (SYNC_PLAN D6) should come before this is trusted with real photos. The
  Empty button still has no accessibility label.
- Asked: **a history — a manifest of what goes and what comes** (every transfer, trash, purgatory and deletion,
  per device), because sync rules are only safe if they can be read back.

---

# Handoff — night of 24 September 2026

Read `SYNC_PLAN.md` for *why* anything is the way it is — **section D3** is the design for everything below. This is
the first ten minutes, the state of the devices, and the traps that cost time today.

## Where everything is

| | |
|---|---|
| **Phone** | `~/Έγγραφα/Claude/Coding/Drive-Android`, branch `bidirectional-sync`, merged into `alpha` (PR #5) |
| **Computer** | `~/Έγγραφα/Claude/Coding/Drive` (Electron app in `desktop/`), same branch, merged into `electron-desktop` (PR #4) |
| **Released** | **0.2.0-alpha.3** on GitHub, both repos (APK; AppImage + .deb) |
| **Installed** | PC: the .deb (`/opt/Tetra`, menu entry "Tetra"). Phone and tablet: **debug** builds of alpha.3 — keep installing debug over them; a release APK needs an uninstall, which wipes app data |
| **Folders** | `~/Tetra/Photos`, `~/Tetra/Files` on the PC; `/sdcard/Tetra` on phone and tablet (renamed from `Drive` tonight, `desktop/home.js`, `TetraFolder` in DriveFiles.kt) |
| **T7 drive** | `T7-TEO`, ext4, UUID `f0544ced-…`, mounted `/mnt/T7`. **Full verified copy** under `/mnt/T7/Tetra/Photos` (3,969, 29 GB) and `/mnt/T7/Tetra/Files` (44, 103 MB), done 22:26. Four 0-byte `*.jpg.part` from 15:10 are old leftovers, harmless |
| **Backups** | `Drive-Android-backups/` — tonight's `2026-09-24-evening-*`, `2026-09-24-before-folders/`, and `~/.local/share/local-drive-desktop/library.db.before-tetra-folder` |

Build and test:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=$HOME/Android/Sdk
./gradlew testDebugUnitTest assembleDebug -q
~/Android/Sdk/platform-tools/adb -s 208c8192 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk   # phone
~/Android/Sdk/platform-tools/adb -s 971f6b37 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk   # tablet
```

Desktop: `cd desktop && npm test` (54 tests), `npm run dist` for the AppImage and .deb. The user runs the
**installed** app now — to test a dev build, quit Tetra from the tray first (it lives in the tray; closing the
window does not quit it, and a second copy exits at once).

## Next session, in the user's words

> complete the hard drive backup and move functionality, implement and test the move, the notification system
> for "free this much space" — "Yes" and they are gone. Could we use the hard drive as a library in the app as a
> new location so I can continue seeing and opening all the images, videos and files?

So, in order (all designed in SYNC_PLAN D3 — nothing here is hard-coded, every choice is a setting):

1. **The drive as a library location.** Each photo gets a location: this PC, or a storage drive. Thumbnails stay
   in the PC's cache, so the grid, People, collections and search keep working; opening reads from the drive when it
   is plugged in and says "Plug in T7-TEO" when not. Files: the drive is a location in Files. This comes first —
   without it a Move makes photos disappear from view.
2. **The storage role for a drive** (next to Backup), and **"do you have it?" answered for the library** (PC *or*
   its storage drive), or every device re-sends what was moved — the loop the user spotted.
3. **Move / Offload on the sender (the PC):** release to the drive what is verified there (read back, not just
   receipted), oldest first — either *keep the disk under N %* (Offload on, a slider) or *keep a year / month /
   week* (Offload off). The PC's copy goes to the system Trash, never a hard delete.
4. **The notification:** *"Free 22 GB — 3,100 photos older than a year are safe on T7-TEO. Yes?"* — Yes and they
   go. Plus a full-disk warning that is always shown (nothing watches free space today). The PC had ~80 GB free
   before syncing, 57 GB tonight.
5. **Test Move for real**, on a handful of photos first, then watch the phone and tablet not send them back.

Also:
- **The copies bar on the Devices page is fixed at three buckets** ("One copy · Two places · Three or more"). With
  the T7 added there are four places (PC, phone, tablet, T7) and every photo is in all four, which the bar cannot
  show. It should have one bucket per count, 1…N, where N grows with the devices that hold photos (the user noticed
  it right after adding the T7). `overview()` in sync.js already returns a row per `copies` value; the fixed three
  are in the Devices page's rendering. One-copy stays red and two amber — the colours mean risk, not count.
- The **T7's card on the Devices page** still offers "T7-TEO → this PC" and "Both ways", which do nothing for a
  drive — the Add-a-drive guide was fixed tonight, the card was not.
- A **folder-emblem catalog** (finance, medical, education, receipts…) on the personalisation card, next to
  colour — asked for, deferred.
- The AppImage needs **libfuse2** on Ubuntu 24.04+; a static-runtime AppImage would not.
- The phone's post-rename sync check did not finish (the phone was unplugged): compare `/sdcard/Tetra` with
  `~/Tetra/Files` next time it is plugged in.

## What tonight changed, shortest possible

- **ChatGPT's session reviewed and repaired**: names beat guesses again, no guess-vs-guess cards, combined people
  stay combined (a deleted "Εγώ" was holding six faces), Help organize is a page of cards.
- **People**: pencil menu (Rename · Choose face · Forget), History with Restore, forgetting syncs.
- **Folders** as user collections, asked about once, on both apps; files folders never offered.
- **Desktop**: scanning and analysis on a **worker thread**, the tray, bigger dialogs, system folders (Documents,
  Scanned Documents) with emblem icons on both apps, drive backups of photos and files (auto when plugged in).
- **The Devices page froze the app**: a missing index made its overview 1.2 s a call, asked every 3 s. Fixed
  (38 ms), with a test.
- **The Tetra folder** on every device, by renames.

## Traps that cost time today

- **sharp segfaults outside Electron's main process** on this machine (utility process, ELECTRON_RUN_AS_NODE) —
  that is why `analyzer.js` is a `worker_threads` Worker. Do not move it back into a process.
- **Every new desktop module must be added to `package.json` build.files** — analyzer, folders, drives and home
  were each missing once, and the AppImage would not have started. Check with `npx asar list`.
- **Freezes: pause the stuck process, do not guess.** `kill -USR1 <pid>` opens the inspector on 9229; a
  `Debugger.pause` over CDP names the function (that is how the Devices overview was found).
- **A file's time stamp through `utimes` comes back a hair early** — compare within 2 ms, not to the millisecond.
- **The scan refuses a missing Photos folder, or an empty one where 20+ photos were known**, instead of reading it
  as everything deleted. Keep that guard when drives become library locations — an unplugged storage drive must
  never read as deleted either.
- `pkill -f <pattern>` kills the shell that typed it when the pattern is in its own command line. Kill by PID.
