# Document Scanner and PDF Manager

Status: planning boundary, requested 2026-09-20. No scanner or PDF-manager
code has been started.

## Product boundary

This is a local `New/Scan` module. It captures paper pages, lets the user
review each page, and saves only after an explicit destination/name decision.
It must not upload documents or silently move existing files.

The first implementation slice should be deliberately small:

1. capture one or more pages from the camera;
2. review, reorder, rotate, retake, or remove pages before saving;
3. export a PDF or individual JPEG pages to an explicit path under
   `/sdcard/Drive/`;
4. open the result through the existing Android file chooser path.

## Non-negotiable safeguards

- Keep capture data app-private until Save; cancel deletes only that temporary
  capture session, never existing Drive files.
- Never overwrite a destination. Offer a new name when one already exists.
- Ask for camera permission only when capture is opened.
- Preserve page order and make every destructive page action reversible until
  the final save.
- OCR, automatic document names, and suggested folders are later opt-in local
  suggestions, not part of the first save path.

## Decisions needed before implementation

1. Capture engine: Android's built-in document scanner or an in-app camera.
   The built-in option is faster and smaller; an in-app camera gives full
   offline control and custom filters.
2. First export default: PDF only, JPEG only, or ask at each save.
3. Default destination inside `Drive/`: a fixed `Scans/` folder or a required
   destination picker rooted inside `Drive/`.
4. First-slice editing: automatic edge detection plus crop/rotate, or also
   grayscale/contrast filters before export.

## Acceptance checks

- Deny camera access and confirm the app remains usable with a clear retry.
- Capture two disposable pages, reorder and rotate one, export, and confirm
  the output page order in a native viewer.
- Cancel a capture and confirm no final document was created.
- Attempt the same filename twice and confirm no file is overwritten.
- Force-stop/reopen after export and confirm the PDF appears in the selected
  Drive folder without any duplicate temporary copy.
