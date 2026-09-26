# Document Scanner

Status: complete, accepted on Xiaomi 15, 2026-09-22; finder and crop reworked
and re-accepted 2026-09-23. The PDF Manager was dropped; its Home slot is
**Codes** (see `FEATURES.md`).

User-facing behaviour is listed in `FEATURES.md › Document Scanner`. This file
records how it works and the rules it must keep.

## Code map

| File | Role |
|---|---|
| `ScanScreens.kt` | Camera, document viewer, actions island, corner editor, reorder grid, paint layer; page rendering |
| `ScanDetection.kt` | OpenCV page outline, the rules that refuse a candidate, straight-edge fitting, letters-decide-the-cut, trim to paper, live-to-capture mapping, edge gap fill |
| `ScanFilters.kt` | Lighting flattening, the four filters, adaptive ink threshold, sharpening, paper swatches, shared paper tone |
| `ScanFiles.kt` | Safe PDF naming and multi-page A4-width PDF writing |
| `PhotoEditor.kt` | Reuses `CornerEditor`, `PaintPanel`, `ScanStrokeLayer` for photo editing |

## Pipeline

1. **Detect** (every analysis frame, 256 px luma): bright-paper threshold, then
   Canny edges; each contour's convex hull is simplified with a rising tolerance
   until four corners remain, and the corners are ordered by angle around the
   centre (handles ~45° tilt).
2. **Judge** — four rules, cheapest first, all of them about refusing rather
   than ranking. A candidate must:
   - **cover the middle of the view.** You point a camera at what you want, so a
     shape that does not contain the centre is not the page, whatever it looks
     like. This one rule discards most of what there is to be distracted by —
     the laptop lid beside the page, the tile two along, the seam crossing a
     corner;
   - **be page-shaped**: convex, no corner under 45° or over 135°. Generous
     enough for any angle you can read from, mean enough to refuse a shadow's
     spike;
   - **stay inside the frame**, which is what the screen asks for anyway;
   - **be brighter inside than just outside its own edge.** Comparing with the
     average of the whole scene was right on a dark table and wrong everywhere
     else: under a lamp, or on a laptop lid, the surroundings are as bright as
     the paper. Across a page's edge there is always a step; across the middle
     of a lid there is none.
3. **Straighten the edges.** A torn or crumpled edge is still a straight edge:
   each side is fitted to the outline points running along it, outliers dropped
   once and refitted, and the corners come back as where those four lines cross.
   The paper was cut straight once; the tear is the part to ignore.
4. **Hold it.** An outline survives 900 ms without a fresh detection, and each
   new reading nudges it rather than replacing it, so one bad frame — a hand, a
   reflection, the light shifting — does not make the page blink out. A real
   move is followed at once. Auto capture never fires on a held outline: holding
   is for the drawing, not for the decision.
5. **Capture** at the sensor's own resolution (highest available, 4:3). The
   outline visible at the shutter is mapped from the analysis buffer into the
   saved photo; re-detection on the still is only a fallback.
6. **Ask the letters where to cut.** The page is read once with ML Kit — at
   capture, not thirty times a second — and each side asks whether anything is
   written near it:
   - nothing near this edge → cut 1.5% *into* the paper. Nothing is there to
     lose, and a cut inside the paper cannot bring the table with it;
   - letters close to the edge, or past it → go carefully: the cut moves
     *outside* the paper, far enough to leave the text room, for the gap fill to
     paint.

   Only this page's letters get a say: text further out than a tenth of the page
   is ignored, and no side may move outwards by more than that. Without that
   limit a word on a laptop lid drags a side across the whole frame, and a crop
   covering everything looks exactly like no crop at all.
7. **Render on demand** from the photo plus stored edits (`CapturedPage`):
   perspective warp → trim to paper → sharpen → optional gap fill → rotation →
   filter → painted strokes. Edits are data, so nothing is lossy until Save; the
   viewer renders at ~2000 px, thumbnails at 1/8, the PDF at 3508 px (300 dpi)
   or 2339 (200), chosen in Settings › PDF scanner.
8. **Trim to paper**: square to the frame now, so any band the outline kept can
   be cut off — walk in from each side while what is in front is not the paper's
   colour. A line of text is mostly paper with ink in it, so only a line almost
   entirely unlike the paper qualifies; text is never mistaken for background.
9. **Sharpen**: straightening resamples every pixel from between four others,
   which softens small print. A gentle unsharp mask puts back what the
   interpolation took and no more — a hard one turns paper grain into speckle.
10. **Gap fill** paints out background the crop could not avoid, and nothing
    else. It starts only where the border really is background — a run of pixels
    along an edge must be unlike the paper for a stretch, so a corner of table
    qualifies and a printed rule touching the edge does not — and it paints one
    flat colour, the paper's own, taken from the middle of the page with the
    darkest quarter dropped so ink cannot darken it. A page whose crop came out
    right is left byte for byte as it was.
11. **Filters** divide each pixel by an estimated paper background (brightest
    tenth per grid cell, borrowed from a neighbour when a cell is mostly ink),
    so shadows and crease shading flatten to white while ink keeps its contrast.
    B&W then splits ink from paper at a line taken from the page's own
    brightness (Otsu), not a fixed one: grey thermal print, pencil and tired
    toner all sit above 170, and a fixed line turned those pages blank.

## Rules

- Capture data stays in app-private cache until Save; discarded or replaced
  pages are moved to a private quarantine folder, never Drive files.
- Saving never overwrites: a name collision becomes `name (2).pdf`.
- Camera permission is requested only from the scanner screen.
- Leaving a document with pages asks before discarding.

## Known limits

- PDFs are large (about 5 MB per page at 300 dpi) because Android's
  `PdfDocument` stores images with little compression; 200 dpi roughly halves
  it, in Settings › PDF scanner.
- Resolution is bounded by how much of the frame the page fills, because the
  crop only ever scales down. Measured 2026-09-23: 125 ppi before this work,
  211–233 ppi after, across four pages. A4 at a true 300 dpi needs the page to
  nearly fill the viewfinder.
- Trim and gap fill only reach 6% into the page; an outline that is wildly wrong
  — half the table inside it — is beyond both, and the corners need adjusting by
  hand. A classifier veto ("is what I just cropped a document?") is the obvious
  next move and is not built.
- A page lying on something as bright as itself, with no step at the edge, is
  still hard; the centre rule usually saves it because you are aiming at the
  page rather than the surface.
- No OCR filing, automatic naming, or single-page delete yet.
