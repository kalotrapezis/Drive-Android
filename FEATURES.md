# Tetra: implemented features

Updated: 2026-09-23. This is the authoritative guide to what is implemented
now. It is based on the current code, not on older roadmap wording. Physical
device acceptance steps remain in `MANUAL_CHECKLIST.md`.

## Scope and safety boundary

- **Files** is a local file manager rooted only at `/sdcard/Drive/`.
- **Photos** reads the device's public MediaStore photos and videos. It does
  not copy, rename, or edit a public media file merely to view, search, tag, or
  collect it.
- Private app metadata includes Favorites, collection memberships, tags,
  analysis results, cached nearby places, and recently used file openers.
  Removing the app clears that private metadata, not public Camera/Screenshot
  originals or files in `Drive/`.
- Hidden media is the exception described below: it is copied into encrypted
  app-private storage before Android is asked to remove the public original.

## Home

- Cards: **Local Sync** (status only), a **Photos** group (Photos with a random
  real-photo backdrop that skips screenshots and AI-classified documents,
  Screenshots, Documents), a **Files** group (Files, Favorites, Recent), a red
  tools group (**Scanner**, **Codes**) and **Settings**.
- Buttons, spinners and selected states use a neutral white/grey accent to
  match the translucent islands (the wallpaper's dynamic colour is overridden).

## Files — complete

### Browse and find

- Requires Android **All files access**, then reads only `/sdcard/Drive/`.
  It creates that root only when it is absent.
- Opens folders, returns to the parent folder, and switches between list and
  grid views. Folder contents can be sorted by name or modified date.
- Pull down to refresh the current folder.
- File search is recursive within Drive and matches file/folder name and local
  tags. Search does not inspect file contents.
- Tags can be created, applied or removed from each item, then used as a search
  filter. Folder colours are private display metadata.
- Home lists up to 50 files that were successfully opened. Favorites is a
  separate local list of file or folder paths.
- Long-press an item to start a **multi-selection**; tapping then adds and
  removes. The navigation island is replaced by an actions island offering
  Share (while a real file is among them), Copy, Move, Tags, Add to Favorites,
  and Move to Trash — or Restore when the folder is Trash. Copy, Move and Tags
  open the same pickers a single item gets, applied to everything selected;
  chosen tags replace the item's tags rather than adding to them.
- **Trash** is not listed as a folder: it is reached from Files tools, and
  inside it the header carries a red Empty Trash. A single item already in
  Trash offers Restore rather than Move to Trash.

### Open and manage an item

- Tap a folder to enter it; tap a file to open it with Android's resolver.
  **Open with** lets the user choose a different installed app; that preference
  is remembered only for the same Drive-relative file path.
- The three-dot item sheet has round **Copy**, **Move**, **Rename** and
  **Share** (files only) buttons, then **Open with**, **Favorite**, **Tags**,
  **Properties** and **Move to Trash**. Folders also provide **Change folder
  color**.
- Copy and Move destination choices are restricted to folders inside Drive.
  Existing names are never overwritten, a folder cannot be copied/moved into
  itself, and canonical-path checks reject traversal outside Drive.
- Drive Trash is `/sdcard/Drive/Trash/`. Moving there is reversible by using
  Move; **Empty Trash** is the explicit permanent-delete action and asks for
  confirmation.
- Properties shows type, exact Drive-relative location, size, tags, and an Open
  action.

### Files limits

- No cloud storage, device pairing, or transfer is implemented. **Sync now**
  deliberately reports that no trusted paired device is available.
- It does not browse arbitrary storage outside `Drive/`, silently overwrite,
  create files/folders, or permanently delete except through confirmed
  **Empty Trash**.

## Photos — complete

### Default gallery and editing

- The app registers as a gallery (`APP_GALLERY`) and for `VIEW` / camera
  `REVIEW` of `image/*` and `video/*`, so it can be chosen as the default
  photos app. A Gallery (MediaStore) item opens in the full viewer with its
  filmstrip; Back returns to the calling app. Anything else (for example a
  chat attachment) opens in a single-item viewer with Share.
- The viewer's **Edit** (photos only, not videos or Hidden items) reuses the
  scanner tools: **Crop & straighten** (corner editor with magnifier),
  rotate left/right, and **Markup** (colours, custom colour, brush size, Undo).
  The editor's bottom island holds the tools and a Save icon (disabled until
  something changes) that opens a bottom sheet with **Save**, **Save as copy**
  and **Discard changes**. **Save copy** writes `<name>_edited.jpg` beside the original
  with the same date and copied EXIF date/camera/location. **Save** replaces
  the original after Android's own modify-consent prompt; the edit is fully
  rendered and encoded before the original is opened, so a failure leaves it
  intact. Its favorite, collections and location stay with it (faces and
  labels are re-analysed, since a crop or rotation moves them). Both save at full resolution (up to 8192 px, `largeHeap`).

