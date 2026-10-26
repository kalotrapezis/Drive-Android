# PDF like Words: tab by tab, page by page, modal by modal

Branch `claude/pdf-like-words`. Plan.md's top item, started 26 Oct with the user. Every piece of Words' interface is listed
here next to the PDF editor's, and marked:
- **same**: Words' own code, shared.
- **adapted**: Words' code and look, changed only where a PDF differs, the difference stated.
- **n/a**: no sense in a PDF, with the reason.
- **PDF-only**: something Words lacks, built in Words' style.

Source: an inventory of Writer.kt/OfficeUi.kt and PdfScreen.kt/PdfText.kt (26 Oct). W = Writer.kt, PS = PdfScreen.kt,
PT = PdfText.kt.

## The engine: why the PDF feels different today

The PDF editor shares Words' small parts: OfficeTabRow, RibbonButton, Swatches, SizeStepper, ObjectHandles, DrawDialog
and the rulers. It builds everything else itself:
- its own tab list (strings, not `WriterTab`) and its own `when(tab)`;
- its own top bar and ⋯ menu;
- its own find row instead of `FindReplaceBar`;
- its own selection model;
- its own text layout (PdfText's StaticLayout, not the Office engine's `OfficePages.stack`);
- its own Draw tab, page setup, header/footer, watermark and comments pane.

Only Tetra's own text boxes can be chosen. Pictures, shapes, stamps, ink and signatures can only be erased.

**The change:**
- **E1. One object model.** Anything Tetra puts on a PDF page is a Words object (`DocImage`): picture, shape, text box,
  WordArt, drawing, signature, chart or equation.
  - It is stored in its annotation's own key, as `TetraText` is today.
  - Its look is drawn by Words' own drawing code into the annotation's appearance.
  - So it can be chosen, moved, sized, turned and edited again exactly as in Words.
  - Other apps' annotations (stamps, ink, shapes) are chosen too: move, size and delete only.
- **E2. Words' object pieces leave Writer.kt for shared Office code**, where today they are private or written inline in
  `Dialogs()`:
  - the Picture tab's body;
  - the Size dialog and Wrap;
  - `TextBoxDialog`, `HistoryDialog`;
  - Save as, Rename, Leave and About;
  - `PositionDialog`, taking a page picture instead of Words' `LaidPage`.

  Words keeps working exactly as before. The 6 .docx fixtures' round-trip test and WriterLayoutDeviceTest prove it.
- **E3. A PDF text box is laid out by the Office engine** (`OfficePages.stack`, as Words' pages and Slides' boxes are),
  not PdfText's StaticLayout.
  - Home's paragraph tools then work on it: lists, spacing, indents, styles.
  - Text boxes saved the old way still open and are rewritten the new way when edited.
- **E4. One screen frame**, shared by Words and the PDF:
  - top bar, ribbon place (desktop/tablet/phone), strips and galleries;
  - `FindReplaceBar`, `KeyboardBar`, keys, the page pill and the rulers.

  The PDF's `when(tab)` becomes per-tab bodies like Words'.

## Top bar

| Words | PDF today | Plan |
|---|---|---|
| ‹ Back · name, "•" while unsaved | ‹ · name · "N pages · View only" | **adapted**: pages instead of words · save state, as Words |
| Counts → About | none | **adapted**: page count → About |
| ♥ Favourite (≥600 dp) | ♥ | same |
| View-only: Reading mode · Navigation · Two pages · Share · Save (when dirty) · Edit | Find · ♥ · Share · Print · ⋯ (Copy page text, Open in another app, Comments, Bookmarks, Edit in Words, Export text, Versions) | **same** as Words. Print, Copy page text, Export… and Versions go to File; Comments and Bookmarks to View; Find to Ctrl+F and editing's bar |
| Reading mode: Smaller · Bigger · Close | none | **adapted**: MuPDF's reflowed text of the pages in Words' reading view (ask) |
| Editing: Find & Replace · Undo · Redo · Save · ✓ | Undo · Redo · Save · ✓ | **adapted**: `FindReplaceBar`, whose Replace works only in Tetra's own text boxes (the page's own text: Edit in Words) |
| Phone: bar hides on scroll down | always shown | same |
| Banner for unsupported parts → About | none | same (forms with JavaScript, XFA, signatures) |

