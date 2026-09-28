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

Next: drag to move (snap / free placement), floating pictures shown in place, then proper pages. Suggested to the user
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