### Gallery and search

- Reads public images and videos from MediaStore, including Camera, Pictures,
  and screenshot paths. Videos have a thumbnail, Videos collection, viewer
  navigation, native playback, and fullscreen control.
- Pull down to refresh Gallery, Collections, or the active Photos filter.
- Gallery tools can hide Screenshots and locally classified Documents from the
  main Gallery view only. The originals remain on-device and in their own
  collections.
- Search matches filename, folder/path, local English AI labels, named people,
  and cached nearby place names. It does not upload a photo to search.
- While a Gallery search is active, the top-right orange eraser is a direct
  **Clear search** action.

### Collections

- **People** and **Documents** start local analysis only after an explicit
  request. Analysis can be paused. It uses bundled local models and does not
  run as a background service or upload media.
- People groups can be renamed and manually combined; a recent combine can be
  undone briefly. Each person has a pencil (Rename · Choose face · Forget this
  person). Forgetting hides someone from People, search and Help organize on
  every device without losing their faces; People's History (top right) lists
  forgotten and combined people with Restore.
- **Folders**: Gallery shows Camera, Screenshots and photos from the computer. Every
  other photo folder (Viber, Messenger, Download…) is asked about once in Help
  organize; Yes shows it, backs it up and makes it an album. Settings › Gallery ›
  Folders switches answered folders on or off. Files folders (Drive, SyncThing,
  Documents, USB) are never offered.
- **Help organize** is a page of question cards answered in place, ending with
  how many are left, or "Thanks, no more questions for now! That's it." Two
  names that disagree about a face become a card on every device; a name always
  beats a guess ("Person 41") without asking.
- **Screenshots**, **Videos**, **Favorites**, **Hidden**, **Map** and
  **Trash** are real filters. Trash is Android's own trash, queried separately
  because trashed media is deliberately absent from the ordinary listing; it
  holds deleted photos for 30 days. With nothing selected the header carries a
  red Empty trash; with a selection the island offers only Restore and Empty
  trash, and emptying asks first. Android confirms both itself as well. The Collections tools can hide People and Documents cards from the
  Collections screen without deleting their data.
- Tap `+` to create **My collections**. Empty and duplicate names are rejected.
  A collection cover is its first available member; an empty one shows the
  collection icon.
- Long-press a personal collection to select it, then use the delete control.
  Deleting a collection removes only that collection and its memberships; its
  photos and videos stay in Gallery and on the device.
- Use multi-selection or the viewer to **Add to collection**. When viewing a
  named collection, the same routes offer **Remove from this collection**;
  this removes membership only.

### Hidden

- Hidden is protected by biometrics or the phone screen lock.
- Before removing a public original, the app creates a verified private copy,
  then Android presents its required removal confirmation. Cancelling removes
  the staged private copy instead.
- From Hidden, select items and choose **Restore**. This is the only path that
  returns a hidden item to public media storage.

### Selection and timeline gestures

