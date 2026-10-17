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
- Other sessions: add your line here when you start.

## Waiting on the user
- Slides' drop cap: a raised cap (the first letter just bigger) or a dropped one (its own box, the lines indented
  beside it)? PowerPoint has none of its own. (asked 17 Oct)

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
- [ ] Tab names and order: Selection first when something is chosen, then File, Home, Insert, … as Words'; the
      contextual tab (Picture, Table, Column, Row, Sheet) comes by itself, in one place and colour in every app.
- [ ] File holds the same items in every app: Save, Versions, Export PDF, Print, Share, Open in another app, About
      this file, Send log, Settings. Slides lacks Versions, Export PDF, Print, About and Send log today; the checklist
      above says Slides' Export PDF is done, but its File tab has no such button, so check that line.
- [ ] Every button has an icon (memory: ribbon icons); the everyday tools on the ribbon and the rest under ⋯ on a
      full page (memory: ribbon ⋯ button) — in Slides and Tables too.
- [ ] Strips (Words' Page numbers, Tables' Borders and Number) open the same way: ‹ back, a title, sliding in.
- [ ] One colour picker (Words' swatches; Slides has its own `SLIDE_COLOURS`), one font picker, one size picker.
- [ ] The top bar: Undo, Redo, Save and Done in the same places; the status text (counts, Saved / Not saved) worded
      alike. View-only on opening with Edit, the Save / Don't save question on leaving, recovery drafts and versions
      behave the same in all three.
- [ ] Keyboard shortcuts and right-click menus the same where the action is the same (Ctrl+F, Ctrl+C/V, Delete).
- [ ] Messages, empty states and dialogs in one style; English only (memory: UI English only).

### B. Slides — what PowerPoint and Impress have that Slides lacks
Foundation first: **a slide's words as runs** (today an edited paragraph is one line in its first run's look,
`SlidesEdit.withTexts`) — needed by most of Home and by References.
- [ ] **Home**: font, size, bold, italic, underline, strike, colour, highlight; align left, centre, right, justify;
      bullets and numbering, list level in and out; line spacing; text direction and anchor (top, middle, bottom);
      autofit (shrink on overflow, the box grows to its words); Clear formatting; Format painter.
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
      lines indented beside it: looks right everywhere but doesn't reflow) — **the user to choose**; Date and time
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
