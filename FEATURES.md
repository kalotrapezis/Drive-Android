# Local Drive: completed Files and Photos features

Updated: 2026-09-20. This is the authoritative guide to what is implemented
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

### Open and manage an item

- Tap a folder to enter it; tap a file to open it with Android's resolver.
  **Open with** lets the user choose a different installed app; that preference
  is remembered only for the same Drive-relative file path.
- The three-dot item sheet provides **Copy**, **Move**, **Rename**,
  **Properties**, **Favorite**, **Tags**, and **Move to Trash**. Folders also
  provide **Change folder color**.
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

## Photos — complete, except editing

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

### Collections

- **People** and **Documents** start local analysis only after an explicit
  request. Analysis can be paused. It uses bundled local models and does not
  run as a background service or upload media.
- People groups can be renamed and manually combined; a recent combine can be
  undone briefly. **Help organize** shows items needing a review.
- **Screenshots**, **Videos**, **Favorites**, **Hidden**, and **Map** are real
  filters. The Collections tools can hide People and Documents cards from the
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
  (or Remove in a collection), Move to Trash, and Move to Hidden.
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
  collection, and Restore for Hidden media. Its handle collapses/expands the
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
  Details contains place, coordinates, an accent-pin map preview, and **Show
  on map**.
- Map pins use the app accent colour. Tapping a pin opens the photo card;
  **Map app** passes the coordinate to an installed external maps app.
- The Map screen intentionally hides Gallery/Search controls. Returning to
  Collections fades them up from the bottom. Map startup uses an indeterminate
  animated message until the map style is ready, rather than a false numeric
  percentage.
- Map tiles and reverse place lookup require internet, but coordinates and
  cached place names remain local.

### Photos limits and deferred editing

- The visible **Edit** viewer button is a placeholder: it does not alter the
  image, video, date, or file. Photo editing is deliberately deferred to a
  later stage.
- Also deferred: custom-collection rename, edit-date, metadata export/sync,
  Android Trash browsing, map marker clustering, cloud AI/backup, automatic
  destructive organisation, and live location tracking.
- Document Scanner and PDF Manager are the next planned module; see
  `DOCUMENT_SCANNER_PDF_MANAGER.md`. No scanner implementation exists yet.