- Long-press a thumbnail to enter multi-select, then drag across thumbnails to
  add items. Holding near the top/bottom edge keeps selecting while the grid
  auto-scrolls.
- The selection island offers Share, Favorite/unfavorite, Add to collection
  (or Remove in a collection), Move to Trash, and Move to Hidden. In Trash it
  offers Restore and Empty trash instead.
- Photo Trash uses Android's media confirmation. It is not a silent or
  permanent deletion action.
- Pinch out for broader date groups/smaller thumbnails and pinch in for finer
  groups: Week (2 columns), Month (3), and Year (6).
- The centered date pill follows the visible period. On large libraries, touch
  or drag the narrow right edge for fast scrolling; it shows the target period
  and does not select photos.

### Viewer, information, and zoom

- Tap a thumbnail to open the viewer. Swipe horizontally for previous/next
  media within the active Gallery or collection filter; tap/drag the bottom
  filmstrip to jump quickly between media.
- The viewer action island contains Share, Details, Favorite, Add/Remove from
  collection, Restore for Hidden media, and Edit (photos only). Its handle collapses/expands the
  actions.
- Swipe a photo upward for **Details**. Details shows name, MediaStore path,
  date/time, size, local English AI labels, and named people.
- Pinch a photo to zoom up to 5×, pan while zoomed, and double-tap to toggle
  2× zoom/reset. Back resets zoom before leaving the viewer.
- Videos play through Android's native `VideoView` and offer fullscreen.

### Map and location details

- The map reads embedded EXIF GPS from images and ISO-6709 coordinates from
  videos only after the dedicated media-location permission is granted. It
  never requests live phone location.
- It caches a nearby main place name locally for Details and photo search.
  Details contains place, coordinates, a static map preview (no pan/zoom; a
  tap opens the Map collection at that pin), and **Show on map**.
- Map pins use the app accent colour. Tapping a pin opens the photo card;
  **Map app** passes the coordinate to an installed external maps app.
- The Map screen intentionally hides Gallery/Search controls. Returning to
  Collections fades them up from the bottom. Map startup uses an indeterminate
  animated message until the map style is ready, rather than a false numeric
  percentage.
- Map tiles and reverse place lookup require internet, but coordinates and
  cached place names remain local.

### Photos limits

- Deferred: custom-collection rename, edit-date, metadata export/sync, Android
  Trash browsing, map marker clustering, cloud AI/backup, automatic
  destructive organisation, and live location tracking.
- The Xiaomi camera's own thumbnail may still open the MIUI Gallery even when
  Tetra is the default gallery; that is hard-wired by MIUI.

## Document Scanner — complete

Home → red tools group → **Scanner**. Details and design notes:
`DOCUMENT_SCANNER.md`.

- **Camera**: black chrome above and below a 3:4 viewfinder — the sensor's own
  shape, fitted rather than cropped, so what you line the page up against is
  what gets captured. Live page detection (OpenCV, on-device) with white corner
  dots; a found outline is held through a bad frame instead of blinking out.
  Optional **Auto capture** fires 2.5 s after the outline is stable, and never
  on a held outline. Flash toggle. The camera stays open for page after page;
  the page-count thumbnail opens the document.
- **What counts as a page**: it has to cover the middle of the view (you point
  the camera at what you want), be convex with four honest corners, stay inside
  the frame, and be brighter inside than just beyond its own edge. Torn and
  crumpled edges are cut where the straight edge ran, because each side is
  fitted to the outline along it rather than taken from two corners.
- **Auto fix** (per page, on by default): the page is straightened to the
  outline, then the **letters decide each edge** — nothing written near it and
  the cut steps 1.5% into the paper, letters close to it and the cut goes
  outside instead, leaving them room. Any band of table the outline still kept
  is trimmed off, the resampling is sharpened back, and only a genuine gap is
  painted, flat, in the paper's own colour. A page whose crop came out right is
  left exactly as it was. The header's wand toggles it per page.
