# Test tree — finishing Word (15 Oct 2026, evening)

Same rules: phone 208c8192, Print layout, Mobile view off, copies in `Test-Words/`.

- **11. Edit on the page**
  - [x] A file with 24 pt after its paragraphs: Edit shows the same gaps as the pages; typing and Enter keep them (Claude, adb)
  - [x] A text box with bold, red italic and blue underlined words: Picture › Edit text, add words to each line → each keeps its look; saved, LibreOffice the same (Claude)
  - [x] Tap an equation → an "Equation" bar with x=(a+b)/2; type +sqrt(y^2) → the page shows it a moment later; saved, LibreOffice shows the equation (Claude)

- **12. .odt parity**
  - [x] `Controls_test.odt` (LibreOffice's own, six controls): no copy banner; tap Pear → bar "Fruit" → Apple; tap ☒ → Untick; Save → LibreOffice shows Apple and ☐, still controls (Claude)
  - [ ] An .odt: Insert › Content control › Drop-down → open in LibreOffice: a real drop-down

# Test tree — Writer's Word gaps (15 Oct 2026)

On the phone (208c8192), Print layout, Mobile view off. Use copies in `Test-Words/`, never the originals.
Tick each leaf; a failing one: note what you saw beside it.

**15 Oct (Claude over adb):** all ten pass on the phone. Left for you: opening the saved files in Word on the PC (citations, equations, groups, page borders, controls, sections, WordArt effects).

**First install the build** (the phone was unplugged when it was made; it is not on the phone yet):

```bash
~/Android/Sdk/platform-tools/adb -s 208c8192 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Writer's log while testing: `adb shell run-as com.kalotrapezis.drive cat files/logs/writer.log`.

- **1. Citation tapped** (References)
  - [ ] Insert a citation; tap inside "(Smith, 2020)" → a "Citation" bar over the ribbon
  - [ ] *Edit Smith, 2020* → the source form, filled; change the year → the citation and the reference list follow
  - [ ] *Change…* → "Change citation", its source ticked (and page); pick another / add a page → replaced in place, no extra space
  - [ ] Cursor just after a citation (typing on) → no bar
  - [ ] Save, reopen in LibreOffice/Word → the citation is still a citation field

- **2. Reading mode** (View)
  - [ ] View › Reading mode → no ribbon, no keyboard, text bigger and reflowed to the screen
  - [ ] − / + in the top bar change the size; leave and come back → the size is remembered
  - [ ] From the pages (open a document): the book icon → reading mode
  - [ ] Back (or Close reading) → Edit, on the page (Print layout) as before; Mobile view still off
  - [ ] A document with pictures and a table reads sensibly (pictures in the text)

- **3. Equations** (Insert, Picture)
  - [ ] Open `Test-Words/04_Academic_References.docx` → E = mc² drawn as maths (no grey "Equation" box), in Edit and on the pages
  - [ ] A new .docx: an empty line, Insert › Equation, type `x = (-b +- sqrt(b^2-4ac))/(2a)` → the preview shows the fraction and root; Insert → centred on its own line
  - [ ] In a line of words: Insert › Equation `a^2+b^2=c^2` → sits in the line, not too high or low
  - [ ] Try the piece buttons: ∑, ∫, lim, matrix, α
  - [ ] Tap the equation → Picture tab → Edit equation → the linear form is there; change it → redrawn
  - [ ] Save, open on the PC in Word/LibreOffice → real equations (editable there); the file was not copied ("(copy)")
  - [ ] File › Export PDF → the equations are in the PDF

- **4. Rotate, Group, Ungroup** (Picture tab, after Bring to front)
  - [ ] Insert › Shapes two rectangles; Wrap text › In front of text on both; move one aside (Position)
  - [ ] Rotate on one → 30° → turned in Edit and on the pages (View); 0° straightens it
  - [ ] Group on the first, then tap the second → one object (one blue frame); Position moves both; Bring to front works
  - [ ] Ungroup → two again, where they were, the turned one still turned
  - [ ] Save → open on the PC in Word/LibreOffice: turned and grouped as on the phone
  - [ ] An .odt: Rotate works; Group is greyed (Word files only)

- **5. APA abstract and run-in headings** (Design)
  - [ ] A copy with headings: Design › APA 7 paper › title page + *Add an abstract page* (type a few sentences, keywords) → Apply
  - [ ] Pages: title page, then "Abstract" page (bold centred label, unindented text, *Keywords:* line), then the text under the title again
  - [ ] A Heading 4 line followed by text → after Apply it is one paragraph: **Heading.** text…; Heading 5 the same in bold italic
  - [ ] Cursor in a paragraph "Short Title. Text…" → Design › APA level 4 → "Short Title." bold
  - [ ] Choose two words at a paragraph's start → APA level 5 → they become bold italic with a period

- **6. Page borders** (Design)
  - [ ] Design › Page borders → Double, 3 pt, dark red, Top + Bottom → Apply → only top and bottom, double, red on every page (View)
  - [ ] Again → Dashed, blue, Box → four dashed blue sides; Dotted shows dots
  - [ ] No border → gone
  - [ ] Save → Word/LibreOffice on the PC show the same border

- **7. Content controls** (Insert, Word files)
  - [ ] Insert › Content control → Plain text, title "Name" → grey "Click or tap here to enter text." with a blue shade; cursor in it → bar "Name" → Fill in → your name, black
  - [ ] Check box → ☐; Tick → ☒
  - [ ] Drop-down with three choices → Choose an item. → pick one from the bar
  - [ ] Date picker → Choose date → the date in the chosen format
  - [ ] Typing just after a control does not go into it
  - [ ] Save → Word on the PC: real controls (click the check box, the drop-down arrow)

- **8. Column and section breaks** (Layout › Breaks)
  - [ ] A Word copy: a title line, then text. Cursor at the text's start → Breaks › Section break · continuous → a dashed "Section break (continuous)" line
  - [ ] Cursor in the text → Layout › Columns › Two → on the pages: the title across, the text in two columns (only after the break — check)
  - [ ] Cursor mid-text → Breaks › Column break → the rest starts the second column; "Column break" line in Edit
  - [ ] At the end → Breaks › Section break · next page → what follows is on a new page
  - [ ] Save → Word on the PC shows the same sections and columns; Backspace at a break's line takes it away

- **9. Live word count**
  - [ ] Under the name in the top bar: "N words"; type a word → it goes up a moment later
  - [ ] Choose some words → "12 of N words"; let go → back to N
  - [ ] Tap it → About this file with all the counts

- **10. WordArt effects** (Insert › WordArt)
  - [ ] Type words, blue, tick Outline + Shadow + Glow → the field previews them; Insert → on the pages: gold glow, shadow, black outline
  - [ ] Picture › Edit text → change the words → the effects stay
  - [ ] Save → Word on the PC shows the glow, shadow and outline (LibreOffice does not draw Word's effects)
  - [ ] An .odt: only Outline and Shadow offered; LibreOffice shows them
