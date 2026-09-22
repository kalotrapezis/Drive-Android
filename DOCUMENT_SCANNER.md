# Document Scanner

Status: complete, accepted on Xiaomi 15, 2026-09-22. The PDF Manager was
dropped; its Home slot is **Codes** (see `FEATURES.md`).

User-facing behaviour is listed in `FEATURES.md › Document Scanner`. This file
records how it works and the rules it must keep.

## Code map

| File | Role |
|---|---|
| `ScanScreens.kt` | Camera, document viewer, actions island, corner editor, reorder grid, paint layer; page rendering |
| `ScanDetection.kt` | OpenCV page outline, corner ordering, live-to-capture mapping, edge gap fill |
| `ScanFilters.kt` | Lighting flattening, ink/B&W filters, paper swatches, shared paper tone |
| `ScanFiles.kt` | Safe PDF naming and multi-page A4-width PDF writing |
| `PhotoEditor.kt` | Reuses `CornerEditor`, `PaintPanel`, `ScanStrokeLayer` for photo editing |

## Pipeline

1. **Detect** (every analysis frame, 256 px luma): bright-paper threshold, then
   Canny edges; each contour's convex hull is simplified with a rising
   tolerance until four corners remain (handles crumpled edges); corners are
   ordered by angle around the centre (handles ~45° tilt).
2. **Capture**: the outline visible at the shutter is mapped from the analysis
   buffer (crop rect + rotation) into the saved photo. Re-detection on the still
   is only a fallback, because it can lock onto another bright shape such as a
   laptop screen.
3. **Render on demand** from the photo plus stored edits (`CapturedPage`):
   perspective warp with 4% margin → optional gap fill → rotation → filter →
   painted strokes. Edits are data, so nothing is lossy until Save; the viewer
   renders at ~2000 px, thumbnails at 1/8, the PDF at up to 3508 px.
4. **Gap fill**: per side, a reference paper colour is sampled just inside a
   6% edge band and median-smoothed along the edge (corners sample beside the
   page). Only pixels that clearly differ from it and connect to the border are
   painted, so an already-perfect crop is untouched.
5. **Filters** divide each pixel by an estimated paper background (brightest
   tenth per grid cell, borrowed from a neighbour when a cell is mostly ink),
   so shadows flatten to white while ink keeps its contrast.

## Rules

- Capture data stays in app-private cache until Save; discarded or replaced
  pages are moved to a private quarantine folder, never Drive files.
- Saving never overwrites: a name collision becomes `name (2).pdf`.
- Camera permission is requested only from the scanner screen.
- Leaving a document with pages asks before discarding.

## Known limits

- PDFs are large (about 3 MB per page at 300 dpi) because Android's
  `PdfDocument` stores images with little compression; 200 dpi would halve it.
- Gap fill cannot fix table showing deeper than 6% into the page; adjust the
  corners instead.
- No OCR filing, automatic naming, or single-page delete yet.
