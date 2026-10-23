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
- [x] (calendar session, 17–18 Oct) **Calendar**, Tetra's last app (§ Calendar below): steps 1–7 built, 18 Oct, on
      Drive-Android branch `calendar` (worktree Drive-Android-worktrees/calendar, from writer-library 6fdf06a: a308980,
      242c094, 4cc9368, 4c43b7a, 7584567) and Drive branch `calendar` (worktree Drive-worktrees/calendar, from
      bidirectional-sync 36a82bd: c2ab218, ea3e221). Checked on the emulator (tablet size and phone size) and the desktop
      hidden; **not pushed, not merged, not on the tablet yet** — step 8 waits for the user. Skill installed.
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
      AutoFilter kept (22bebc0), Names (2ab13f5),
      Trace and error checking (b924d9c), newer functions' file names (51544bb),
      dynamic arrays (dc739be), row groups (40132d5), subtotals (ed52400), shapes (8bf77b9),
      drawings moved/sized/deleted (9b077de), contents sheet (0e3019b),
      Sources sheet (116c4d6), Review tab (b39004e),
      freeze panes (e621ff6), zoom to selection and the bar/tabs (0d32d4e),
      Goal seek and CSV import (cf4d0d0), Reapply (f6d3d41), File's Save a copy/AutoSave/Save as/Rename (4667b2e), the keyboard bar
      while typing in a cell (86dc6c7) and in a Slides text box (90463dc), Formulas' Recently used (4519744), Calculate sheet
      (7c1bdd0), Evaluate formula (5e528d4), Show all notes (00a7f4e), Protect workbook (bbaafa4), a note in place of an Excel 365
      thread (1c6a74b), Equation (1822d02), a save keeping Undo (59c9218), WordArt (3dca5bb), Page break preview (48bc258), Page
      layout view (efc4b16), Draw on sheet (70fcb8f) — C done (c378f71). Then B's open lines: Add text to a shape (797a5bc), Save
      picture as (7fb9ad0), Edit data on a deck's chart (4a5778e), Text effects (f40b0ab), Ruler and Reading view (5d9b9bf), Text
      direction (e300633), Spelling (4762498), the show's All slides grid (71d830f), Without animation / Use timings (41ec9e7),
      leftovers marked done or later (5d23b40). Then Office's last big pieces, in the order the main session chose overnight: View ›
      Zoom (ffd88ab), rich text typed in a cell (0c6f13b), character spacing (375298a), Reuse slides (e932366), things on every slide
      — the slide master (c28416f), Save as the other format (9e1a500), Audio (99a2a58), a film's Trim (da417a3). Checks at a925139: unit
      suite 460, 0 failing, 2 skipped; sweep 349 documents + 29 decks, none failing. With the .ods rich text (01:00): unit 464, 0
      failing, 2 skipped; sweep 349 + 29, none failing. At 22bebc0 (01:40): unit 471, 0 failing, 2 skipped; sweep 349 + 29, none
      failing. At e621ff6 (02:50): unit 484, 0 failing, 2 skipped; sweep 349 + 29, none failing. At c378f71 (04:10, C done): unit 490, 0 failing, 7 skipped
      (the sweep's and LibreOffice-only ones); sweep 349 documents + 29 decks, none failing. At 5d23b40 (04:55): unit 493, 0 failing,
      7 skipped; sweep 349 + 29, none failing. At da417a3 (06:20, the last big pieces done): unit 502, 0 failing, 7 skipped; sweep
      349 documents + 29 decks (each deck also saved as the other format), none failing. Emulator only. For the tablet too: Tables › Page
      layout, Note, Drop-down list (its values row under the ribbon), Series, Cell styles, Format as table, Conditional formatting, Protect / Unprotect and Lock cell, View › Headings, Page layout › Print titles, Header and Footer (and Export PDF), Home's alignment and Clear ▾, a sheet's Copy and Tab colour, Insert/Delete cells, Insert › Picture and Text box, Data › Sort, Keep filter in file, Formulas › Names, Precedents/Dependents/Error checking, =FILTER/SORT/UNIQUE/SEQUENCE spilling, Data › Group/Collapse, Data › Subtotal, Insert › Shape, a drawing tapped, dragged, sized, deleted, Insert › Contents sheet, Insert › Source, the Review tab (Spelling, notes, statistics), View › Freeze panes; then View › Zoom to selection, Formula bar, Sheet tabs, Page break preview, Page layout; Data › Goal seek, From text/CSV, Reapply; File › Save a copy, AutoSave (Undo kept after a save), Save as, Rename; the keyboard bar while typing in a cell (phone and tablet) and in a Slides text box; Formulas › Recently used, Calculate sheet, Evaluate formula; Review › Show all notes, Protect workbook; Insert › Equation, WordArt (a look and a bend); Draw › Draw on sheet (with the tablet's pen); a picture's Save picture as. Slides: Add text on a shape with none, Save picture as, Edit data on a deck's own chart, Home › Text effects, View › Ruler and Reading view, a shape's Text direction, Review › Spelling, the show's All slides grid (and on the second screen), Slide show › Without animation and Use timings; View › Zoom; Tables' part-bold typing; Home › Spacing (Words and Slides); Home › Reuse slides; Arrange › On every slide; File › Save as PowerPoint/LibreOffice; Insert › Audio; Video › Trim.
- [x] (office-strips session, 18 Oct, the only writer in this checkout) A › Made the same: the user's rule for options (18 Oct) — on a
      tablet/phone no lists over the page: presets get their own strip at the bottom (‹ name, ✓ / ×, More… → a window); desktop as is.
      In order: Slides › Design › Slide size; Design's Themes/Colours/Fonts/Background/Gradient; then every other popup in the three apps.
      Done: (1) the shared strip — `OfficeStripFrame` round each ribbon, `officeStrip` opens one in the ribbon's place (ribbon slides
      down, strip up, ‹ name, × ✓, Back cancels), `RibbonMenu` (a menu on a desktop, a strip on a tablet/phone); Slides › Design › Slide
      size on it: Widescreen 16:9, 16:10, Standard 4:3, 3:2, 1:2 (tall), Custom… (cm window); p:sldSz with PowerPoint's type
      (screen16x10, 35mm, custom), Impress's page size; with other changes unsaved it asks "Save and go on" instead of refusing. Test
      `each slide size saved` (pptx + odp, LibreOffice both ways). Unit 503, 0 failing, 7 skipped. Emulator (1280×800): strip, ✓, Ensure
      fit, Save and go on, Custom….
      (2) Slides › Design on strips: Themes (cards, the one tapped shown on the slides, More… = the full panel), Background and
      Gradient (shown as tapped, ✓ one Undo step, × back); Colours/Fonts' gallery now slides the ribbon down as Words' does.
      (3) The sweep (the user, 18 Oct: "everything that was recreated is made in to a list … Check in all applets of office"); on a
      desktop (ribbon at the top) every one is the menu it was. Found and changed:
      · every colour row (`Swatches`: Words' Text colour, Highlight, Shading, Page colour, the table's; Tables' Text colour, Fill, Tab
        colour; Slides' Text colour, Highlight, Fill, Border) → its own strip, picked then ✓, **More…** = Word's colour table (black and
        Word's ten standard colours on top, each a fifth lighter a row down) — the user: "a more that opens a table with these basic
        colors at the top and gradually brighter ones as you go down like the real word has".
      · Words: Aa, Spacing, picture Align, Design › Paragraph spacing, Watermark, Layout › Columns, Breaks; **Line numbers** = cards on
        the pages' strip like Insert › Page numbers (shown on the page as tapped, OK/Cancel) — the user: "I want the way the UI looks in Insert".
      · Slides: several chosen › Align, Aa, Spacing, Text effects, Arrange › Align, Text direction, Picture styles, Transparency, File ›
        Print, View › Zoom, References › Style; New slide / Layout and Table sizes strips now start with "‹ name".
      · Tables: Clear, Insert/Delete cells, Unhide sheet, Page layout's Orientation/Size/Margins/Scale/Header/Footer (Custom… opens its
        window), Formulas › Recently used, Home › Orientation.
      · Insert › Video (Words and Slides).
      Strips: presets picked then ✓ (× / ‹ / Back leave them); actions (Clear all, Shift cells down, Align left, Print…) act at the tap and
      the ribbon comes back. A phone: the name, × and ✓ on the tab row's line, the choices under them.
      Left as they are, deliberately: lists inside windows (Sort's column pickers), the top bar's ⋯ menu, right-click menus (see the next
      line), the tab-defining galleries shown in the ribbon itself as Word/PowerPoint do (Words' styles, table looks; Slides' transitions).
      Unit 503, 0 failing, 7 skipped. Emulator (tablet 1280×800 and phone 360 dp, ribbon at the bottom and at the top): Slide size,
      Themes, Background, Colours, Insert › Video, Words' Text colour + More…, Line numbers, Watermark, Spacing; Tables' Fill, Clear.
      Seen there: the emulator's Gboard (hardware-keyboard mode) floats its toolbar 28 dp above the room it tells apps it takes, over
      the ribbon; it can be dragged away (Gboard's own, not Tetra's).
- [x] (office-strips session, 18 Oct, carried on) The tablet walk's 22 findings (A › "The tablet walk, 18 Oct"): 1–14, 16–22 fixed, 15
      kept as the file has it (written there why); Tables › Selection › Select cells (the user's ask). Commits 9ea0280 … 77fbc42. Unit 505,
      0 failing, 7 skipped. All on emulator-5554 (tablet 1280×800; the phone size for the strips); the tablet was not touched.
- [ ] (the user, 18 Oct: "right click otions go in the selection tab in phone and tablet") Words, Slides, Tables: what a right-click
      menu offers is on the Selection tab on a phone and tablet (the ribbon at the bottom); the menu stays for a mouse.
- [x] (office-strips session, 18 Oct, after 0.5.0 Beta 2 — merged main's 02edcee) The user: "the resents does not update in office when
      I open a file from picker or Files. In tablet and bigger screens add a heart at the top to go strait to favorites. In phone it does not
      feet place it in the file tab. Do it for all a applets".
      (1) Recents: every file opened in Words, Tables, Slides or PDF — the home's list, the picker, another app, Files — goes on a list of
      its own (`OfficeRecents`); the home lists those first, then the newest of each kind, and reads both again when it shows (a resume,
      a file opened). Emulator: from another app and from Files, back to the home: the file on top. (054bd38)
      (2) The open file's heart, one shared piece (`rememberOfficeFavourite`, Files' favourites as the home's hearts): 600 dp and wider in
      the top bar of Words, Tables, Slides and PDF, filled when it is one; a phone in the File tab ("Add to favourites" / "Remove …"),
      the PDF viewer's ⋯. Emulator: arrange.pptx's heart → in the home's Favourites; Words' favourite filled; the phone's File tab.
      Unit 507, 0 failing, 7 skipped. Not done: a file that is not in Tetra's Files (a PDF opened from another app is read from a copy in
      the app's cache) has no heart. Seen: opening a file of Tetra's own Documents from another app makes a copy in "From other apps"
      each time ("Reading list (9).docx") — [x] open the file itself when it is already in Tetra's Files.
- [x] (open-in-place session, 18 Oct) A Tetra file opened from the picker or another app opens itself, not a copy in "From other
      apps" (the line above's last [ ]): `OfficeOpen` now also resolves content://media/… (MediaStore's own address, what other apps'
      Open with gives) and Tetra's own share address (content://com.kalotrapezis.drive.files/tetra/…) to the file; the picker's
      Files/Documents roots already did. Files outside the phone's storage are copied as before. Emulator: Reading list.docx and
      Ferry and rooms.pdf by content://media, the own address, the PDF picker (Tetra/Documents) and Words' picker (Documents root) —
      each opened in place with its heart, no new copy. Unit 508, 0 failing, 7 skipped. Not tried: a save after such an open (it is
      the same file object Files opens).
- [x] (timer session, 18 Oct) **Timer**, Android and desktop, branches `timer` in both repos (from `calendar`): § Timer below. Also
      the Calendar's and the Timer's folders hidden in Files on both. Not merged, not on the tablet.
- [x] (notes-audit session, 19 Oct) The Codex bug-audit patch
      (backups/2026-10-19-codex-worktrees/bug-audit-uncommitted.patch) against main: none of it was there, all its tests failed.
      Applied: Notes — one lock per notes folder for every NotesStore, a deleted note's id never read/listed/written again and a
      stale file of it deleted at sync, a failed save or ledger write throws instead of deleting, updatedAt always moves forward
      (a corrupt deletions.json still reads as empty, not a crash). Files — Copy and Empty trash refuse a folder holding a link
      (on main Empty trash deleted what an escaping link pointed at). NotesTest +6, DriveFilesTest +2; unit 547, 0 failing, 8 skipped.
- [-] (the user, 20 Oct: "no map scrap it the app will get 2GB") **Maps** (§ Maps below): dropped; worktree and branch `maps` removed.
- [~] (maps-link session, 21 Oct) **Maps restarted** (the user, 21 Oct: "I changed my mind start buidlding the map applet";
      "we may transfer this map to the desktop as well"): branch `maps`, worktree Drive-Android-worktrees/maps; step 1's
      files downloaded to ~/Tetra-maps by tools/maps-greece.sh (§ Maps below): greece.pmtiles 485 MB (Protomaps
      20261021, zoom 0–15, Geofabrik's Greece border) and greece-latest.osm.pbf 327 MB (md5 checked), ~3 min.
- [x] (maps-link session, 21 Oct) Timer: its waste files cleared (the user, 21 Oct: "some kind of a garbige collector be cause
      the timer app creates a lot of duplicat files what is nessesary and what is waste … on the phone I dont know about the
      desktop"): 23 of the 28 files on the PC and the tablet were sync's "<id> (n).json" copies from the 18 Oct bug (fixed
      since: one file per device). TimerFiles.clean / desktop timer.js clean, the same rule: every copy goes, the timer kept
      as it is (its newest state, pin and all, written into the device's own file first when only a copy held it). Runs
      when the Timer opens (phone) and when desktop Tetra starts. Sync never deletes, so the copies stop coming back only
      once every device has the new version (phone, tablet, PC .deb). Dry run on a copy of the PC's folder: 23 removed,
      the five timers unchanged. Tests: TimerTest, desktop test/timer.test.js.
- (the user, 22 Oct) **Desktop Tetra's scope**: no PDF editor, no Office, no Text editor on the desktop — "Office things that I
      can just throw in to files no reson to rebuild what is there" (they open in the PC's own programs). Maps does come to the
      desktop, because it syncs the same way the Timer does. Desktop Timer's copy-cleaner (d7fec05) is not in the installed
      0.6.0-beta.1 (built 19 Oct): a new .deb is needed; the PC's Timer folder still holds 25 old copies.
- [x] (22 Oct night, the user: "bring the destop up to speed with the android versions. No office or Text Edittor just the maps
      and the changes in the galery") Desktop 0.6.0-beta.3 (Drive 141d40e, aac3c9b, 36c1dd1): Photos' Text in the photo (a panel
      beside Details, select part to copy), Hide eyes and Hide text (the editor's Hide mode, bar or stars) — the phone's
      tessdata_fast models through tesseract.js, eyes from YuNet; and Maps (§ Maps). Checked in the packaged app (text, eyes,
      a route). The .deb is built, not installed (the user installs it: sudo dpkg -i …0.6.0-beta.3_amd64.deb).
- Other sessions: add your line here when you start.
- [x] (f5c3277, 23 Oct) Android Auto in the user's car: stuck on "Finding the way…" — the car refused navigationStarted and
      the HostException crashed Tetra; now logged and the route driven anyway. Android Auto lists a sideloaded Car App
      Library app in a real car only after a desktop head unit run (Unknown sources doesn't cover it, Google's docs); every
      new install drops it until the DHU is run again. So the phone is installed with tools/phone-install.sh: install, wait for
      the user's Start head unit server, the DHU until Android Auto's check passes Tetra, then closed (~15 s).
- [x] (0428e49, df4b2f0, 2170623, 23 Oct) Maps by car after the user's drive: a new route keeps the car's heading; turns
      warned a street early / 150 m+; sharp-bend warnings (setting); the car's recenter button; a busy crossing's picture in
      the turn card and the map close and flat near it (Νικήσιανη, Δράμα's 5 Δρόμοι); lane guidance from OSM's turn:lanes
      (tools/maps-lanes.py → greece-lanes.tsv in Tetra/Maps): a lane strip, "από τη δεξιά λωρίδα", "Συνεχίστε ευθεία" for a
      keep along the through lanes; exits warned at 3 km and "take the exit" where its lane begins. Not yet driven.
- [x] (a9d9756, 23 Oct) Maps drive traces (files/logs/drive, 7 days, Settings › Maps › Send log), richer navigation log
      lines, MapsGuide.guide shared by car, phone and MapsReplayTest (traces in ~/Tetra-maps/traces are replayed).
- [x] (fd304b1, 66bdce9, 6ad773a, 23 Oct) Android Auto tried with the user on the head unit: the first route no longer
      waits 22 s for the search list (tolls from their own lines); camera by speed and what is ahead (carView), car low on
      the screen; crossing square under ⚙ / in the corner; cards at the edges, bottom card icons in one row; bigger arrow;
      recenter checked (drag → let go, ◎ → back). Not yet driven.
- [x] (calendar-sync fix, 23 Oct; Drive 77b9d08) The user's ~/Tetra/TETRA_BUG_calendar.md: rm'd events came back, identical "(2)" copies,
      a birthday changed. Fixed in both repos (Drive SYNC_PLAN 6an): a path removed here is not asked back, Calendar events
      follow into the other side's Trash, the same bytes never land as "(2)", SEQUENCE first between two versions. The
      birthday: the phone's two changed copies read 23 Oct — 16:53:21Z an editor save in Γενέθλια (VALARM rewritten as
      -PT15H "Reminder", SEQUENCE 1), 16:53:27Z Delete › This event on 27 Jun 2027 in Personal (EXDATE, SEQUENCE 1);
      both moved into the phone's Trash. Desktop 0.6.0-beta.4 installed; Personal cleaned (387, no copies); phone and
      computer Calendar byte for byte the same (468 files). Tablet not yet synced (hotspot isolates it); APKs not installed.

## Waiting on the user
- [x] (answered 18 Oct) **Touch targets**: the Office settings decide — the user: "The tab look compact normal and desktop mode
  (Top bottom) is all settings in the Office section". Done: with the tools Normal, the tab row's tabs and the icon-only tools are
  48 dp (all three apps, ribbon at the top or bottom); Compact (chosen, or By itself with a mouse) keeps the smaller look. See A › Touch points.
- [x] (answered 18 Oct) **Tables' AutoSave**: kept shared — the user: AutoSave is one setting in Settings › Office, "the same will
  apply to all office apps" (Words, Tables, Slides alike).
- (office-F, 18 Oct, 05:00) **Office's last big pieces — which first, or later?** Words, Slides and Tables' lines in this plan are now
  done or marked later, except these, each a day or more: (1) Slides › File › Save as the other format (.pptx ⇄ .odp: a whole-deck
  writer for each) [done 18 Oct: the deck rebuilt in the other format, see B › File]; (2) copying slides between decks (PowerPoint's Reuse slides) [done 18 Oct]; (3) editing the slide master [done 18 Oct: things on every slide; a Slide Master view later]; (4) View › Zoom on the
  slide being edited [done 18 Oct, see B › View]; (5) audio, Words' Online video, a film's start and end in Slides [done 18 Oct: audio, a film's Trim; Online video 17 Oct]; (6) rich text typed in a Tables cell (part bold) [done 18 Oct, 0c6f13b];
  (7) character spacing in all three (the shared engine) [done 18 Oct]. The "later"s with evidence (none of the 29 decks uses them): slide tables'
  merge/split, pictures' brightness/contrast, SmartArt, right-to-left text.
  Overnight (05:10) the main session chose an order so the work goes on — smallest and least risky first: (4), (7), (6), (2), (3),
  (1), (5). Change it in the morning if you like; each is a commit of its own, ticked here as it is done.
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
      gesture). Done 18 Oct (office-F, the user's answer: the Office settings decide): in Normal size every tab is at least 48 dp tall
      (`OfficeTabRow`, 2 dp under it so the tools' touch areas do not reach into it), the icon-only tools 48 dp (`LocalToolSize` /
      `RibbonTools` inside the ribbons and Words' top bar — Notes and the text editor keep 40 dp), a labelled button at least 48 dp
      across (1 2 3 4, X², H1); Compact is as before. Re-run in Normal (emulator, every tab of all three): Words 0, Tables 2, Slides 2 —
      those four are the next button cut off where the ribbon line scrolls on (full size once in view). Words' fingerprint unchanged.
      Before/after shots: touch-targets/ribbon-48dp-before-after.png in the group's notes. Hand check: on the tablet.
- [ ] **Inconsistencies**: the checklist below, the three apps side by side on the tablet. Each difference is made
      the same or written down as deliberate.

**The tablet walk, 18 Oct 07:10–07:50 (main session, build 19781a2 on 971f6b37)** — fixed by the office-strips session in this order:
- [x] 1. Slides › Home › Paragraph: OK with only Line spacing changed puts the first paragraph's indent on every paragraph (sub-points
      lose their level). Only the fields changed are applied.
- [x] (the user, 18 Oct: "add a select mode in tables under selection") Tables › Selection › Select cells: while on, one finger dragged
      chooses cells at once (no long press); two fingers still move and zoom. Off: a long press, then drag, as before.
- [x] 2. Slides › Multilevel: the "1. / 1.1. / 1.1.1." card gives "1." at level 2 — dropped for slides (PowerPoint and Impress have no legal numbering).
- [x] 3. Slides: no gap between a number and its words ("1.Read") in the editor while unsaved; saved, level 2 has it, level 1 not. Now a number
      hangs half an inch (PowerPoint's 457200) and the .pptx gets exactly the indents the editor drew (test `numbered paragraphs drawn and saved alike`).
- [x] 4. Tables › Remove duplicates: a whole column (header tapped) refused; formula cells refused after the dialog (Excel compares
      values); "The first row is headers" ticked even when row 1 is data. Now: a column's letter takes the column, nothing chosen the table
      round the cell; formulas compared by what they show; headers ticked only for words over numbers.
- [x] 5. Tables: on a protected sheet a tap does not choose the cell, nothing says why — choose it, refuse typing with a message.
      Now the first tap on a locked cell says the sheet is protected (once per sheet); typing is refused with it. Found on the way: a tap
      in the ~1 s while the sheet is read again after a whole-file step (Protect, Hide…) is lost — the grid's gestures restart on the new
      sheet (`pointerInput(painter)`); [ ] keep them across a reload (the callbacks through rememberUpdatedState).
- [x] 6. Slides: Save clears Undo — all three keep the history over a save. Slides now keeps the deck and Undo; the file as opened is kept
      aside and each save writes the edits onto it (Tables did so already, Words never cleared it). A new slide size still starts afresh.
- [x] 7. Tables › Sheet › Unhide popup → strip (7ee3675).
- [x] 8. Slides › View › Zoom popup → strip (7ee3675).
- [x] 9. Slides › Insert › Table: "‹ Table" (7ee3675); one shared table-size picker with Words' Insert › Table. Now `TableSizeButton`
      in both: the size dragged out on a grid, on a strip on tablet/phone (✓ puts it in), in Words' window on a desktop.
- [x] 10. Words › Page numbers in Insert and in Layout, once a strip, once a list — one form. Layout has no Page numbers; its list was
      Line numbers, now cards on the pages' strip as Insert › Page numbers (7ee3675).
- [x] 11. Duplicates: Tables Sheet › Protect vs Review › Protect sheet; Formulas › Insert function vs Insert › Function; Show formulas
      in Formulas and View; AutoSum in Home and Formulas; Slides' Selection pane in Home and in the Shape tab — one place, or one form.
      Now: the sheet's tab says "Protect sheet" as Review does; Insert › Function is Formulas' Insert function (the same window); Show
      formulas only on Formulas (Excel's place); AutoSum and the Selection pane were already the same in both places (as Excel/PowerPoint).
- [x] 12. Slides › Design: after Save, Apply to all and Reset background go although the slide keeps its own background.
- [x] 13. Slides › View: Notes page leaves Normal highlighted; Normal does not hide the notes — exclusive, or a "Notes" toggle. Now PowerPoint's
      Show › Notes, a toggle of its own beside the views.
- [x] 14. Slides › View › Outline: sub-points at the main points' level, without their numbers. Now each line at its level, its
      bullet or number before it (drawn only; the words typed as before).
- [-] 15. Shadow "Offset: bottom right" barely visible in Slides, clear in Words — draw the same. They are drawn by one function
      (`ShapePicture.shadowOf`: 3 pt down-right, 40 % black, as the file says); a slide is ~1.6× a page's width, so on screen the same 3 pt
      looks smaller — as in PowerPoint next to Word. Kept as the file has it (deliberate; ask the user if a stronger look is wanted).
- [x] 16. Slides › Ungroup leaves nothing chosen (PowerPoint keeps the pieces); Undo after Paragraph drops the choice. Now the pieces
      stay chosen; Undo and Redo keep what is chosen while it is still on the slide.
- [x] 17. Slides: a table cell being typed is not marked on the slide. Now outlined and tinted in the app's colour.
- [x] 18. The ribbon's height jumps a few px between tabs (Slides Home vs Group/Shape; Tables Home vs View). Now every ribbon line is at
      least a tool tall (48 dp, Compact 40) inside its padding, in all three apps.
- [x] 19. Some ribbons centred (Design, Arrange, Sheet), others from the left (View) when they fit — one. Now all from the left (Office's
      way; a long tab has to start there anyway), in all three apps and the strips.
- [x] 20. Words' contextual tab row lacks Selection / × at its start when a shape is chosen (Slides and Tables have it). Now ×, which
      lets go of the picture, shape or table. Selection stays for words (Words' Cut/Copy work on text; a picture has its own tab).
- [x] 21. Tables' top bar has no Find (Words and Slides have it). Now before Undo, while editing too.
- [x] 22. Words › Insert › Shapes › Block arrows: left-right and up-down arrows drawn as diamonds. Their drawing was right — in a square
      the two heads meet (Word's too); now the gallery draws each in its proportions and puts it in at Word's size, one rule for all
      three apps (`shapeSize`: ⇔ 2:1, ⇕ 1:2; Slides used to put every shape in square).
- [x] (the user, 18 Oct, Tetra test.docx: "the position of [the text box] is different in Edit mode than Viewer mode … in edit mode it
      climbs around 5 lines and goes in front of [the text]") Words: a box placed freely on the page (in front, behind, no wrap) is
      drawn in the editor from its paragraph's top, as the pages lay it out; a box alone in its paragraph has its mark as given
      (\uFFFC), which the editor's paragraph finder did not look for — its paragraph was "not found", the box measured from the one
      before. Now found either way; the editor matches the viewer (emulator, the user's file). Unit 507, 0 failing.
Found on the way (office-strips, emulator, not fixed):
- [ ] Slides: a table put on a slide over a text box is drawn on top, but a tap there chooses the box under it (the hit test's order
      is not the drawing's).
- [ ] Slides › Numbering on "Olive harvest talk.pptx" gives "1. • Olives…": that deck's bullets are typed "•" letters, not PowerPoint
      bullets — Numbering could take a typed "• " / "- " off the line's start as PowerPoint's AutoCorrect does.
Working on the tablet: speaker notes, Select all/Group/drag/Ungroup, Distribute, Reset, Shape options Dash/Shadow (both), Gradient, Slide
size 16:9 Ensure fit, table Row below, Zoom 200% + pan, Fade + Fly In, the show letterboxed; Words' heart with dot + shadow; Tables'
Gridlines, column Hide, sheet Hide/Unhide, Protect/Unprotect; LibreOffice opens all three saved copies; touch targets Tables 2 (edge-cut).
Not walked yet: Tables Merge & centre, Replace, Chart, Link, Text to columns, Insert function, rich-text cell; Words beyond Insert/Shapes.

**The tablet walk, second round, 18 Oct 08:48–09:00 (main session, at acb4526 on 971f6b37)** — confirmed there: Slide size strip (3:2 saved
as 35mm, Ensure fit), Themes strip, Colours cards, Paragraph keeps levels, numbers' gap, Save keeps Undo, Tables' Find, Select cells, Merge
& centre, part-bold cell, Insert function's window, touch targets. To fix (office-strips session, in this order):
- [x] 1. DATA LOSS — Tables: C13 typing "=SUM(B1," then a long-press drag over B2:B4 moved the edit to B2; ")" and Enter wrote
      "=SUM(B1,B4" over B2 (AutoSave saved it). While a formula is typed a drag (plain, long press, Select cells) puts the range in at
      the cursor; it never changes the cell being edited; Enter writes to the cell the edit started in. (The user: "After a formula like
      SUM is in place can the user just click on cells and add them to the formula?" — yes, as Excel.)
- [x] 2. A tap adds a cell's name (keep); a drag adds nothing today — the gap. Now (1, 2): the cell being typed is fixed when typing starts
      (Enter writes there, the bar names it); while a formula is typed one finger dragged — plainly, after a long press, or in Select
      cells — names the range at the cursor, growing as it goes ("B2:B4"), the chosen cell untouched; two fingers move the sheet.
      Emulator: E2 "=SUM(" + drag B2:B4 + "," + tap D2 + ")" ✓ → =SUM(B2:B4,D2) = 5952 in E2.
      The user on the tablet, 18 Oct: "it works but it does not show the selection so I know easily where to stop" — now the range
      being dragged is outlined on the sheet (Excel's dashed blue over a light tint) while the finger moves.
      And: "if I tap and then drag … it gets sum(K2K2:K20" — now, as Excel's pointing, the reference a tap or drag put in last, with
      nothing typed after it, is replaced by the next tap or drag (B2 tapped, then B2:B5 dragged → "=SUM(B2:B5"; after a "," a new one).
      ✓ with a ")" missing closes it (as Excel). And (the user: "Do we have syntax help warning as well? … for missing a "," or a "(" | }
      that kind of thing"): the function's syntax shows above the keyboard while typing (it did); now a formula Tables cannot read is
      not put in at the first ✓ — the message says what is wrong ("a comma or an operator missing before “C2”", "a ) with no (",
      "it ends too soon"…), ✓ again keeps it as typed (Excel's arrays { } and table [ ] references, which Tables does not read).
- [x] 3. Formulas › Insert function › SUM: "=SUM(" in the bar but not editing (no ✓ ×, no keyboard, taps not added); tapping the bar
      throws it away; leaving saved "=SUM(" into the cell. It must open the bar for editing, the cursor inside the brackets.
      Not reproduced on the emulator (ribbon top and bottom, a hardware keyboard): there the bar opens with ✓ ×, the cursor in "=SUM(".
      Made robust: the typing (and its cell) is set before the bar takes the focus, and Enter or leaving never writes an unfinished
      function — missing ")" are closed as Excel does, "=SUM(" stays in the bar with a note (`SheetInput.closed`, tested). [ ] On the
      tablet with the keyboard case: the steps again, then File › Send log, to see what closed the typing there. The user, 18 Oct, on the
      tablet with the keyboard case: "it works — I can type, it has the buttons, I can choose cells and press Enter to apply".
- [x] 4. With a keyboard: typing on a chosen cell does nothing, a double tap does not open it — Excel starts typing on both. Now a key
      typed on a chosen cell starts its typing afresh (every key kept), Enter or F2 opens what is in it, the arrows move; a double tap
      opens the cell. Emulator (hardware keys): F3 "4278" Enter; B3 double-tapped → its 1850 in the bar.
- [x] 5. Search forgives a wrong letter ("Hello" finds "Yellow fill"); Replace / Replace all must change exact matches only. They did
      change only exact ones, but said so badly; now Replace takes the one shown or the next exact one, Replace all every exact one and
      says how many near matches it left as found.
- [x] 6. Two strip forms: "‹ title … × ✓" (Slide size, Themes) and "title … Cancel OK" (Colours, Multilevel, Line numbers cards) — one form.
      Now the card strips (`DesignGalleryStrip`: Colours, Fonts, Font, Style sets, WordArt, Page and Line numbers, Header & footer,
      Multilevel, Drop cap) have the same head: ‹ and the name, × and ✓ at the end; on a phone the head on its own line over the cards.
- [x] 7. Slide size: the size picked is not shown on the slide before ✓ (Themes and Colours are). Now shown (fitted) at the tap; ✓ asks
      Maximize or Ensure fit; × puts it back. With other changes unsaved it is only marked (✓ offers Save first, as before).
      Unit 506, 0 failing, 7 skipped (at this commit). Emulator only; the tablet was not touched.

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
      Done 18 Oct (office-F, 0c6f13b): letters chosen while typing in a cell take Bold, Italic or Underline from the keyboard bar alone
      (Excel's); the field shows them, the runs follow the typing (`SheetRich`: kept letters keep their looks, typed ones take the look
      before them, as Words), and Enter keeps them as the cell's rich text (Excel's runs, LibreOffice's spans, both written already).
      `part of a cell's words bold while typing`. Emulator: "Hello **world**" in F8, saved — Excel's run with <b/>, LibreOffice's bold span.
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
      Copying slides between decks done 18 Oct (office-F): Home › Reuse slides (PowerPoint's, with its Use destination theme) — another
      .pptx or .odp chosen in the system's picker, its slides shown to tick, Insert puts them after this one on this deck's Blank layout:
      their words as text boxes keeping their letters' looks, alignment, bullets and levels and shrink-to-fit; shapes with their fill
      and words; pictures, tables, charts; background colour; notes (`SlidesReuse`; `slides reused from another deck`, both ways
      between .pptx and .odp, LibreOffice's conversions). The other deck's master decorations stay behind. shortcut: custom outlines,
      gradients, effects, rotation, groups' insides, animations and films not brought (the count left out is said); a bullet as a
      mark and spaces. Emulator: owntable.pptx's slides 2 (a table) and 4 (a chart) put into arrange.pptx.
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
      backgrounds (read or made), slide size. Unit 424/0/7; sweep 349 + 29, none failing. (Both done since: 396a8f8, 1be3318.)
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
- [x] **Review**: comments (PowerPoint's own, read and written); spelling in the typing field.
      Comments done 17 Oct (office-C session), with Insert › Comment: Words' `NewCommentDialog` (its remembered name), Words'
      `CommentCard`s with their replies (Edit, Reply, Delete — no Resolve: `CommentActions.resolve` now optional, as neither
      PowerPoint's older list nor Impress keeps it) in a pane beside the slide on a tablet, over the slides on a phone; a marker on
      the slide where each stands (tap: the pane at it); Review › New comment, Comments, Previous/Next (round the deck), Reply,
      Delete comment. Read: PowerPoint's older list (+ p15:parentCm replies), PowerPoint 365's threads (replies, resolved), Impress's
      officeooo:annotation; written as PowerPoint's older list (commentAuthors.xml, eighths of a point — what LibreOffice writes and
      PowerPoint 365 shows; a slide's 365 threads become it when its comments are changed) and as Impress's annotations (a reply
      its own annotation: Impress has no threads). `comments saved` (both, LibreOffice's conversions; LibreOffice reads a
      comment's place only roughly — its own 5 cm comes back 4.76). Emulator: read, added, saved; phone size too. Spelling: not yet Unit 417/0/7; sweep 349 + 29, none failing.
      Spelling done 18 Oct (office-F): Review › Spelling — every box's words, slide by slide, checked with Words' dictionaries
      (`WriterSpelling`), each word not in them shown in the shared Excel/PowerPoint-style dialog (`SpellingDialog`, now Tables' too):
      Change (the box's words changed, its looks kept), Ignore, Ignore all. Emulator: "speling" on slide 1 made "spelling".
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
      Zoom done 18 Oct (office-F): View › Zoom — Fit, 150 %, 200 %, 300 % for the slide being edited (a tablet's layout): the slide in a
      window of the fitted size, moved about by dragging where nothing is chosen; items tapped, dragged and typed in as at Fit (rulers
      shown at Fit only). Emulator: arrange.pptx at 200 %, panned, the flag chosen.
      Editing the slide master — what it is mostly for, done 18 Oct (office-F): a thing put on a slide, Arrange › On every slide, goes
      onto the deck's masters (PowerPoint's slide masters' shape trees, its pictures their own relationships; LibreOffice's master
      pages in styles.xml, the styles it names copied there) and shows under every slide's own things; the master's own shapes (a
      logo, a line) and these are chosen on a slide (on top, or from the Selection pane) with a tab of their own — Only on this slide
      (one put on here), Delete from every slide (each master's copy). `a thing on every slide` (both formats, typed words and their
      colour, LibreOffice's conversions, then taken off). The master's title and body looks change deck-wide through Design's colours
      and fonts. Not yet (later, when asked): a Slide Master view of its own (layouts, the master's placeholders moved or restyled).
      Emulator: "Olive Co-op" on every slide of arrange.pptx, saved, chosen through the Selection pane and taken off every slide. Full run after it: unit 499, 0 failing, 7 skipped; sweep 349 + 29, none failing.
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
      18 Oct (office-F): colour, brightness and contrast — later: none of the user's 29 decks uses them (their pictures' only
      setting is transparency, which is done); compress pictures later too.
- [x] **Shapes**: text effects (shadow, outline, glow) and character spacing; merge shapes (union, combine,
      intersect, subtract — later); edit points (later).
      Text effects done 18 Oct (office-F): Home › Text effects on any box's words — Outline, Shadow, Glow, each on or off (WordArt's,
      already read and written both ways; an .odp has no glow, so none offered). Emulator: arrange.pptx's red words shadowed and glowing.
      Character spacing: later — Words has none either; the shared engine lays letters out without it, so it comes to all three at once.
      Character spacing done 18 Oct (office-F): in the shared engine (`LetterLooks.spacing`, drawn by `LetterLooks.Spaced` only on a run
      that has some — Words' fingerprint unchanged); Words' Home › Spacing and Slides' Home › Spacing (Very tight −1.5 pt … Very loose
      +3 pt), in Word's w:spacing, LibreOffice's fo:letter-spacing and PowerPoint's spc — read, shown in the editor and on the pages,
      written (`CharacterSpacingTest`: Words' .docx and .odt, Slides' .pptx and .odp, LibreOffice's conversions each way). Tables: none
      — Excel's and Calc's cells have no character spacing. Emulator: Reading list's "Greek" very loose, saved, on the page.
- [x] **Transitions**: duration; sound (later); advance on click and/or after a time set here (the file's `advTm`
      is played today, not set); **Morph** (later: the most used modern transition).
      Found done 18 Oct (office-F): Transitions › Options — Fast / Medium / Slow, and Next slide: On click, After 3 / 5 / 10 / 20 s.
- [~] **Animations**: start on click / with previous / after previous; duration and delay; entrance, emphasis and
      exit kinds; the animation list (pane) to reorder; animation painter; motion paths and triggers (later).
      Found 17 Oct (office-D session): start, duration (Fast/Medium/Slow), delay, the kinds and Earlier/Later were already in Options and
      Effect. Added: **Animation pane** (the slide's effects in order — click number, what, to what, when, how long; tap to choose, ↑ ↓,
      take away) and **Animation painter** (the chosen thing's effects put on the next thing chosen, in place of its own). **Shared
      piece:** none of Words' (Words has no animations); the format painter's way of working. Emulator: Fade painted from the title onto
      the subtitle, both in the pane. Not yet: motion paths and triggers (later).
- [x] **Slide show**: go to a slide by number or from a grid during the show; keyboard keys as PowerPoint's (N/P,
      arrows, B/W screen, number + Enter, Esc); keep or discard the pen's ink at the end, saved into the deck;
      set-up options (loop, without animations, use timings or not); subtitles (later).
      Done 17 Oct (office-C session): a number + Enter and PowerPoint's keys (with Loop until Esc, B › Slide show above); at the end of a
      show drawn on with the pen (while editing), "Keep your ink?" — kept, each slide's strokes become a drawing on it where they were
      (Words' drawing picture, red as the pen, Edit drawing draws on). Emulator: a line under the subtitle, kept. Not yet: a grid to go to
      a slide during the show, without animations, use timings or not; subtitles later.
      A grid to go to a slide during the show done 18 Oct (office-F): the show's tools › All slides (PowerPoint's See All Slides) —
      every slide shown, the one on screen marked, one tapped goes there (the second screen follows). Emulator: slide 3 from the grid.
      Without animation and Use timings done 18 Oct (office-F): Slide show › Without animation (each slide whole at once) and Use timings
      (off: on a click only), kept in the file as Loop is — PowerPoint's showPr showAnimation / useTimings, LibreOffice's settings
      animations="disabled" / force-manual (`shown without animation and timings`; LibreOffice carries Use timings across, not
      Without animation). Emulator: both on the Slide show tab. Subtitles: later.
- [ ] **Record** (later): record narration and timings per slide; export the deck as a video.
- [x] **View**: reading view; ruler; notes and handout masters (later); grayscale preview (later).
      Done 18 Oct (office-F): View › Ruler (Words' HorizontalRuler and VerticalRuler round the slide being edited, in Words' unit from
      its top left; a tablet's layout, as Words shows its rulers) and Reading view (the show from the chosen slide — on a phone or
      tablet the screen is its window). Emulator: a 4:3 slide's 25.4 × 19.05 cm; slide 4 shown.
- [x] **File**: Save as template (.potx, into Tetra's Templates folder, where Design › Themes finds it); Save as
      .odp / .pptx; remove notes and comments before sharing (Inspect); password protection (later).
      Template and Inspect done 17 Oct (office-C session, with From Words' File above). Saving as the other format done 18 Oct
      (office-F): File › Save as has PowerPoint / LibreOffice; the other one rebuilds the deck on Tetra's blank of that kind (assets
      blank.pptx, and blank.odp — LibreOffice's conversion of it), sized as the deck, every slide as Reuse slides brings one plus its
      master's and layout's decorations, background picture, transition, hidden mark and notes (`SlidesReuse.convert`); Slides goes
      on with the new file. `saved as the other format` (both ways, LibreOffice's conversions); the deck sweep now also saves each of
      the 29 as the other format (all slides, LibreOffice's pages the same). Left behind (said in the dialog): animations, films, a
      group's insides; placeholders become text boxes on a plain theme. Emulator: arrange.pptx → .odp → .pptx, both read back.
- [x] **Text**: right-to-left text and text direction (vertical); highlight (planned in Home above).
      Done 18 Oct (office-F): a shape's Text direction — Horizontal, Rotate all text 90°, 270° (PowerPoint's bodyPr vert / vert270;
      LibreOffice's loext:writing-mode tb-rl90 / bt-lr, on the shape's style and its paragraphs', as LibreOffice writes them), read
      and drawn turned (`SlideText.vert`). `text turned 90 and 270 degrees` (both formats, LibreOffice's conversions; its PDF of
      ours shows them turned). Emulator: arrange.pptx's rounded box turned. Highlight: on Home already. Right-to-left: later —
      Tetra's languages are Greek and English.
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
      Sound done 18 Oct (office-F): Slides › Insert › Audio (PowerPoint's Audio on My PC) — a sound from the phone (mp3, m4a, wav, ogg,
      aac, flac) as a speaker in the slide's middle, its tab "Audio" with Play, played in its frame when tapped in a show; saved as
      PowerPoint's (p:pic with a:audioFile and p14:media, the audio relationship) and Impress's (draw:plugin with the sound's type —
      audio/x-wav for a WAV, LibreOffice's name: with audio/wav its .pptx leaves the sound out). `a sound put on saved` (both formats,
      LibreOffice's conversions). Emulator: a tone picked from Music, saved, read back as a sound, played. Online video was done 17 Oct.
      A film's start and end done 18 Oct (office-F): the Video tab's Trim (PowerPoint's Trim Video) — a range over the film's length,
      to a tenth of a second; it plays from that start and stops at that end, editing and in a show (the player seeks to the very frame,
      not the keyframe before it). Kept as PowerPoint's p14:trim in the film's p14:media (ms left out at the start and at the end), on a
      film just put on or one already in the file; read back. Only in a .pptx: LibreOffice has no trim (the button is not shown on an
      .odp, nor for online videos). `a film trimmed`. Emulator: an 8-second counting film trimmed to 3–6 s, saved, reopened, played 3→6.
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
      Found done 18 Oct (office-F): a text box's Fill and Border (and Shape options' width, dash, shadow) on its tab.
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
- [x] **Picture / Shape** (Words' Picture tab): Add text to a shape, Edit text; Shape options (fill and outline
      colour, besides width and dash above); Save picture as; Edit data on a chart, Edit equation, Edit drawing.
      Already there (found 18 Oct, office-F): Edit text, Fill / Border / Text colour, Edit equation, Edit drawing, Edit data on a chart
      put on here. Add text done 18 Oct (office-F): a deck's shape with no words gets them (typed as a box's are): in a .pptx a text
      body of its own, centred as PowerPoint's (`words added to a shape that had none`); in an .odp a paragraph in the shape.
      Emulator: arrange.pptx's arrow says "Next", LibreOffice reads it there.
      Save picture as done 18 Oct (office-F): Words' own, now shared (`savePictureToPhone`: into Pictures/Tetra, its kind told by its
      first bytes) — on a picture's tab in Slides and Tables. Emulator: arrange.pptx's flag saved as a PNG (Tables' not tried there).
      Edit data on the deck's own chart done 18 Oct (office-F): Words' chart editor on its numbers, then put on in its place as Slides'
      own chart (as a deck's equation is). shortcut: the file's own styling of the chart not kept, and its old part stays in the
      package unreferenced. Emulator: arrange.pptx's bar chart made a line chart; LibreOffice's PDF of it shows the line.
- [~] **Table** tab when a table is chosen (Words'): row above / below, column left / right, delete row, column,
      table; merge and split cells; borders and shading; table styles; Make chart from the table.
      Done 17 Oct (office-D session), for a table put on in Slides: Make chart (Words' `OfficeCharts.fromTable`: the first row names the
      series, the first column the categories, into Words' chart editor), Row above / below, Column left / right (round the cell last
      tapped; the table a row taller, a column wider — keeping its width when it would leave the slide), Delete row, column, table; its
      fill and words' colour as before. **Shared piece:** Words' Table tab (its labels, icons and order) and `fromTable`; the rows and
      columns are `SlidesEdit.rowAdded/columnAdded/rowTaken/columnTaken` on the table's cells. `a table's rows and columns`; emulator:
      2×2 made 3×3, saved — LibreOffice opens it. Not yet: merge and split, borders, table styles.
      18 Oct (office-F): table styles are there as the Fill colour's banded look (PowerPoint's Medium Style 2 in that colour, read
      from a deck's own styles too). Merge and split, cell borders: later — they need spans through the reader, the drawing and
      both writers, and the user's decks have one table in all, with none.
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
- [x] **While typing**: Words' keyboard bar (bold, italic, underline, highlight, voice typing) over the keyboard.
      Done 18 Oct (office-F): Words' KeyboardBar and rememberDictation over a text box's typing panel, on tablet and phone
      (the ribbon is hidden while typing on both); Bold, Italic, Underline, highlight on the box as Home's, voice typing in
      at the cursor (Insert's putWords), ⋯ puts the words in and brings the ribbon back. Emulator: arrange.pptx's subtitle bold.
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
- [x] **Insert**: **Chart from the chosen cells** (Words' chart editor, the series from the cells); picture; shape
      and text box; link; **notes and comments** (they are only shown today); symbol; a checkbox in a cell; a drop-down
      (data validation list); sparklines (later); PivotTable (later).
      A checkbox in a cell: later (18 Oct, office-F) — it is Excel 365's alone (a cell look's feature property bag; older Excel
      shows TRUE/FALSE, LibreOffice nothing); a drop-down list of Yes/No does the job meanwhile.
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
      conversions); emulator: a text box at B7, a picture at C14.
      Drawings moved, sized, taken away done (office-F session, 18 Oct): while editing, a picture, chart, shape or text box tapped is
      chosen (Excel's green frame; its own tab, named for what it is): dragged, it moves to the cell its corner is dropped on; Size
      (width and height in Words' unit); Delete. Found by its place in the file (`SheetDrawing.ordinal`: Excel's anchors, LibreOffice's
      frames and shapes as the reader counts them); `SheetDrawings`. `drawings moved sized and taken away` (both formats, LibreOffice's
      conversions); emulator: a shape dragged from C8 to G13, deleted. shortcut: a move lands on a cell's corner (its offset in the
      cell not kept); Size keeps no aspect lock.
      Shape done (office-F session, 18 Oct): Insert › Shape — Words' and Slides' `ShapesDialog` (styles, fill, outline, every preset),
      the shape at the chosen cell sized as Words sizes it: Excel's preset shape (prstGeom, arrows' line ends), LibreOffice's custom
      shape with Words' geometry (`ShapeGeometry.odfGeometry`) and a graphic style of its own. `shapes put on a sheet` (both formats,
      LibreOffice's conversions); emulator: an orange heart at C8 of an .ods.
- [x] **Formulas**: browse functions by category (the typing row exists); named ranges (Name Manager); show what a
      cell uses and what uses it; error checking; more functions — the dynamic ones (XLOOKUP, FILTER, SORT, UNIQUE),
      checked against LibreOffice as the 129 are.
      Browse by category done (office-F session, 17 Oct): Formulas › Insert function — Excel's categories (Maths, Statistics, Logical,
      Text, Date and time, Lookup, Information) over the cell bar's own list (`SheetFunctions.all`, its arguments and words; seven it
      lacked though Tables works them out added: CEILING.MATH, FLOOR.MATH, STDEV.S, STDEVP, VAR.S, VARP, RANK.EQ), a search; tapped, a
      new formula in the cell or put in where one types (`FunctionsDialog`). `functions in categories`; emulator: =MAX( from Statistics.
      Named ranges done (office-F session, 18 Oct): Formulas › Names while editing — Excel's Name Manager: the workbook's names and
      what each stands for, Delete, and a new name for the chosen cells (Excel's rules for names); Excel's definedNames, LibreOffice's
      table:named-range — whose names are now read too, lazily (they sit at content.xml's end) when a formula first needs one, so
      =SUM(Amounts) works on an .ods. `SheetNames`, `Workbook.nameList`. `names defined and taken away` (both formats, a formula using
      it, LibreOffice's conversions both ways); emulator: Amounts for B2:B3 of an .ods, =SUM(Amounts) 1192.50.
      Seen on the way (not changed): in an .ods a new formula's result shows the phone's separators (1,192.50) beside the file's
      own stored text in LibreOffice's (1.192,50) — older behaviour; worth a look with Words' and Tables' number display.
      Trace and error checking done (office-F session, 18 Oct): Formulas › Precedents (the cells the chosen cell's formula reads, names
      followed), Dependents (this sheet's formulas reading it), Error checking (each tap the next error cell, "#DIV/0! in C7 (1 of 3)"),
      Remove marks — marked on the sheet as found cells are (Excel draws arrows). `SheetTrace`; `formulas traced and errors found`;
      emulator: B5's B2:B3 marked. shortcut: another sheet's cells are not marked.
      Newer functions' names in files fixed (office-F session, 18 Oct): Excel keeps XLOOKUP, IFS, TEXTJOIN, MAXIFS, STDEV.S and the rest
      as "_xlfn.XLOOKUP(" (FILTER, SORT "_xlfn._xlws."), LibreOffice as "COM.MICROSOFT.XLOOKUP(" — a formula typed here was saved
      bare (Excel shows #NAME? then), and a file's prefixed ones read as unknown functions (never worked out again). Now read without,
      written with them (`SheetCalc.fromFile/toXlsx/toOds`, names as LibreOffice 26.2 writes them). `newer functions named as files name
      them` (both formats); full run after it: unit 474, 0 failing, 2 skipped; sweep 349 + 29, none failing.
      Dynamic arrays done (office-F session, 18 Oct): FILTER, SORT, UNIQUE and SEQUENCE (with XLOOKUP, already there) — their results
      spill into the cells below and right (`SheetCalc.Arr`; comparisons and arithmetic on ranges work cell by cell, so FILTER(A:A,
      B:B>5) works; SUM(FILTER(…)) too); a spill follows its data, a value in its way gives #SPILL!, cleared it goes
      (`SheetCell.spill`/`spilledFrom`, `SheetRecalc`). Saved as Excel's array formula over its cells (Excel shows {=…}) and
      LibreOffice's matrix, and those read back as spills. `results spilled`, `a spill saved` (both formats, LibreOffice's conversions
      — it works them out again itself); emulator: =SEQUENCE(4) down F2:F5. Full run after it: unit 476, 0 failing, 2 skipped; sweep
      349 + 29, none failing. shortcut: a formula reading a spill's lower cells is worked out again at the next change, not at once;
      SORT by columns and SORTBY not yet.
- [x] **Data**: a sort that changes the file, by several columns; the filter saved as the file's AutoFilter; remove
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
      Group and outline rows done (office-F session, 18 Oct): Data › Group / Ungroup the chosen rows (a level deeper or less), and on a
      grouped row (or the row after a group) Collapse / Expand — Excel's row outlineLevel, hidden, collapsed on the summary row and
      sheetFormatPr outlineLevelRow; LibreOffice's table:table-row-group (display "false", rows visibility "collapse"); both read
      (`Sheet.rowLevels`). `SheetOutline`. `rows grouped and collapsed` (both formats, LibreOffice's conversions); emulator: rows 2–3 of
      an .ods grouped, collapsed, expanded. shortcut: no outline bar beside the row numbers (the ribbon has the buttons); columns not
      grouped; rows across a group's edge not grouped in an .ods.
      Subtotals done (office-F session, 18 Oct): Data › Subtotal — Excel's dialog (at each change in a column, Sum/Count/Average/Max/
      Min/Product, the columns to total): a row after each group ("North Total", SUBTOTAL(9,…)), a Grand Total, each group's rows
      outlined — one change to the file, rows put in as Insert row does (`SheetShift`, every reference following). SUBTOTAL added to
      the engine, leaving out cells that are subtotals themselves (`Context.formulaOf`), so the Grand Total counts each value once.
      `SheetSubtotal`; `subtotals made` (both formats, LibreOffice's conversions); emulator: Region/Sales → 30, 12, 3, Grand Total 45.
      Drop-down lists done (office-F session, 18 Oct): Data › Drop-down list on the chosen cells — the values typed, comma-separated
      (Excel's Source box), Take away; Excel's list dataValidation and LibreOffice's table:content-validation (`SheetLists`, a change to
      the file), both read (`Sheet.lists`/`listAt`, `SheetList`: a quoted list, or this sheet's cells). While editing, the chosen
      cell's values show as a row under the ribbon to tap; a value typed that is not one of them is refused (Excel's stop).
      `drop-down lists made and taken away` (both formats, LibreOffice's conversions); emulator: E2:E4 Yes/No/Maybe, "No" tapped into
      E3, "Hello" refused, saved and read by LibreOffice. shortcut: a list from another sheet's cells is kept but not looked up.
- [x] **Page layout**: margins, orientation, paper size, print area, rows repeated on every page, fit to pages, header
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
- [x] **Review**: protect a sheet (respect a protected one: no typing in locked cells); spelling.
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
      unprotected.
      Spelling done (office-F session, 18 Oct): a Review tab, as Excel's — Spelling (Words' Hunspell dictionaries, Greek and English, on
      the phone: `WriterSpelling`): the sheet's words in reading order, each not known shown with its cell and suggestions in Excel's
      dialog (Change, Ignore, Ignore all); also Previous / Next note, New or Edit note, Protect / Unprotect sheet, and Workbook
      statistics (sheets; this sheet's cells, numbers, formulas, words, notes, drawings, used range). `SheetSpelling`; `spelling in a
      sheet` (Words' English dictionary read in the test); emulator: harvst → harvest, ferri → ferry in a CSV, the counts.
- [x] **View**: gridlines and headings on or off; split the window (later).
      Gridlines done (office-F session, 17 Oct): View › Gridlines while editing, kept in the file (`SheetVisibility.gridXlsx`: the sheet
      view's showGridLines; `gridOds`: the sheet's ShowGrid in the view's Tables list, else the view's own — and the reader now takes
      the view's own when a file has no Tables list). `gridlines off and on` (both formats; LibreOffice 26.2 carries them neither way).
      Emulator: off.
      Headings done (office-F session, 18 Oct): View › Headings while editing — Excel's showRowColHeaders (a sheet's view), LibreOffice's
      HasColumnRowHeaders (the whole file's: its headings are one setting); read too (`Sheet.headings`), and off the grid has no
      letters and numbers, the cells from the edge, as Excel. `SheetVisibility.gridXlsx`/`gridOds` take the setting's name.
      `headings off and on` (both formats; LibreOffice carries them both ways); emulator: an .ods's off and on.
      Freeze panes kept in the file done (office-F session, 18 Oct): View › Freeze panes (the rows above and columns left of the chosen
      cell), Freeze top row, Freeze first column, Unfreeze while editing — Excel's frozen pane in the sheet view, LibreOffice's split
      settings in the sheet's view entry (its Views › Tables made when a file has none, as LibreOffice run without a window writes
      them). Viewing alone keeps the view-only "Keep rows above at the top". `panes frozen` (both formats; LibreOffice's .xlsx of the
      .ods keeps it — its .ods made without a window keeps no view at all); emulator: C3 frozen in an .ods.
- [x] **References**, where it makes sense: a **contents sheet** listing every sheet as a link (`#'Sheet'!A1`), for
      workbooks with many sheets; a **Sources sheet** of where the data came from, in the chosen style, with Words'
      dialogs. In-text citations in cells: not planned unless asked.
      Contents sheet done (office-F session, 18 Oct): Insert › Contents sheet (a workbook of two sheets or more) — a first sheet,
      "Contents", each shown sheet's name a link to its A1 (made again, not twice, when there is one); one change to the file:
      Words'-shared parts in a row (`SheetBook` add, delete and move, the cells, `SheetLinks`). `SheetContents`; `a contents sheet made`
      (both formats, twice, LibreOffice shows it); emulator: Contents first with Numbers and Second as links.
      Sources sheet done (office-F session, 18 Oct): Insert › Source — Words' `SourceDialog`; the reference as APA 7 writes it (Words'
      `CiteStyle.APA.entry`, its italics the cell's rich text) on a "Sources" sheet (made the first time, last, its column wide, its
      cells wrapping) under "References", in APA's order. `SheetSources`; `a sources sheet made` (both formats, two sources sorted, the
      italic title kept by LibreOffice both ways); emulator: Kirk, A. (2021). *Olive harvests*. Crete Press. shortcut: a source is kept
      as its reference's words (change its cell to change it), not Words' fields.

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
- [x] **Insert**: Equation and WordArt (Words'); header and footer (with Page layout); icons (later); slicers
      (later, with PivotTables).
      Done (office-F session, 18 Oct): header and footer (Page layout, above); Insert › Equation (Words' EquationDialog and
      MathDrawable, as Slides': the equation put on at the chosen cell as a picture, its letters 16 pt). Emulator: x²+a/b at F14.
      shortcut: a picture, not Excel's own equation (OMML in a text box), so it is not edited again as an equation.
      Insert › WordArt (Words' WordArt strip: a look and a bend; OK asks its words, "Your text here"): Excel's WordArt text box
      (no fill or line; the letters' outline, glow and shadow in the run; prstTxWarp), LibreOffice's styled words or, bent, its
      fontwork (`SheetChart.wordArtXlsx` / `wordArtOds`). Read back with its size, weight, effects and bend, and drawn on the
      sheet with Words' EffectSpan and TextBoxDrawable.bent (`WordArt put on a sheet`, both ways through LibreOffice; its
      outline is hollow letters there, as in Words' .odt). Emulator: an orange arch and a blue outlined one at F8.
      Not done: icons (later), slicers (later, with PivotTables).
- [x] **Formulas**: Recently used; Insert function by category (as Excel's dialog: a search, the arguments explained);
      Calculate now and automatic / manual; trace precedents and dependents as arrows; evaluate a formula step by step.
      Done (office-F session, 18 Oct): Insert function by category with a search (FunctionsDialog); Precedents, Dependents and
      Error checking (cells marked, `SheetTrace`); Recently used (the last ten functions put in — Insert function, the row of
      functions while typing, AutoSum's — kept in Words' store; a ribbon menu, and first in Insert function). Emulator: AVERAGE
      there after Average.
      Formulas › Calculate sheet (Excel's: every formula here worked out again, NOW and TODAY moving on; one step of Undo).
      Emulator: =NOW()*86400 moved on by 20. Not done, on purpose: manual calculation — Tetra works out on every change (a file
      set to manual keeps its setting); other sheets are worked out as their own cells change.
      Formulas › Evaluate formula (Excel's dialog: Evaluate shows the next step, the leftmost innermost part replaced by its value —
      `SheetCalc.steps` on the tree, written back with `SheetCalc.show`; `formula evaluated step by step`). Emulator: B5's
      =SUM(B2:B3) to =1192.5. Precedents and dependents are marked cells, not arrows.
- [x] **Data**: import a CSV or text file into the sheet; Reapply and Clear the filter; advanced filter (later);
      Flash fill (later); Goal seek; consolidate (later); forecast (later).
      Done (office-F session, 18 Oct): Data › From text/CSV (the system's file picker; the table typed in from the chosen cell, numbers
      and dates read as typing reads them, one step of Undo) and Goal seek (Excel's dialog; `SheetGoalSeek`: the sheet's own
      working-out tried by the secant method, then put back; the found value one change). `goal sought`; emulator: B3 made 765.5 so
      B5 is 2000; sort.csv imported at F8. Clear filter was there (a column's tab, its chip); Data › Reapply (while a filter or sort is
      on) works them out again on the cells as they are now, as Excel's.
- [x] **Review**: next / previous comment, show all; notes (Excel's older comments) apart from threaded comments;
      workbook statistics (as Words' counts); protect the workbook's structure; accessibility check (later).
      Done (office-F session, 18 Oct): Previous / Next note, Workbook statistics, Protect sheet (above); Show all notes (each
      note in Excel's yellow box beside its cell, drawn over the cells, frozen rows' and columns' too). Emulator: A2's note shown.
      Review › Protect workbook (its structure: no sheet added, deleted, renamed, moved or hidden — refused with a word, as Excel;
      a password or none): Excel's workbookProtection lockStructure (SHA-512 as Protect sheet), LibreOffice's structure-protected
      (`SheetProtect.bookXlsx` / `bookOds`; `a workbook's structure protected and unprotected`). LibreOffice keeps it in an .ods,
      but its .xlsx filter neither reads nor writes Excel's lock (26.2). Emulator: + refused while protected, then unprotected.
      Threaded comments (Excel 365's) are read with their replies ("Ann: …" a line each); a note put on, changed or cleared on
      such a cell now takes the thread away too, so Excel shows the note, not the old thread over it (`a threaded comment read
      and a note put in its place`). Not written: replies (none of the user's 29 workbooks has a thread, and no Excel here to
      check one) — later, when asked. Accessibility check: later, as planned.
- [x] **View**: page break preview and page layout view (with Page layout); freeze the first column, and rows and
      columns together; zoom to the chosen cells; show or hide the formula bar, headings, gridlines, sheet tabs.
      Done (office-F session, 18 Oct) but the page views: freezing (above, kept in the file); View › Zoom to selection (the chosen
      cells fill the screen: `SheetPainter.fit`), Formula bar and Sheet tabs shown or hidden (the viewer's, as Excel's View
      options; the bar comes back while typing); headings and gridlines kept in the file (above). Emulator: A1:D5 zoomed to, the
      bar and tabs hidden. Not yet: page break preview, page layout view.
      View › Page break preview (Excel's: the pages as `sheetPages` cuts them for printing and PDF — each framed in blue with
      "Page n" across it, what is not printed greyed; a print setting changed shows at once). Emulator: a 120-row sheet, pages 1–2.
      View › Page layout (Excel's: the sheet as its pages print — Export PDF's pages with margins, header and footer, white on
      grey one under another; a page tapped goes back to the cells at its first). Emulator: page 2 tapped, A54 chosen.
- [x] **Draw**: pen and highlighter over the sheet (Words' Draw tab), saved as ink.
      Done 18 Oct (office-F): a Draw tab, Draw on sheet — Words' DrawDialog (two pens, the highlighter, eraser, Undo) on the cells
      as the sheet shows them from its top left (960 × 640 at 100 %), the drawing put on where it was drawn as Words' and Slides'
      ink: a see-through PNG with its strokes kept in it (pictures now take an offset in their cell: `a drawing put on inside
      a cell`, both ways through LibreOffice). Emulator: a red stroke from B16 to F19. Not done: drawing on it again (Words'
      redraw from the kept strokes) — later, when asked.
- [x] **From Words**: AutoSave, Save a copy, Save as, Rename; Find and Replace (above); Format painter (above); the
      keyboard bar while typing in a cell.
      Keyboard bar done 18 Oct (office-F): Words' KeyboardBar and rememberDictation while a cell is typed in, on tablet and
      phone (Tables hides its ribbon while typing on both); Bold, Italic, Underline and the highlighter (the fill) on the chosen
      cells, voice typing in at the cursor, ⋯ puts the typing in and brings the ribbon back. Emulator: "hello" bold on yellow.
      Done (office-F session, 18 Oct): File › Save a copy ("<name> (copy)" beside it), AutoSave (Words' own
      setting, shared: a few seconds after a change, never while a cell is being typed in), Save as (the new file open from then
      on), Rename (its versions and draft with it). Emulator: each on a copy of sheet.xlsx. Note for the user: AutoSave is Words'
      setting, so with it on (as on this emulator) Tables now saves by itself too — the File tab's AutoSave turns it off for both.
      Fixed 18 Oct (office-F): a save took Undo away (the file was read again), so with AutoSave on Undo went four seconds after
      each change. Now a save keeps what is open and Undo, as Word's: the edits are written onto a copy of the file as opened
      (kept in the working folder), and whole-file steps never start from the file itself. Emulator: a cell typed and a row
      inserted, each autosaved, both undone, saved again — the file as it was.

### D. Words
Its own open items stay in THE PLAN's Phase 2 (HANDOFF.md). Anything the UI/UX pass finds in Words goes into A.


## Calendar — the last app (designed 17 Oct, built 18 Oct on the `calendar` branches; the real pair still to do)

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
  An AI deletes with `mv`, not `rm` — an `rm`'d file came back from the other device until 23 Oct (SYNC_PLAN 6an:
  now the other devices put theirs in their Trash, but nothing is left here to restore).
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
- [x] (a308980) 1. Android: the files — `Calendar.kt` read/write/expand (lib-recur) + fixtures (Google's, Claude-style,
      repeats), `CalendarTest`; `Calendar` a required folder with its emblem.
- [x] (242c094) 2. Android: the screens — big screen month/week/day/agenda + side panel; phone week row (pulled down: the
      month) + agenda, Day, 3 days, Month; Home's pink group (the user's icon, the next event), launcher icon.
- [x] (4cc9368) 3. Android: create (Create, +, an empty hour, Home's New event), edit, delete; calendars (new, colour,
      rename, delete, show/hide); repeats (presets + Custom) with This / Following / All; Keep this one for a conflict.
      **Not done: drag an event to move or resize it on the tablet** (tap and edit works).
- [x] (4c43b7a) 4. Android: reminders — one exact alarm (USE_EXACT_ALARM) for the next, re-planned from the files on open,
      edit, a file landing, after every sync (SyncClient), boot, clock/zone change, update; missed ones up to a day;
      Snooze 10 min; notifications asked once; Xiaomi note (Autostart, battery). Emulator: rang at the second, Snooze
      re-rang, the alarm came back after a reboot.
- [x] (4c43b7a) 5. Android: import .ics / Takeout zip (side panel, or "Open with › Tetra Calendar"), export one .ics
      (VTIMEZONEs written) or all as a zip.
- [x] (7584567) 6. `skills/tetra-calendar` (SKILL.md + calendar.py: list, agenda, find, add, new-calendar, trash, check),
      installed in ~/.claude/skills; its agenda gives the app's days on the fixtures.
- [x] (Drive c2ab218, ea3e221) 7. Desktop: `calendar.js` (own engine, the phone's fixtures and answers in
      test/calendar.test.js), `Calendar.tsx` (Google web layout, editor, card, import/export), fs.watch, reminders via
      notify(), "Reminders on this computer", "Start Tetra when I log in" (autostart, --hidden); Calendar a required
      folder in Files. Not done: a VTIMEZONE written for a zone the files only name (the phone writes them).
- [ ] 8. The real pair (waits for the user and the tablet): merge `calendar` into writer-library/main (Android) and
      bidirectional-sync (Drive), install on the tablet and the PC; an event made on each device and by Claude, edited on
      two at once (conflict), deleted, all three agree; a reminder rings on the tablet with the app closed and after a
      reboot. Then the user's own calendar: Google Takeout imported.
- Later, if wanted: drag to move/resize; multi-day events as one bar (Google's) instead of a chip per day.

### Tetra's island instead of Google's panel (the user, 18 Oct, relayed by the main session)
"After the calendar app is finished try to bring it closer to the UX with an island and bottom drawer that opens for
the rest like what calendars to see and what not. The same for the big screen not hamburger menu an island that is
close to the UX we are using to the rest of the Tetra app." And: "The calendar will go to desktop as well. So it can
sync and you to be able to work with it with a skill just by editing files like normal."
- [x] (0ed5b56) Phone and tablet: no hamburger, no side panel. Tetra's bottom island (Files' and
      Notes' `IslandBottomBar` / `IslandNavigationItem`): the views (phone: Agenda, Day, Month; tablet: Day, Week, Month,
      Agenda) and New; pulled up, the drawer (the same `ModalBottomSheet` as Notes' and Files'): the calendars with their
      coloured boxes (show/hide, ⋯ colour/name/export/delete), New calendar, Import, Export all, the small month on a
      big screen, 3 days on a phone, Reminders on this device. The top keeps Today, ‹ › and the month's name.
- [x] (Drive 7b9115c, 218f4ea) Desktop the same: Notes' bottom island (Day, Week, Month, Agenda, New event, Calendars)
      and its drawer for the rest, not the side panel; Drive's HANDOFF.md says where it is. The desktop part is already files only (no database copy): it reads Files/Calendar on every
      look and fs.watch shows any edit (Claude's, the skill's, sync's) at once; the skill's paths (~/Tetra/Files/Calendar)
      are what both apps read and write. Checked 18 Oct: a file made by the skill's calendar.py is read by the desktop with
      the same days and reminder times (and by the phone: the same fixtures and answers in both test suites).
- [x] (534e341, bc0f3ff; Drive 7736b99, 0559fd0) Closer to the rest of Tetra (the user via the main session, 18 Oct, after
      0ed5b56 on the emulator): Files' top (Back and the month in islands, Today ‹ › beside them; the desktop's
      title-island); Files' bottom (the island at the left with the current view labelled, 3 days on the phone's island,
      New event the round island at the right); the drawer pads for the navigation bar; overlapping events readable as
      Google lays them out (starts within 15 min side by side, later ones cascade on top indented, under 30 min a chip).
- [x] The user's own Google calendar (~/Λήψεις/…ical.zip, kept out of both repos and out of ~/Tetra): 393 events in on the
      emulator through Open with › Tetra Calendar, the desktop engine and the phone engine (CalendarRealImportTest, run
      only with TETRA_CALENDAR_ZIP); out again, all 397 entries line for line equal; imported twice, no doubles; the phone,
      the desktop and the skill agree on all 1639 occurrences and 758 reminder times (2015–2030). It found a bug, fixed:
      two events of one title whose Google UIDs begin alike got one file name, one lost; names now use the UID's hash and
      never take another's. The skill's helper is now tetra_calendar.py (calendar.py hid Python's own module).

### Wide and narrow, and kinds of event (the user, 18 Oct, relayed by the main session; overrides part of the island ask)
"On a wide screen dont hide the calendar or the opitions as it was then the island it would hide only the setting day
week month year. On narow screen keep the calendar at the top I like to see you now a week or two or three or month and
be able to choose." Then: "Alltho to be closer to our thing we could bring calendar to the bottom and ajenta to the top".
And for Home: "On the default buttons at home add New : Shedule : burthday : reminder."
- [x] (0cdba78, 1eb85cf; Drive 050cff8) **Wide** (tablet landscape, desktop): the small month and the calendars (show/hide,
      New calendar, Import, Export) always beside the grid; the island only Day · Week · Month · Year (a new Year view)
      and New event; the drawer only what is left (the reminders switch). The top in Tetra's pill style.
- [x] **Narrow** (phone, tablet portrait, a narrow desktop window): the events on top; a small calendar at the bottom,
      just above the island — 1, 2 or 3 weeks or the month, its handle dragged up for more and down for fewer (kept);
      a day tapped there moves the events to it. The island and drawer as before. Same at the same widths on the PC.
- [x] **Kinds**: Home's Calendar card gets four tiles as Office's — New, Schedule, Birthday, Reminder — and + in the app
      offers the same. Birthday: a Birthdays calendar (made if missing), all day, yearly, name and date, the year
      optional (kept as X-TETRA-BIRTH-YEAR so the age shows). Schedule: chosen weekdays, a time, a start and end — one
      event `RRULE:FREQ=WEEKLY;BYDAY=…;UNTIL=…`; templates "Elementary school year" (1 Sep – 26 Jun, the user's dates)
      and "High school year" (1 Sep – 15 Jun: **the end date needs the user's confirmation**), both editable; "Skip the
      Holidays calendar's days" adds an EXDATE for each. Reminder: one event with a list of notifications. All plain
      .ics the tetra-calendar skill reads and writes.
- [x] (1eb85cf, 37a2c86, f7ccd75) The user on the tablet, 18 Oct: the top floats over the content as in Files; reminders
      in the Hyper Island with a ✕ Done (pink in a black circle with a pink rim) and Snooze — ongoing, as Xiaomi's Clock
      timer; seen on the tablet (HyperOS raises no island for the app in front, so Settings › Calendar's test reminder
      comes 5 s later); ✕ marks that time done and its other reminders do not ring. Settings › Calendar: notifications,
      alarms on time, battery, Hyper Island, Autostart, the reminders switch, a test reminder, Import, Export all.
      Desktop the same at the same widths (Drive branch `calendar`, 18 Oct).
- [x] (1f00114; Drive 756d805) **Share with Claude** (the user, 18 Oct: "Add a share with Claude in the settings of the
      calendar app for the user to download the skill"): skills/tetra-calendar the one source, zipped into the Android app
      at build time (assets/tetra-calendar.zip) and copied into desktop/skills before a desktop build. Android, in the
      drawer and Settings › Calendar: Share (the share sheet) and Save to Files. Desktop, in the Settings drawer: Save as…
      (~/Downloads) and Copy to ~/.claude/skills. "Lets Claude read and change this calendar by editing its files."
- [x] (322fe65; Drive bc47403) Wide screens (the user, 18 Oct: "The today and the arrows for the tablet place them at the
      bottom between the + and the island on its own island", then "Closer to the + button to be easily reachable with
      the thum"): the top keeps the title; the views' island at the left, ‹ Today › in its own island beside the + at the
      right; the grid stops above them. Phone and narrow windows unchanged (no room at the bottom beside the week strip).
- [x] (7ed20a5; Drive 2cf2fa2; SYNC_PLAN 6am) Calendar changes travel at once (the user, 18 Oct: "once there is an update
      on calendar like on notes send it imediatly across devices as soon as connection available"): a Calendar-only sync
      on Files' own path (`/files/manifest/scoped`, scope `calendar`), 2.5 s after a change, waiting for a network as a
      job; the computer's changes (the skill too) by its nudge, or a quarter-hourly look with Tetra closed. Reusable for
      the Timer (its own scope). **Not yet run between real devices** (the emulator cannot scan the pairing code).
- [x] (46a6309; Drive 83c4e50) **Refresh, Trash and Settings at the top; no delay after a change** (the user, 18 Oct):
      Refresh beside the title (tablet/desktop), the Trash beside it (phone: in the drawer) with each event's days left
      and Restore; desktop Settings is Gallery's tune menu with Share with Claude. Saves push at once (desktop: 300 ms).
      **Run on the real pair** (tablet ↔ installed PC Tetra, older, so the whole sync): Thursday/Friday lessons arrived
      7 s after Refresh; the user's new event reached the PC in seconds, its deletion was in the PC's Trash 3 s later and
      stayed gone. The phone's hotspot first blocked device-to-device traffic (restarting it fixed that). New .deb:
      Drive-worktrees/calendar/desktop/release/local-drive-desktop_0.3.0-beta.4_amd64.deb (not installed).

- [x] (Drive ca93bdb; 2f3c21e) **Clock and month pickers on the desktop; a repeat's cancelled days follow its time**
      (the user, 18 Oct: ΤΠΕ Β' at 11:50–12:25 snapped to 11:45–12:20, "why a list and not a clock", "why is the date not
      a calendar"): every time field types any minute ("12:25", "1225", "925") and opens a clock as the phone's (hours
      00–11 outside, 12–23 inside, then minutes); every date field opens a month (‹ ›, the year list for birthdays,
      Today). Moving a repeat's start moves its EXDATEs and RECURRENCE-IDs by as much, desktop and Android (a test in
      each). Checked in the desktop source build against the real library, nothing saved; installed Tetra restarted.
      Not done: the user's own ΤΠΕ Β' file (its EXDATEs are still at 11:50) — the main session asks the user; "this and
      following" still drops the cancelled days after the split (both apps).

- [x] (73b2354; Drive 7111221) **"This and following" keeps the cancelled and changed days after the split**:
      the new event gets the old series' EXDATEs and RECURRENCE-ID days from the split day on, moved by as much as that
      day moved (shiftRepeat), desktop and Android (a test in each, the staff-meeting fixture). Both suites pass; not
      tried in the apps by hand.

- [x] (maps-link session, 21 Oct) **A place opens in the maps app** (the user: "when I add a location link … from Google
      maps here maps or what ever … make it a button"): the event card shows the place without its link and an "Open in
      Maps" button — a link with coordinates (Google's @lat,lng / q=, HERE's map=, OSM's #map=) or a plain address goes as
      geo: to the default maps app, a short link (maps.app.goo.gl) opens as itself; web links in the notes are tappable.
      `CalendarPlace` + CalendarPlaceTest; seen on the emulator (opened Google Maps at the link's point). Android only —
      the desktop's card not yet; not on the phone/tablet yet (neither connected).


## Timer (18 Oct, timer session; branches `timer` in both repos, worktrees Drive-Android-worktrees/timer-b50bfc and Drive-worktrees/timer)

**The user, 18 Oct:** "start it a simple timer with the clock widget like the calendar to select time and some recently used
times below and the ability to pin some of them. Active notification on the hyper island with pause and stop buttons. Sync
check every 30 sec. I mean as long as they start and stop together no real sync is needed. Just stopping the one device or
clicking ok stops all of them." Then, while it was built: "If I click stop or pause you sync that moment, so it controls all
the devices"; "we cant write every second to memory"; "at the timers card to the right side add 6m 15m 20m 30m options to
imediatly start without even opening the UI"; "can we hide the folders of the calendar and the timer on PC and Android";
"make the cards smaller on the desktop … 3 columns that get fewer when the window resizes"; "add a trash can to the top right
corner of the timer card and a pin at the top left. The UI will fit 9 timers"; "remove the recently used column … thats it a
clock with hands and the timer with play pause stop"; a sketch: the clock, HH and MM under it, Pause and Stop under those, a
line, the cards beside — "play is not needed as soon as I set a timer starts"; "why do we need an island on this applet?"

- [x] (timer session, 18 Oct) built on both sides; checked on emulator-5554 (tablet size and phone size) and the desktop in a
      hidden window (a scratch HOME); not on the user's tablet or phone, not between real devices.

### What it is
- **The screen**: the Calendar's clock (hours on the outer and inner ring, then minutes); **lifting the finger from the minutes
  starts the timer** (desktop: the minute hand let go, or Enter in a box); Pause/Resume and Stop under it act on the timer
  last started there (else the one ending first). The timers' cards beside it (desktop and tablet: three to a row, fewer as
  the window narrows; phone: two, above the clock), **nine at most**. Each card: the time left, Pause/Resume and Stop (OK when
  it rings); **a pin at the top left** keeps the card when it is stopped or done, ready to start again (▶ Start); **a trash at
  the top right** takes it away (a running one stops). No island, no drawer, no recently used list (all dropped by the user).
- **Home (Android)**: the Timer card with **6 · 15 · 20 · 30 min**, each starting at a tap, Tetra staying on Home.
- **Running**: Android — an ongoing notification per timer (Android's own countdown) with Pause/Resume + Stop, on a Xiaomi
  the **Hyper Island** counting down by itself with the same buttons (`HyperIsland.timer`: `sameWidthDigitInfo.timerInfo`,
  timerType −1 to the end, as the open-source HyperIsland ToolKit writes it); the end is an alarm clock (`setAlarmClock`, on
  time in Doze), then the alarm sound (USAGE_ALARM) and **OK**. Desktop — the system notification and a "Time's up · OK"
  bar with beeps on any page.
- **Folders hidden**: Calendar and Timer are not shown in Files' lists, search or Move on either side; they sync as before.

### The files (plain JSON, one per timer and device — Claude reads or starts a timer by writing one)
```
Tetra/Timer/                     (~/Tetra/Files/Timer on the PC, /sdcard/Tetra/Timer on a phone)
  k3f9a2.24091RPADG.json   {"id":"k3f9a2","seconds":300,"label":"","state":"running","ends":"2026-10-18T16:05:00Z","left":0,"rev":1,"by":"24091RPADG","pinned":false}
  k3f9a2.theologos-82xm.json   {"id":"k3f9a2",…,"state":"stopped","rev":2,"by":"theologos-82xm",…}   ← OK on the computer
```
- `state`: `running` (counts down to `ends`, an ISO instant) · `paused` (`left` seconds remain, `ends` null) · `stopped`.
  `pinned`: the card stays when stopped or done.
- **Written only at a change** — start, pause, resume, stop, OK, pin, delete: a handful of writes in a timer's life, never per
  second. Each device works out the time left from `ends` and its own clock, in memory.
- `rev` +1 at each change; the highest wins, equal ones stopped > paused > running (a pause and a stop at once: stopped). So
  clocks a few seconds apart decide nothing; they only show the time left a few seconds apart.
- **Each device writes only its own file** of a timer, `<id>.<device>.json` (the device = `by`, letters, digits, - and _ kept);
  all the files of an `id` are one timer, the highest `rev` among them wins. So two devices never change the same file and
  sync never keeps two versions side by side. (Until 18 Oct evening every device wrote the timer's one file; an OK on the PC
  and a start on the phone at once made `x (3) (3).json`, `x (3) (4).json`… — copies like these still read as the timer.)
- A new timer takes the `id` of one that is over and not pinned (`rev` + 1): the folder holds about as many files per device
  as there were timers at once. Ran out and nobody tapped OK: it rings 10 minutes at most, then is over.
- **Claude starts one** by writing `{"seconds":600,"state":"running","ends":"<now + 10 min, UTC>"}` as any `<name>.json` (the
  file name is the id when there is none); **stops one** by writing `<id>.json` with `"state":"stopped"` and `rev` one
  higher than the highest of its files.
- The clock opens on the length last started on that device (a local setting, not synced).

### How the devices keep in step
- A change is pushed at once: Android `ScopedSync.soon("timer", 0)` — Files sync's own requests for the part `timer`
  (DriveScope; desktop files.js SCOPES), as the Calendar's (SYNC_PLAN 6am); the computer's watcher on Files/Timer nudges every
  paired device (their SyncServer `/sync` → their own `timer` part at once). So a stop on the tablet reaches the phone through
  the computer within seconds.
- Fallback: a look every 30 s while a timer exists (Android TimerService; the desktop re-reads every 30 s); with Tetra closed
  on a phone, the Calendar's quarter-hourly look carries the Timer's part too. While ringing, a phone looks at once.
- Code: Android `Timer.kt` (record, files), `TimerService.kt` (TimerControl, the service, notifications, alarm),
  `TimerScreen.kt`, `HyperIsland.timer`, Home's card in MainActivity; desktop `timer.js`, `src/Timer.tsx` (the Calendar's
  `ClockFace` with `below`/`onDone`), main.js `timer:*`. Tests: Android `TimerTest` (9), desktop `test/timer.test.js` (9):
  time left, pause/resume, a stop/OK reaching the other device, two changes at once, clocks 3 s apart, a free file reused,
  pin and trash across devices, nine at most, a hand-written file.

### Left
- [ ] The real pair: start on the tablet, pause on the PC, OK on the phone — on the user's tablet (971f6b37) once the main
      session says it is free; the island's look there (the emulator has no Hyper Island). The user: the sound works on the
      tablet with sync ("it works on sync with the tablet").
- [x] (18 Oct evening) "when I click ok on finished timer the other devices keep ringing": OK on the PC reached the phone in
      3 s (it stopped at 17:06:45) but never the tablet, which had lost the network after the reinstall (the PC heard nothing
      from it after 17:04:58; restarting the hotspot fixed it) — nothing can reach a device that is off the network. Found
      on the way: every change of a timer made sync copies of its one file; now each device writes its own file (above).
- [x] (19 Oct, the user: "We did not make a share with claude skill setting on Android or Desktop for the timer, we also did
      not add the timer to the togles for the individual apps for apearing in the launcher") A **tetra-timer skill**
      (skills/tetra-timer, zipped into the APK and copied into desktop/skills as the Calendar's); **Settings › Timer › Share
      with Claude** on Android (Share / Save to Files) and in the desktop Timer's own ⚙ at the top right, as the Calendar's (Save as… / Copy to ~/.claude/skills);
      **Timer in Settings › App icons** (Android; `.AppTimer`, its icon by hand from the vector glyph on Timer teal). The
      desktop has no launcher toggles for any part.
- [ ] Seen: Tetra already open does not turn to a part asked for by `adb am start -e …APPLET` (the Calendar's too); a
      notification's tap does. Not looked into.
- [ ] (18 Oct, the user: "Let be for now. write it down") **The tablet drops off the network after each reinstall** of
      Tetra: the PC stops hearing from it (17:04:58 that day) until the hotspot is restarted, so no stop, OK or change can
      reach it. To look into on the tablet itself (971f6b37) — the pairing/connection after an `adb install -r`.
- [ ] (same) **A 10 h 20 min timer started by itself** on the phone at 17:08:40 — most likely a stray touch on the clock while
      setting the minutes, since a timer starts the moment the finger lifts from the minutes. If it happens again: a Start
      button (or a confirm for long times) instead of starting on lift.
- [ ] (same) The fix above (72e9d0b / Drive b2e9561, pushed) is **not yet on the devices**: the PC's .deb is built
      (Drive/desktop/release/local-drive-desktop_0.6.0-beta.1_amd64.deb, the user installs it with sudo dpkg -i); the phone
      and tablet get the debug APK when connected. No GitHub release of 0.6.0 Beta 1 yet.

## Maps — dropped 20 Oct, restarted 21 Oct on branch `maps` (the user: "I changed my mind start buidlding the map applet")
The user, 21 Oct: "Have in mind that we may transfer this map to the desktop as well" — so the files stay plain and
shared: greece.pmtiles opens in MapLibre GL JS (pmtiles protocol) on the desktop as in MapLibre Android; routing and
search files built so both can read them. The size worry stands: the map is a file in Tetra/Maps, not in the APK.
The user, 20 Oct: "Greece as a map, offline, Map managment, Navigation, Turn nav voice, Import from Google Maps, Save loations
with SVGs and names, A home tab doeble the size, with slots for 6 locations". Decided 20 Oct: the **computer builds** the map
files and Tetra's sync carries them to the phone and tablet; routing for **car, walking and bike**.

Pieces (all free, no keys): the map is **Protomaps** (a Greece cut of their daily OSM build, one `.pmtiles` file, opened by the
MapLibre the app already has; style "light"/"dark" with Greek labels, fonts and sprites in assets/maps); routing **GraphHopper**
offline on the phone (Greek instructions); voice Android's **TextToSpeech** (el-GR); search an offline index of names/addresses
from Geofabrik's Greece OSM extract (accent-blind); icons **Maki** (CC0) + the user's own SVGs; import **Google Takeout**
(starred places have coordinates; list entries are name + link, found through the search, misses shown to fix).
Files: Tetra/Maps (hidden in Files like Calendar and Timer, synced): `greece.pmtiles` now; routing and search files later; saved
places as GeoJSON. License note: GraphHopper and AndroidSVG are Apache-2.0 beside the repo's GPL-2.0 (as other libraries already).

- [~] (maps-link session, 21 Oct) 1. Greece offline map (512 MB at zoom 15, region polygon), my location, map management (packages,
      size, date, delete to Trash); `tools/maps-greece.sh` makes the file on the computer.
- [x] (maps-link session, 21 Oct) 2. Saved places (name, emblem, lists) as files; Home's Maps group double size with place slots; Google Takeout import.
- [x] (maps-link session, 21 Oct) 3. Offline search, the bottom island, places' pages, lists (the user, 21 Oct: "an Island
      at the bottom with navigation,favorites, search … make lists with names and emojes … nearby lcations in an area like
      Google Maps … restorants and pharmases and supermarkets or petrol stations or charging stations add disability parking
      … button pills"; "If I click touch on a place there is no info page"; "To click on a spot show the address the number
      the postcode"; "the EOpYY farmacies … as a list as well as a pill"; schools "from kindengarden to Highschools"):
      tools/maps-places.py makes greece-places.tsv (122 834 towns and places of 22 kinds, with hours, phone, website,
      wheelchair) and greece-addresses.tsv (168 778 addresses); tools/maps-eopyy.py takes EOPYY's 39 pharmacies from
      eopyy.gov.gr (with their map positions). Search: words (accent-blind, Greek or English names) or a pill, nearest the
      map's middle first, as dots on the map; the user's accessible places count as disabled parking. Saved: all places by
      emblem, lists with a name and an emoji (long-press to change), EOPYY's list built in. Directions: Car/Walk(Roll)/Bike,
      "Where to?", Home's places and the car. A tap on the map: my place, a result, a place of the map's own (its page from
      the list), or a dropped pin with the nearest address; the page has the address, hours, phone, website, wheelchair,
      directions, Save (with lists), and Google Maps online for reviews and photos. Top left the road I am on with its code
      (EO1, A2), top right the next turn in a small window. Home's card 12 slots on a tablet (6 a row), 6 on a phone.
      Google Takeout import in Settings › Maps (lists' CSVs and starred places; unplaced ones listed). Not done: sch.gr's
      school register (its terms forbid reuse without permission; OSM's schools only, thin in places: 9 in Xanthi); Google
      Maps' data is not copied.
- [ ] 4. Routing (car, foot, bike), turn by turn, voice, the next turn in HyperIsland; reroute offline.
- [x] (22 Oct night) **Maps on desktop Tetra** (Drive desktop 0.6.0-beta.3, maps.js / router.js / src/Maps.tsx): the same files in
      Files/Maps, read and written as the phone writes them — the offline map (greece.pmtiles served as tiles through media://maps,
      the phone's light style, fonts and sprites), Search with the phone's pills (accent-blind, Greeklish, a kind in a named
      town, what is in view on Enter), a place's card (address, hours, phone, website, wheelchair, Nearby, Google Maps), saved
      places with the phone's emblems, Home slots 1–12 and lists, trips planned on the computer (A, B, C…) for the phone to drive,
      and directions by road on the computer: GraphHopper 10.2's server (Java 17 + ~/Tetra-maps/bin/graphhopper-web-10.2.jar)
      on the phone's graph, memory-mapped read-only through a folder of links in the app's data, so no lock file reaches the
      synced greece-gh. Each leg's km and time (car × 1.2, as the phone), Car / Walk / Bike. Maps hidden in Files' lists.
- [ ] Later: the desktop app builds and refreshes the files monthly by itself; a `tetra-maps` skill; desktop directions
      without the computer's Java (a bundled runtime, ~50 MB) if another computer is to route.
- [x] (Drive 4e2ae56 + the Android commit beside this line; phone-location session, 23 Oct) Desktop Maps asks the phone where it is (the user, 21 Oct: "when I click the corent location or a rute is calculated it
      will ask the phone while showing Waiting phone for location … from the internet it will be off by half a county"):
      the computer sends a location request over the paired connection, the phone answers with one GPS fix (asking for the
      location permission if it has none); the desktop shows "Waiting for the phone's location…" meanwhile, and says so
      when the phone is not reachable. Built with desktop Maps.
      Built (SYNC_PLAN 6am): desktop Maps' where-I-am button (over the zoom buttons) and "Start from where I am" in a trip
      ask the paired phone (`POST /location`, phones before a tablet); the phone (PhoneLocation.kt) answers one fix — GPS,
      the network's only when GPS found nothing — asking for the permission on its screen if needed; in the background it
      posts "The computer asks where this phone is", and one tap sends it. The fix: a blue dot and a card, "Start a trip
      here (A)". Tried on the emulator against the desktop's own code (permission asked, fix in 8 s; background + tap,
      8.7 s); the desktop seen hidden with a stand-in phone. Not yet between the user's real phone and the installed
      /opt/Tetra (needs both new builds). Android freezes a backgrounded Tetra within about a minute: then only opening
      it on the phone helps, and the computer says so.
      On the user's phone (23 Oct, debug build, the new desktop code from source with the phone's real pairing): Tetra open,
      a GPS fix ±20 m in 4.3 s over Wi-Fi. Tetra in the background (5 s or 35 s after Home): no answer at all — the
      phone's network policy shows Tetra `blocked=APP_BACKGROUND`, so the computer says "No phone answered. Open Tetra on
      the phone". The notification path only works while something keeps Tetra out of the cached state (a running timer's
      service), and on the emulator.
- [x] (the commit beside this line, 23 Oct) Desktop Maps' where-I-am with the phone in a pocket: Android cuts a backgrounded Tetra off the network within ~5 s
      (above), so the phone can answer only while Tetra is open. Needs a decision from the user: Tetra on the phone keeps
      listening in the background (a foreground service with its own small notification while paired with a computer;
      it would also let the computer's "sync now" reach a pocketed phone), or it stays "open Tetra to answer" as now.
      Not a test: a design change (SyncServer's "nothing listens once the app is closed" rule, SYNC_PLAN 6i).
      The user, 23 Oct: "we had such a rule ok change it". Built: SyncListenService, a foreground service while a computer
      is paired (started when Tetra opens, after pairing, after a restart or an update; Settings › Sync › Listen in the
      background turns it off, as does Stop on its notification, which is the quietest kind: only Android's services
      indicator). Started from the open app with the location allowed it also holds the location right, so the phone
      answers without a tap. On the user's phone: Tetra closed 40 s, a GPS fix ±7 m in 7.5 s; the user saw only the
      services indicator, and the location pointer when the computer asked. Phone locked (screen off): ±10 m in 5.8 s.
      Not tried: deep Doze (the phone was on USB, so charging, which keeps Doze away) and a day's battery; Tetra is
      locked in MIUI's recents on the user's phone, so the battery saver should not stop it there.
- [x] (maps-link session, 21 Oct) Nearby (the user: "πατάω αναζήτηση πατάω πύργο και βγάζει την καρτέλα … βάλε και ένα κουμπί
      Nearby και βγάλε σημεία ενιδαφέροντος"): a place's card › Nearby opens Search around it ("Near Πύργος"), nearest first,
      distances from it, the pills narrowing it. Search's panel now rides above the keyboard, and says it is loading.
      The user's work schools put in by hand (Places/sch-*.json, list Work 💼, Home slots 1–5): Πύργος and Σκάλα at the
      village's middle (no school in OSM there), the 2nd of Κρεώνα at the user's link, the 1st
      of Κρεώνα (not a work one) at the village's middle.
- [x] (maps-link session, 21 Oct) Motorways (the user: "έκανες κανένα τέστ σε εγνατία οδό … σε 3χμ πάρτη την έξοδο στα δεξιά";
      "Κάνε και Σχιματάρι Ναυπλιο"): tried Καβάλα → Ξάνθη and Σχηματάρι → Ναύπλιο on the graph. The exits say "μείνετε
      δεξιά … προς Εύλαλο, Τοξότες" (OSM's destination signs) and "πάρτε τον δρόμο Α7 προς Τρίπολη"; a turn onto a nameless
      ramp now says where it leads ("στρίψτε αριστερά προς Α8 Αθήνα - Πάτρα", from the path's street_name/ref); the spoken
      warnings follow the speed (dueWarning: 2 km ahead over 80 km/h, ~20 s ahead, ~5 s ahead; "Σε 2 χιλιόμετρα, …").
      Not there: lanes (below), exit numbers, the word "έξοδος", tolls. GraphHopper chose the Θηβών–Ελευσίνας road over the
      Αθηνών–Λαμίας + Αττική Οδός for Σχηματάρι → Ναύπλιο (shorter; to be judged by the user on the road).
- [x] (maps-link session, 21 Oct) Ways to choose (the user, beside Google Maps on the tablet: "can our map have alternative
      routs with tolls or without, main roads over roral roads"): a place's Car / Walk / Bike shows up to three routes
      (GraphHopper's alternative_route on the CH graph), fastest first, each with its time, length and roads (Motorway / Main
      roads / Local roads, by share of length; tracks dropped for a car); Start goes by the chosen one; Home's one tap still
      starts the fastest at once. Άγιος Λουκάς → 2ο Κρεώνα: 23 min motorway / 25 min local roads (Google: 30 tolls / 31 none).
      Also: the speed limit as its sign bottom left, red over it by 3 km/h (OSM maxspeed in the graph, 71–89 % of the roads
      tried); the ferry said as a ferry; the user's schools as system places (added-places.tsv, drawn on the map, in Search)
      with Home names (Place.label); 8 half-size Home slots on a phone; P and where-I-am at the bottom right (the compass);
      Greeklish search; Google Takeout in Greek (Αποθηκευμένα, Χάρτες (τα μέρη σας), Μέρη με ετικέτα), streamed, links'
      ?q= positions, addresses looked up in the address list in the right town: the user's first Takeout part gave 53 of 65.
- [x] (maps-link session, 21 Oct) More of the user's asks the same evening: a narrow screen's one pill (the road over the next
      turn), no Back in directions; toll stations (OSM's 255 barrier=toll_booth, with operator and price where tagged) as
      € dots on the route, counted on each route to choose ("💶 n", "No tolls" only for local roads: OSM misses some ramp
      tolls, as Νέα Οδός' near Σχηματάρι), and under a handle on the directions bar with the turns ahead; a kind with a town's
      name searched in that town ("agios loukas" + Supermarkets); mini markets under Supermarkets; the cities' own ΑμεΑ
      spots from data.gov.gr (tools/maps-amea.py: 740 — Θεσσαλονίκη, Χανιά, Παπάγου-Χολαργός, Αθήνα, Χαλάνδρι, Νάξος; Λάρισα's
      and Ηράκλειο's only as shapefile/document, not read); "Add to the map" (a long press: My place / On the map with its
      kind, added-places.tsv, removable from its card); Export / Import as GeoJSON (my places, map additions or both).
- [x] (maps-link session, 21 Oct) Tunnels (the user: "What if I loose GPS sygnal on a tannel"): 3 s without a GPS fix in
      directions at speed, the arrow goes on along the route at the last speed (≤10 km), "No GPS signal" shown; a cell fix
      does not stand for the GPS for 2 min after it; the first GPS fix takes over. Tried on the emulator with location off
      for 8 s at 107 km/h: it went on and said the next turn, then took the real fix back. Then (the user: "μπορεί για 5
      λεπτά να μην έχω GPS … να χρησιμοποιεί τους αισθητήρες"): up to 15 min or the route's end (no 10 km cap); the
      accelerometer (StillDetector: the spread of linear acceleration over 2 s) holds the arrow while the car stands, and
      after a stop it goes on at 30 km/h (or less). Emulator, sensors set by hand: on, held, on again. Not tried in a tunnel.
      Markers (the user: "my personal keep our style what I save as public info gets the maps own style"): search results
      and map additions in the map's own icon style (its sprite for restaurant, café, supermarket, school…, the rest drawn
      as it draws them); my places keep Maps' indigo circle with their emblem. A tavern emblem.
      ATMs (the user: "Can we find a map of all the ATMs in greece"): no open per-ATM list found (the banks' union publishes
      counts only, ~6 000); OSM has 3 405, all in Search's ATMs pill.
- [x] (maps-link session, 21 Oct) Search's OK (the user: "να πατάω απλά ρεστοράντ και να πατάω οκ και να βγάζει τα ρεστοράντ σε
      αυτό το κομάτι που βλέπω … ή και λίγο μεγαλήτερο"): a pill or words, then OK (or the keyboard's search key): up to 300
      of them on the screen and half a screen round it stay on the map, the panel goes, a "🍽️ Restaurants · n ✕" pill clears
      them; none there: the nearest 20, the map moved to them.
- [x] (maps-link session, 21 Oct) ΑμεΑ spots by the user himself (none in any data for Καβάλα or Ξάνθη): after parking (P or
      a car trip's end) "♿ ΑμεΑ spot? Add it" for 20 s puts one at the car's GPS spot; a long press's dialog has "♿ ΑμεΑ
      parking here" at the top, one tap. Not done: looking for them by eye in Google's imagery (its terms forbid taking data
      from it; its satellite pictures too coarse with cars on the spots, 21 Oct). Mapillary's street photos (CC-BY-SA) and
      their detected signs could give them lawfully; needs a Mapillary token (the user's account).
- [x] (maps-link session, 21 Oct night) ΑμεΑ spots from Διαύγεια, all of Greece (tools/maps-diavgeia.py; the user: "Continue the
      same way for all over Greece add what are you are serten of and I will check (give links) the rest"): 6 061 decisions
      2012–2026; 5 740 left out (a spot given to one person or car — most of them —, requests, abolitions, regions' reviews);
      2 placed for sure by address (greece-amea-diavgeia.tsv: Βόλος, Ξάνθη); 223 public ones to place by hand
      (greece-amea-check.tsv: Ξάνθη 49, Βόλος 37, Μυτιλήνη 19, Ρέθυμνο 13, Πειραιάς 12 …), 129 of them at their street
      (greece-streets.tsv, 212 167 named road pieces), 94 at the town. In the app: Saved › "♿ ΑμεΑ to place (n)", nearest
      first; a tap shows the street and the decision; a long press places it (a map addition with the decision's link),
      "Not there" drops it; amea-checked.txt keeps the done ones on every device. OSM has too few house numbers to do more.
      Also: favourites' markers at 22 dp (were 28); car times ×1.2 (Άγιος Λουκάς → Χαλκίδα 30 min); routing errors in
      plain words; map additions shown at once (a bug: they waited for a reopen); an edge-case route battery (MapsEdgeCaseTest:
      ferries, bridges, islands, mountains, one-ways, outside Greece, at sea — no crash, sensible times). The user's Takeout:
      18 favourites given their emblem (beaches, cafés, parkings, doctors…), 4 public additions (2 ΑμεΑ spots in Ναύπλιο,
      Παραλία Δρεπάνου, River Cafe, a parking in Ξάνθη) — waiting for a device on USB (scratchpad emblems/, public-additions.tsv).
- [x] (maps-link session, 22 Oct) More ΑμεΑ sources (the user, from Gemini: OSM/Overpass, the open data portals, open aerial
      imagery): OSM holds ~290 tagged ΑμεΑ spots in all of Greece (parking_space=disabled, capacity:disabled, wheelchair on
      parkings) — the list already had most; data.gov.gr searched with more words: + Κερατσίνι–Δραπετσώνα (173, WFS),
      Ηράκλειο (79, CSV in cp1253 with "35.337.201" numbers), Λάρισα (49 general spots, a shapefile in ΕΓΣΑ87, converted
      in maps-amea.py), Νάξος' signs (1): greece-amea.tsv 740 → 1 031. Aerial: Esri World Imagery (free, allowed for
      mapping) is 0.45 m/px in Ξάνθη — a parking space is 5 pixels, its paint unseen; not a source.
- [ ] Tolls: GraphHopper's toll encoded value is not in the graph; with it (a rebuild) a "no tolls" profile, and the routes
      marked with tolls. The user stopped a rebuild on 21 Oct (distance influence 30 for the car); to be asked again.
- [ ] Lane guidance (the user, 21 Oct: "where there are alot of laines does it tell you show you at the corner what lane
      you should take - keep?"): now only GraphHopper's keep left/right at forks. GraphHopper 10 keeps no lanes, so the
      computer's build reads OSM's turn:lanes tags itself (a pass over greece-latest.osm.pbf beside the graph, by way id),
      and the phone draws the lanes under the next turn, the ones to take lit.
- [ ] Map Update from the computer (the user, 21 Oct: "a map updater in the app settings in the applets section and a map
      managment to downlad or update a map"): Settings › Maps shows what is installed (done, 21 Oct) and how updates come;
      the Update button asks desktop Tetra to run the build (tools/maps-greece.sh's steps) and sync brings the files —
      with desktop Maps. More regions than Greece then too (the same script with another Geofabrik extract).
- [x] (maps-link session, 21 Oct) Parking (the user, 21 Oct: "save park location button, when user stops or and when the
      Android auto turns off … save automaticaly and have the map start from location to that parking spot automaticaly at
      the next open. Option in settings. The parking spot resets at the next car moveon not legs or bike, based on speed or
      car bluetooth"): the map's P button; Settings › Maps › Save where I park (on: the end or Stop of a car trip saves it)
      and Open the map at my car (directions on foot to it at the next open); cleared by a new car trip or 3 fixes over
      40 km/h. One place, Places/parking.json ("My car"), so it syncs and shows like the others.
- [ ] Parking from the car's Bluetooth and Android Auto: saved when the car's Bluetooth (chosen in Settings) or Android
      Auto disconnects, cleared when it connects again. Needs "Allow all the time" location (the app is in the background
      then) and BLUETOOTH_CONNECT; with Android Auto.
- [x] (maps-link session, 21 Oct) Rolling (the user: "diferent modes on the map? Walking/Rolling"; "Add it as an option in
      the settings Walking to be changed the icon to rolling"): Settings › Maps › On foot: Walking / Rolling; Rolling routes
      with the graph's wheelchair profile (maps-graph.yml: foot.json without steps or hike/mtb-rated paths, ≤ 4 km/h) and
      shows the wheelchair icon. A graph from before it falls back to walking.
- [ ] Public transport (the user: "public trasport (can we have that are there info about bases or trains or ships…)"):
      GraphHopper's GTFS module routes on timetables. Open feeds: Athens OASA (buses, trolleys) and STASY (metro, tram);
      Thessaloniki OASTH to check; Hellenic Train and ferries have no open feed (ferries: none known). Offline on the phone
      it needs GraphHopper's PT graph built on the computer like the road one; a big piece, after the road directions.
- [x] (maps-link session, 21 Oct) The map light or dark, apart from the app (the user: "add in the settings a separate
      togle for dark and light theme, because I dont like the dark theme on the map"): Settings › Maps › Map, light by default.
- [~] (maps-link session, 21 Oct) Tetra in light (the user, 21 Oct: "after that we sould check every page and polise the
      light theme of the android app and make it an option light or dark"): Settings › Appearance › Theme Dark / Light
      (TetraLook; dark by default), a light palette (TetraLightColors), status-bar icons that follow it; Home's Scanner/Codes
      cards and tile icons, and the page headers (ModuleHeader) fixed for light. Checked on the emulator in light: Home,
      Files, Notes, Calendar (and its event editor), Timer, Text editor, Settings, Sync, Office's start page, Maps. The
      floating dark-glass islands stay dark in both. Not yet: Office's editors (Words, Tables, Slides, PDF) keep their dark
      frame and ribbon in light — their own colours, a pass of their own; Photos with photos in it; the phone size.
- [~] Android Auto (the user, 21 Oct: "Can it work with Android auto?"; 22 Oct: "its not on the list of the available
      Apps can we add it?"): MapsCar.kt, built 22 Oct — androidx.car.app 1.4 navigation app: the map on the car's surface
      (a MapView in a Presentation on a VirtualDisplay), Places (Home's first, then nearest), Nearby (ΑμεΑ parking, fuel,
      EV, parking, pharmacies, EOPYY, supermarkets, toilets, hospitals), car directions with the phone's guidance and voice
      (ducking the music), parking saved when Android Auto disconnects. Android Auto lists a sideloaded navigation app only
      with its Developer settings › Unknown sources on. Seen 22 Oct on the desktop head unit through the user's phone
      (Start head unit server, adb forward tcp:5277; DHU needs libc++1 and an open stdin), full screen and beside Spotify:
      zoom (+/−, pinch), pan and back to me; the speed-limit sign drawn on the map (the user: "where is the speed limit?").
      Places on the map (22 Oct, replacing a GridTemplate start page that left the small card blank): with no directions
      the big side shows cards over the dimmed map — the phone Home's 8, Favourites (all the other saved ones), Nearby, the
      recent destinations not saved (MapsPrefs.recent, phone and car) — tapped through SurfaceCallback.onClick; Map hides
      them, a tap on the map drops a pin (Go / ✕); Search (as you type, parked). The small card is told apart by the visible
      area lying on one side of the surface's middle (the surface is always the whole screen). The column goes to the
      driver's side by the network's country (right where traffic keeps left). On the map a column
      with no background — speed, the limit's sign, "€ Tolls n" lit while the route has tolls ahead, "No GPS" after 10 s
      without a fix — and ⚙ Voice: every direction / important only (wrong way, new route, no GPS, arrival) / off.
      Sizes follow the car screen (the column a hundredth of its height per unit, at the driver's side, left). The small
      card (beside Spotify): with directions the next turn only, big (arrow, distance, street) and the limit; without, the
      Home's 8 places 2 by 4 (Android Auto opens Tetra on a tap there, so they show and do not start a route). The graph is
      opened as the car connects (the first route took 25 s).
      Phone and car one trip (CarLink, 22 Oct): with the car connected a place picked on the phone starts on the car's
      screen and the phone shows those directions with Stop. In directions the arrival card holds End, Routes (up to 3, with
      tolls), Stop (a place on the way, then on), and Parking — only when the destination has ΑμεΑ spots within Settings ›
      Maps' distance (500 m–2 km), said ~1.5 km before. Tolls: "€ n" in the arrival card, said at the start and ~1 km
      before each, € dots on the route.
- [x] Trips (22 Oct; the user: "a plan mode for the phone … how many stops … point to the 1st stop, the second … saved as
      a trip … start automaticaly for the next A B C D"): Directions › Plan a trip asks how many (2–10), then each stop in
      turn (map tap, Search or Saved → "➕ Stop B"), lettered on the map; Save / Save and go. Files in Tetra/Maps/Trips. A
      stop reached: the next leg starts when the car moves on (15 km/h or 150 m), kept 12 h in MapsPrefs.pendingTrip so it
      survives the engine off; with the car connected the trip runs on its screen (Trips among the car's cards too).
      For the drive (23 Oct): the speed column, € Tolls, wrong way and No GPS while moving; the voice over the car's speakers
      and the important-only mode; how the user's car shows the small card (an ✕ there if it fits); updateTrip for the
      cluster/HUD; the map dark at night (carContext.isDarkMode) — the user prefers the light map, ask; lanes.
      Not in the car: alternative routes, the tunnel's dead reckoning.
- [x] (maps-link session, 21 Oct) Emblems in tabs (the user, 21 Oct: "Add more emblims for pharmases, hospitals,
      relatives travel locations, nature like waterfalls rivers beaches and have them in tabs with categories"): General,
      People, Health, Travel, Nature (26 emblems) in the place dialog, and the saved-places list in the same tabs; the
      accessible (wheelchair) mark in General and beside Parking (the user: "a disability mark to have for my parking spots
      and accessible places").

## PDF — from viewer to editor (planned and built 22 Oct; what is left at the end of this section)

The user, 22 Oct: "make our PDF viewer a PDF editor … parity with PDF Craft"; "I dont want to copy their code I want you to see
the futures … as a roadmap"; "I have no intension to ever taking money from the things i make, software is born free and it
sould stay that way". So: PdfCraft (github.com/storytold/pdfcraft, v0.11 alpha, Rust, desktop/web only, ~170 tools) is the
feature checklist only — none of its code. The engine is **MuPDF** (AGPL-3.0, fine for a free app; Tetra's LICENSE says GPL-2.0
with no version, so "GPL-3.0-or-later" should be stated — the user's call).

Why an engine at all: today's viewer (PdfScreen.kt) uses Android's PdfRenderer, which does not draw annotations — a PDF with
highlights, notes, ink, stamps and arrows showed only its text (emulator, 22 Oct). MuPDF draws them all, edits them, does pages,
forms, true redaction, passwords, compression; minSdk 30 stays. Size: AAR 21.6 MB for 4 ABIs (~5.4 MB compressed per ABI);
measured in Tetra's APK before Phase 1 is committed (today arm64 release 159 MB). Fallback if ever needed: PDFium + PdfBox-Android.

Under Office's rules: opens view-only with Words' Edit; tabs File · Home · Review · Draw · Pages · Fill & Sign · Insert · Forms ·
Protect · View (+ Image, Comment when chosen); everyday on the ribbon with icons, the rest behind ⋯; tablet/phone strips with
✓/×, modals for many fields; Words' parts reused (Swatches, DesignGalleryStrip fonts, SizeStepper, Draw tab for ink and
signatures, FindReplaceBar, Versions, AutoSave); incremental save keeps the original bytes; Greek text boxes embed a real font.

- [x] (22 Oct) MuPDF in (com.artifex.mupdf:fitz 1.28.5, ~11 MB native per ABI): a file's comments drawn, Find/Copy on every
      Android, password asked. Edit (view only first, as Words): Pages — Organize (the scanner's ReorderGrid, shared), rotate,
      delete, blank page, insert another PDF, extract a page; Review — highlight/underline/strikethrough over the words a
      finger crosses, notes (written; tapped to read), rectangle, oval, arrow, colour (Words' Swatches), delete a comment;
      Draw — pen, highlighter, widths; Fill & Sign — text (Greek: MuPDF's URW fonts; long dashes made plain), tick, cross,
      dot, date, a signature drawn once in Words' DrawDialog (kept in the app) placed by a tap; a form's own fields filled
      by tapping them (text, boxes, round buttons, lists; Edit comes on by itself). Undo/redo (MuPDF's journal), Save
      appending to the bytes as opened (incremental; Versions kept), Save or not on leaving. Emulator-checked each.
      Not yet: text boxes with a box/colour, own stamps, the comments pane, bookmarks, Insert, Protect, phone layout check.
- [~] Phase 0 (1–2 days, no library): superseded by MuPDF — highlights/notes/text boxes/stamps shown on API 35+ (RenderParams flags), Find and copy
      on Android 12–14 (PdfRendererPreV), password-protected files (LoadParams).
- [x] Phases 1–3 and most of 4 built 22 Oct (emulator-checked; see git log): every annotation drawn and edited; Pages; Review
      with the Comments pane (replies) and Bookmarks; Draw; Fill & Sign (forms filled and made: text field, check box); Insert
      (text box, picture, own and standard stamps, header & footer, watermark); Protect (password, find & redact, select &
      redact — the text removed from the file, checked with pymupdf); Reduce size; OCR (Tesseract, Greek + English, an invisible
      text layer); Export text; Edit in Words (colours, lists, pictures, tables found after the fact, contents → Words' TOC).
- [ ] Left: cover-and-retype for a PDF's own text; digital (certificate) signatures (~6 MB of crypto); remove hidden
      information; document properties; a placed signature moved afterwards; Edit in Words sometimes misses a table's header
      row; the phone layout checked; all of it on the real pair (phone 208c8192, tablet 971f6b37).
- Left out: JavaScript, XFA, PDF/A, measuring, compare, Bates numbering, Action Wizard, accessibility tagging, layers.
- Desktop Tetra later: MuPDF's npm build (WASM) or pdfcraft-cli as a backend; the edit list and this ribbon spec shared.
  PdfCraft can be the reference to compare pages against, as LibreOffice is for Words.

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