- **Document viewer** (gallery-style): page pager, numbered filmstrip with
  **+** to add a page, and an actions island:
  - **Retake** replaces that page in place.
  - **Adjust**: Crop & straighten (drag corners, gridded magnifier in the
    opposite corner), rotate left/right, Reset.
  - **Filters**: Original, Fix lighting, Sharpen ink, B&W, **Match pages** (one
    shared paper tone for all pages) and Apply to all. All but Original flatten
    uneven lighting first, which is what takes crease shadows and lamp gradients
    out. B&W finds its ink/paper line in the page's own brightness, so grey
    thermal print and pencil survive it.
  - **Pinch to zoom** on a page, up to 8×, to check the small print; panning
    stops at the page's edges and the pager lets go while you are zoomed in.
  - **Pages**: numbered grid; long-press and drag to reorder.
  - **Save**: one multi-page A4-width PDF in
    `Files › Documents › Scanned Documents`, never overwriting (`name (2).pdf`).
    Pages are written at **300 dpi**, or 200 for roughly half the file —
    Settings › PDF scanner. A saved scan also asks Sync to catch up, a moment
    later.
  - Pull the island handle up for **manual painting**: swatches sampled from
    the page's paper (lit, typical, light shadow, shadow), white, black, a
    custom colour, brush size and Undo.
- Leaving the document asks before discarding unsaved pages.

## Sync — complete for Photos and Files

The protocol, its reasoning and what it deliberately leaves out are in
[SYNC_PLAN.md](SYNC_PLAN.md); this is what the phone offers.

- **Pair once** by scanning the computer's QR code in Sync. The connection is
  checked against that code's certificate fingerprint every time.
- **Back up now** sends every photo and video the computer does not have, each
  verified there by SHA-256 before it is kept. Nothing on this phone is
  changed or deleted.
- The same run carries **everything the user made**: document answers,
  favorites, collections and their membership, search labels, people's names
  and the face → person grouping, and the Files module's tags, favorites,
  folder colours and recents. Newest edit wins per record, both ways.
- **Files themselves** travel too, by path. A file whose bytes the computer
  already holds under a path the phone has since left is followed as a move —
  which is how a move into `Drive/Trash/` arrives as a move into Trash rather
  than a second copy.
- **Pause** beside the progress bar or in the notification; it suspends
  between files, so a half-sent photo is never left behind.
- On a Xiaomi with focus notifications the backup also shows in the **Dynamic
  Island** — a pill counting up, a card with the progress bar. Everywhere else
  the extra is ignored and the ordinary notification is unchanged.
- **Syncing happens on its own** when the app opens and after a scan is saved.
  It is easy to talk out of: no paired computer, a backup already running, a
  metered connection, or a sync less than fifteen minutes ago and it does not
  happen. A VPN is looked past to the network underneath it.
- A finished sync refreshes the pages it changed.
- **Settings that describe the library** cross (hide Screenshots, hide
  Documents, hide an album). Settings that decide what a device should *do* —
  running analysis, search quality — stay local on purpose.

### Sync limits

- The phone is the client: a file or photo created on the computer waits for
  the phone to ask, and opening the desktop app cannot start a sync.
- One paired computer at a time.
- Hidden is not synced yet.

## Codes — complete

Home → red tools group → **Codes** (implementation: `CodeScreens.kt`,
`PaymentCodes.kt`).

- **Codes** mode scans QR codes and barcodes continuously (ML Kit, bundled,
  offline) and opens a bottom sheet: **Open in browser** (http/https only),
  **Copy**, **Share**. A dismissed code is ignored for 3 s.
- **Text** mode recognises text on shutter into selectable text with **Copy
  all** / **Share**. ML Kit has no Greek model: Latin text and numbers only.
- **RF payment codes** (ISO 11649, checksum-validated) found in a code or in
  recognised text get a dedicated sheet with **Copy payment code** / **Share**;
  the copied code has no spaces.