## Pages view

| Words | PDF today | Plan |
|---|---|---|
| `PagesView`: pinch 0.5–4×, rulers ≥700 dp, "Page n of N" pill | own LazyColumn: snapping fling, pinch 1–5×, double tap 2.5×, pill | **adapted**: Words' zoom and pill. The PDF keeps the snap per page while viewing (ask) |
| Rulers along the top and down | the same rulers | same |
| Phone: print layout scaled, pinch | thumbnails column toggled by the pill | **adapted**: Navigation pane as Words' (below) |

## Ribbon tabs: order

Words: File · Home · Insert · Draw · Design · Layout · References · Review · View, plus Selection (first) and Picture or
Table (last, the object's kind as its name) when chosen.

PDF today: File · Home · Insert · Draw · Layout · Pages · Review · Fill & Sign · Protect · View, plus Selection and Text box.

**Plan:** File · Home · Insert · Draw · Design · Layout · **Pages** · Review · **Fill & Sign** · **Protect** · View, plus
Words' Selection and the object's kind tab.
- References: **n/a**, since citations and contents are made in Words; Edit in Words covers it.
- No ⋯ overflow page in either today; everything is on one scrolling row, as Words.

### File

| Words | Plan |
|---|---|
| Save · Save a copy · AutoSave · ♥ (phone) · Save as… · Rename | same (the dialogs become shared) |
| Versions → `HistoryDialog` (versions shown as pages, Keep this one) | **same**. The PDF's list `VersionsDialog` is replaced |
| Export PDF | **adapted**: Edit in Words, Export text |
| Print · Share · Open in another app · About this file · Send log · Settings | same. About is Words' (kind, pages, what is unsupported) plus MuPDF's metadata |
| — | **PDF-only**: Copy page text (from today's ⋯) |

### Home

These act on the chosen text box, or on the next one typed. With E3 every item is real.

| Words | PDF today | Plan |
|---|---|---|
| B I U S · X² X₂ | B I U | same |
| Font gallery · size −/+ · text colour · highlight | the same pieces | same |
| Aa Change case · Spacing · Format painter · Clear formatting | Clear formatting; Copy/Paste formatting in Selection | same |
| Normal · Title · H1–H3 · Styles… | none | same (styles of the text box's own paragraphs) |
| Bullets · Numbering · Multilevel · indents | none | same |
| Align ×4 · line spacing · 1.0/1.15/1.5/2.0 · Borders · More… (Paragraph) | Align ×4 · fill · border | same: `ParagraphDialog`, `BordersDialog` |
| — | Text · Text box tools | **adapted**: they go to Insert, as Words' |

### Insert

| Words | PDF today | Plan |
|---|---|---|
| Blank page | Blank page | same |
| Page break · Line break · Drop cap · Content control | — | **n/a**: the pages are fixed, there is no running text. Form fields are under Fill & Sign |
| Table (drag grid) | — | **adapted**: a Words table drawn as an object (later, ask) |
| Picture | Picture (placed once) | **same**: a Words object, chosen and edited afterwards |
| Signature ▾ (Put/Draw mine · Draw new · From a picture) | Signature (Insert) + Sign / New signature (Fill & Sign) | same strip in both tabs |
| Shapes → `ShapesDialog` (all categories, styles) | Rectangle · Oval · Arrow, 2 pt, fixed | **same**: every Words shape and `ShapeOptionsDialog` |
| Video | — | n/a: PDF video is rare and most readers don't play it |
| Text box · WordArt | Text box (typed on the page) | **same** objects. Typing: see the questions |
| Chart · Equation · Symbol · Date | Date (a tap) | same dialogs, drawn as objects (Symbol/Date into the chosen text box, or a new one) |
| Link | — | **adapted**: a PDF link to a web page or a page in this file |
| Comment | Note | **same**: `NewCommentDialog`, saved as a PDF note |
| Header & footer · Page numbers galleries + "Your own…" | Header & footer dialog · Page numbers (one form) | **same** galleries, stamped on every page |
| — | Insert PDF · Stamp | **PDF-only**: Insert PDF moves to Pages. Stamp stays in Insert, as a gallery strip |

### Draw

| Words | PDF today | Plan |
|---|---|---|
| Draw on page → `DrawDialog` with the page under it (Pen, Pen 2, Highlighter, Eraser, Move, Undo, colours, widths) · Drawing box | live pen/marker on the page, 3 widths, a colour strip, Erase | **adapted**: Words' `DrawDialog` with the PDF page under it. The strokes are saved as ink annotations, so other readers see them and the eraser still takes them one by one (ask) |
| Picture tab › Edit drawing | — | same |

### Design

| Words | Plan |
|---|---|
| Watermark ▾ (presets · Custom… · Picture… · Remove) | **same** strip. Today's PDF watermark dialog is replaced; Remove works for Tetra's watermark |
| Page colour | **adapted**: a coloured rectangle under the page's content |
| Page borders → `PageBorderDialog` | **adapted**: drawn on every page |
| Standards · Style set · Colours · Fonts · Paragraph spacing | **n/a**: a PDF's look is fixed. Tetra's text boxes take Home's styles |

### Layout

| Words | PDF today | Plan |
|---|---|---|
| Margins · Size → `PageSetupDialog` | Size ▾ and Margins ▾ strips (PT:365) | **same** dialog, taking plain values; margins are guides for Position and new text |
| Portrait · Landscape | the same | same |
| Paragraph Left/Right/Before/After steppers | — | same, for the chosen text box |
| Columns · Breaks · Line numbers · Hyphenation | — | n/a: there is no running text |
| Blank page | Blank page · Delete page · Rotate ×2 | same. Delete and Rotate are PDF-only and stay in Pages |

### Pages (PDF-only, in Words' style)

Today: Organize (ReorderGrid), Rotate ×2, Delete, Blank, Insert PDF, Bookmarks, Recognize text, Reduce size, Extract.

Plan: the same, plus:
- **Split** and **Merge** (several PDFs into one);
- **Duplicate page**;
- **Crop pages**, with Words' `CornerEditor`;
- **Flatten** (comments and form fields into the page).

Bookmarks moves to View › Navigation. Recognize text and Reduce size stay here.

### Review

| Words | PDF today | Plan |
|---|---|---|
| Spelling · Next mistake | — | same, in Tetra's text boxes |
| Read aloud | — | **same**: the page's text through `ReadAloud` |
| Track changes and its 6 buttons | — | n/a |
| Comments pane: tablet side pane with connector lines (`CommentPane`), phone right sheet (`CommentSidebar`) · New · ‹ › · Reply · Resolve · Delete | own `PdfPane` (list, reply dialog, delete) | **same**: PDF notes and markup shown as Words' comment cards. Resolve is saved as the note's state |
| — | Highlight · Underline · Strike · Colour · Erase | **PDF-only**: stays; Words' Swatches already |
| — | Note · Text box · Rectangle · Oval · Arrow · Stamp | moved to Insert (above), as Words' |

### Fill & Sign (PDF-only)

Today: Text, Tick, Cross, Dot, Date, Text field, Check box, Sign, New signature, sizes S/M/L, Erase.

Plan: the same, with:
- the signature strip as Insert's;
- marks as Words' objects, so they can be moved;
- form fields shown in Words' `FormBar` while filling.

### Protect (PDF-only)

Today: Password, Remove password, Select text, Mark area, Find & redact, Redact marked.

Plan: the same, with these dialogs in Words' panel look: Password, and Find & redact (with its patterns). Also
**Remove hidden information** (from the old list).

### View

| Words | PDF today | Plan |
|---|---|---|
| Navigation → `NavigationPane` (headings) | Navigation (thumbnails) · Bookmarks (own pane) | **adapted**: Words' pane with two parts, Pages (thumbnails) and Headings (the PDF's bookmarks) |
| Comments · Zoom − % + · Fit width · Two pages · cm/in | the same, plus Ruler | same |
| Reading mode · Mobile view (phone) | — | adapted: MuPDF's reflow (ask) |

### Selection (contextual, first)

Words: Cut · Copy · Paste · Paste text only · Select all · Copy formatting · Paste formatting.

PDF today: the same except Select all. Plan: **same**. Select all means all words in a text box, or the page's own text
for copying.

### The chosen object's tab (contextual, last, named by kind: Image, Shape, Text box, WordArt, Signature…)

This is screenshot 4 against 5.

| Words | PDF today ("Text box" only) | Plan |
|---|---|---|
| Edit text / Add text / Edit drawing / Edit data / Edit equation | Edit text | same, by kind |
| Shape options → `ShapeOptionsDialog` | Fill · Border swatches | same |
| Size → sizing dialog (preview, 3 handles, fields, lock) · Crop → `CornerEditor` | — | same |
| Wrap text | — | **adapted**: in front of the text (an annotation) or behind it (written into the page). No wrapping around: a PDF's text does not move |
| Position → `PositionDialog` (the page small, "Drag it where it goes") | Position ▾ (Align strip) + 4 arrow buttons | **same** dialog over the PDF page. Align ▾ as Words' (below). The arrows go (the user's screenshot 5) |
| Bring to front · Send to back · Rotate → `RotateDialog` | Arrange ▾ · Rotate ▾ | same |
| Group / Ungroup · Align ▾ | — | same |
| Turn left · Turn right · Flip | — | same |
| Replace · Save as · Delete | Duplicate · Delete | same. Duplicate (Ctrl+D) stays (Slides has it) |

### On the page

| Words | PDF today | Plan |
|---|---|---|
| Tap chooses and shows the kind's tab. Long press → Position. Drag moves the chosen one | tap chooses (text only), drag moves at once, tap on chosen types | **same** as Words for every object (ask about tap-to-type) |
| `ObjectHandles`: thin blue outline, small squares, round knob on a stem; snaps to 45° | `ObjectHandles` through `PdfChosenHandles` with its own outline | **same** (the outline is ObjectHandles' own) |
| Keys: Ctrl+F S Z Y B I U | Ctrl+S Z Y V C X D, Del, arrows, Enter, Esc | **same** plus the PDF's object keys (Slides has them too) |
| No right-click menu (Slides, Tables and PDF have `OfficeMenu`) | right-click menu | keep. It is Office's, not the PDF's own |
| Phone: `KeyboardBar` while typing | — | same |

## Order of work, each step shown to the user before the next

1. **E2 + E4**: the shared frame and object pieces out of Writer.kt. Words looks and works the same, checked on the
   tablet with Tetra test.docx.
2. **E1**: PDF objects become Words objects. Choose, handles, drag, Size, Position, Rotate, Arrange, Align, Delete, and
   the kind's tab, for text box, picture, shape and signature.
3. **E3**: PDF text boxes on the Office engine, so Home is Words' Home.
4. **Top bar, Pages view, View, Selection, File**: Words' own.
5. **Insert, Draw, Design, Layout**: Words' items and dialogs, as in the tables above.
6. **Review**: Words' comments.
7. **Pages, Fill & Sign, Protect** in Words' style, plus Split, Merge, Duplicate, Crop, Flatten and Remove hidden information.
8. Phone pass, and the real pair (tablet 971f6b37, phone 208c8192).
