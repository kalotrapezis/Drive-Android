# ChatGPT handoff — Writer tables

Date: 28 September 2026  
Branch: `writer-library`

## What the user asked for

- Editing table text should not require a modal: tap a cell, focus it, and type directly.
- Table structure remains in a modal: add/remove rows and columns and merge cells.
- Insert Table should provide a 5×5 grid that supports dragging to choose the table size.
- The nested 2×2 table inside a parent cell must not extend into the next cell or draw extra lines outside its bounds.

## Implemented

- A tap on a Word table cell is consumed before the rich-text editor can take it. An inline `BasicTextField` is placed over the exact cell, focused automatically, and the keyboard opens. The cursor starts at the end. Leaving the cell, saving, or opening Layout commits the edit while preserving the existing paragraph/run formatting.
- Insert › Table now opens a 5×5 touch grid. Tapping or dragging selects the desired rows and columns and inserts that bordered Word table.
- Table › Layout provides structural operations: select a rectangular range, merge simple unmerged cells, add a row or column, remove the last row or column, and delete the table. Existing complex merges are rejected instead of being damaged.
- Table geometry comes from `OfficePages`, so the editor bitmap and page view use the same cell positions.
- Fixed nested-table overflow at the shared layout layer. Explicit column widths are now proportionally fitted when wider than the parent cell. A nested table may use the parent cell's right margin, matching LibreOffice, while ordinary cell text still respects both margins.

## Verification

- `133` unit tests pass; the regression test checks that an oversized 160 pt nested grid is fitted to 95 pt while an already-fitting grid remains unchanged.
- Live emulator test: edited a real cell, inserted a dragged 5×5 table, merged 2×2 cells, added a row and column, saved, pulled the DOCX, inspected its XML, and converted it with LibreOffice to a five-page A4 PDF.
- Reopened `02_Tables.docx` in both page view and edit view. The inner 2×2 table now ends exactly at the right border of the parent Description cell and no longer draws into Quantity.
- The latest debug APK was installed successfully on emulator `emulator-5554` and phone `208c8192`.

## Commits

- `7613a78` — `Writer: edit Word tables directly and add layout tools`
- `7ba893d` — `Writer: keep nested tables inside parent cells`

## Remaining table work

- ODT table reader/writer support.
- Word rotated cell text and conditional table-style formatting.
- Physical tablet acceptance when the tablet is connected.

Untracked files under `Assets/` belong to the user and were deliberately left untouched.
