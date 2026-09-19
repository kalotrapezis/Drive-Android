# Continue Drive Android

Updated: 2026-09-19. Read `ROADMAP.md` and the original `Plan.md` first.

## UI checkpoint — 2026-09-19

- Installed and visually checked on Xiaomi 15 (`208c8192`): Home, Files,
  Collections, Screenshots and Gallery tools use the current island treatment.
- Home → Screenshots opens the real `Pictures/Screenshots` / `DCIM/Screenshots`
  MediaStore filter directly. It has a Files-style header (Back, SVG, large
  title), an initial first-month heading, and the sticky date pill fades/slides
  in only once the title header has scrolled away.
- Gallery tools persist two local-only filters: hide Screenshots and hide
  Documents from the main Gallery timeline. Dedicated collections remain
  available. The filter rule has a unit test.
- Collections now use a Files-style header and compact two-column system
  buttons; user-created collections are square cards with a real local cover
  when populated, otherwise an empty-collection graphic. The `+` creation
  sheet is functional.
- Android `Videos` remains an explicit placeholder: the app queries only
  `MediaStore.Images.Media`, `PhotoFilter.Videos` is intentionally empty, and
  there is no video thumbnail or playback implementation. Add it as one slice:
  query `MediaStore.Video.Media`, preserve mime/duration in entries, render
  video thumbnails and open playback via Android's native video intent/player.

## Current state

- Git checkout: `codex/android-storage-baseline`; do not commit this checkpoint
  until it has passed an on-device permission test.
- Minimal Kotlin/Jetpack Compose app created: package
  `com.kalotrapezis.drive`, application name `Local Drive`, SDK 36/min 30,
  Java 17. It uses only AndroidX/Compose baseline dependencies.
- `Files` uses fixed `/sdcard/Drive/`. It requires Android 11+
  all-files access, opens its app-specific system Settings page when absent,
  re-checks on resume, creates `Drive` only if it does not exist,
  and lists direct children read-only via `File` APIs. Listing failure is an
  explicit error.
- `Photos` is read-only: it requests the Android-native media permission and
  queries MediaStore images in `DCIM/`, `Pictures/Screenshots/`, and
  `DCIM/Screenshots/`. It neither copies, moves, nor deletes media. Android 14
  labels full, selected-only, and denied access separately.
- The first navigation/UI slice is implemented: Home launches Drive or Photos;
  Back/Home returns to Home. Photos has a pinned `Timeline`, `Collections`,
  `More` action bar and groups real MediaStore images by date. Collections
  counts Screenshots from the loaded records and labels Videos/Trash as not
  indexed yet; More holds the permission and refresh actions.
- Timeline now renders native MediaStore thumbnails in a lazy grid. Pinch
  changes only one scale per gesture: Week (2 columns), Month (3, default),
  Year (6); More exposes the same scales as accessible buttons. Thumbnails are
  bounded native loads, not original decodes or an app-owned image cache.
- Tapping a timeline thumbnail opens a read-only viewer with a bounded,
  fit-to-screen decode of the original MediaStore source (not an upscaled
  thumbnail), compact chevron Back, date/time, a
  horizontally draggable thumbnail filmstrip that recentres on the opened
  image, native Share, and a Details bottom sheet (name, media path, date/time
  and size). The selected filmstrip image has an accent border. It follows the
  Android system light/dark mode and, on Android 12+, the system dynamic colour.
  It safely reports a missing selected URI after a refresh or permission change.
  Photo metadata is private app SQLite: Favorites, legacy Archive and named
  custom collections are currently implemented. Metadata never changes source
  media bytes or MediaStore fields. Archive is now a withdrawn legacy feature:
  do not extend it; replace it with the local AI collections design in
  `AI_COLLECTIONS_ARCHITECTURE.md` before the next Photos implementation slice.
- Android Auto Backup is disabled. Uninstalling the app clears this private metadata; it does not remove or alter the original DCIM/Screenshots media. Metadata export/sync is deliberately not implemented yet. Local AI classification, OCR, face embeddings, people/clusters and correction history are documented only in `AI_COLLECTIONS_ARCHITECTURE.md`; no model or background scan is present.
- Timeline is compact: its thumbnail grid uses one physical-pixel white gaps,
  no rounded tiles, and compact translucent top/bottom controls so photos use
  the available space. Long-press starts in-memory selection; dragging over
  photo cells adds them, tapping toggles while selection is active, and holding
  at the top/bottom edge autoscrolls while continuing to select. Back/Cancel
  clears it. The compact selection bar has real Share and Move-to-trash actions.
  Trash uses Android's confirmation request and is reversible; it is never a
  silent or permanent delete.
- Timeline overlays the active Week, Month or Year grouping while it scrolls.
  A narrow right-edge scrollbar jumps through large libraries and shows the
  target year while pressed or dragged. It uses the same filtered list as the
  active collection, so Collections no longer show unrelated timeline photos.
- Photos Timeline now uses an image-first, edge-to-edge layout: floating dark
  Home/Photos and date pills, a dark rounded module bar, and no inline date
  rows between thumbnails. It is inspired by the supplied gallery reference
  without copying cloud controls or Google branding. Device visual QA is
  recorded as blocked in `design-qa.md` until a device is connected.
- Pull down refreshes Photos Timeline, Collections and the active Drive Files
  folder using the existing local loaders. Returning to the foreground also
  refreshes Photos and an open Drive Files folder; no background scan runs.
- Home now opens dedicated Sync and Settings shells. They display the fixed
  collection map, real Android permission state and explicit `Not connected` /
  `No paired devices` states. They do not yet discover, pair, copy or delete.
- Drive Files uses a fixed top header island above the list/grid: chevron Back,
  ellipsized leaf folder title and list/grid control. It never renders a full
  pathname, duplicate Drive title or Refresh button. Every recent/file/folder
  has a three-dot action sheet. Copy, Move, Rename, Favorites, Properties and
  Trash use explicit modal steps; folders additionally offer app-private color
  and an honest `Sync now` no-paired-device state. File types use the requested
  icon colors, while folders remain neutral until their own color is chosen.
  File opening delegates directly to Android's chooser, leaving the system
  resolver to report a genuinely unsupported type.
- The user-facing module is `Files`, not `Drive`: its home header is Back,
  home SVG and `Recent files`. The bottom Files island is bottom-left and uses
  a translucent white selected state with black content in dark mode (the
  inverse in light mode); this is a glass approximation, not a fake backdrop
  blur implementation.
- Favorites and the file browser now share the Files home header logic: Back,
  contextual SVG and title. The browser deliberately has no Refresh control;
  pull-to-refresh is its single manual refresh affordance.
- Photos top controls use safe-area-aware, translucent adaptive islands: white
  vector icons/text on black in dark mode and black on white in light mode.
  Timeline date headers are spaced grid sections; the right-edge quick-scroll
  rail is invisible until touched and hides shortly after release.
- At the Timeline top, the active period is shown only in its centered sticky
  pill; the first inline date heading is intentionally omitted. Later period
  headings are centered with extra spacing. The temporary `Edits` bottom item
  is hidden; Gallery tools remain under the top More control. Sync and Settings
  use the same full-width safe-area header treatment above their first card.
- The desktop `Drive` project remains a separate, dirty C++/Qt worktree. Do
  not edit, reset or copy its implementation into this directory.

## Settled safety rules

- The Android file workspace is fixed at `/sdcard/Drive/` in primary
  shared storage. All-files access is permission, not a location choice.
- Photos stay in the actual `DCIM/` and Screenshots media collections. Read
  them through MediaStore; never duplicate them merely for display or sync.
- The desktop uses `LocalDrive/Drive/` plus `LocalDrive/Photos/` for synced
  photo bytes and metadata. Sync maps phone Camera and Screenshots to Photos,
  and writes returned items to `DCIM/Camera/` or `Pictures/Screenshots/` from
  their catalog collection. Unknown origins require review. The desktop's
  future Screenshots discovery resolves XDG Pictures/Images and `Screenshots/`;
  desktop discovery/sync remains deferred.
- Preview before consequential copy/restore work; preserve source files.
- Keep history truthful: label remembered device state separately from live
  connection state.
- Treat USB names as display labels, not identities or authorization.

## First implementation checkpoint

Implemented, pending fixed-root on-device acceptance. The earlier picker
checkpoint is superseded. Run the documented Gradle checks, then verify on a
physical device:

1. grant all-files access, confirm `/sdcard/Drive/` is created only
   when absent and its direct children list after force-stop/restart;
2. revoke all-files access in system settings and confirm the Files tab reports
   the missing permission safely, then grant it again;
3. deny, select-only, and fully allow Photos (Android 14+) and confirm the
   tab's label and MediaStore result follow the system decision;
4. confirm real camera and screenshot files appear without any duplicate copy.

Before adding sync, scanning, USB backup, tags or new libraries, run the
on-device checks above and the focused metadata/collections checklist in
`MANUAL_CHECKLIST.md`. The local SQLite metadata slice is implemented but has
not yet been accepted on a physical device.

## Drive Home / Files checkpoint

Implemented, pending device acceptance: Drive is a read-only browser rooted at
`/sdcard/Drive/`. It has an app-private, capped list of the 50 most
recently *successfully dispatched* files, list/grid browsing, nested-folder
navigation and Android native viewing through a non-exported `FileProvider`.
Path checks reject traversal, sibling-prefix and canonical symlink escapes.
There are deliberately no cloud or shared tabs. Favorites are private app
metadata. Every mutation validates canonical source and destination paths
against `/sdcard/Drive/`, blocks overwrites and self-descendant folder moves,
and never offers an Android location picker. Trash is a reversible move to
`/sdcard/Drive/Trash`; no permanent deletion is implemented. Run the titled
Drive section in `MANUAL_CHECKLIST.md` after granting all-files access; do not
use personal documents as test fixtures.

## Resolved product choices

1. First release is a standalone local Android file app; desktop pairing is
   deferred.
2. Use fixed `/sdcard/Drive/` through Android 11+ all-files access.
   Actual photos stay in `DCIM/Camera/` and Screenshots; screenshots are queried
   from their MediaStore paths, never copied into the app folder. The desktop's
   later Screenshots collection resolves XDG Pictures/Images and `Screenshots/`
   independently.
3. The existing `kalotrapezis/Drive-Android` remote is the checkout here.
4. Use Kotlin + Jetpack Compose, following the established Notes-Android
   build baseline.
5. UI is a launcher Home leading to full Drive, Photos, Sync and New/Scan
   modules. Each module owns its contextual bottom actions; there is no
   permanent global `Sync/Drive/Photos/New` navigation bar.
6. Photos uses `Timeline`, `Collections`, `More` as its bottom actions.
   Collections includes People, Documents, Videos, Screenshots and Trash.
   People and Documents are pending the local-only AI architecture; Archive is
   withdrawn. Trash is a Photo
   collection and means Android-confirmed move-to-trash, never default
   permanent deletion.
7. Photos selection is gesture-led: long-press enters selection and drag
   extends it, without a Select button. Pinch zoom changes the timeline's
   thumbnail density and grouping from week through month to year. In the
   viewer, upward drag opens Details; a bottom filmstrip scrubs images; More
   contains Details, Add to collection and Use as cover, while Edit date is
   inside Details.

## Explicitly deferred Photos work

- Map/location search using the photo's embedded GPS (city search and
  tappable photo pins) comes after the core Photos timeline and viewer.
- Do not add a mapping/geocoding dependency, network service, or background
  location collection before that slice is separately designed.
- Real Videos index, edit-date, metadata export/sync and collection rename/delete
  are deferred. Android Trash browsing remains separate from Android-confirmed
  move-to-trash; do not add a decorative/non-working action.
- Implement `AI_COLLECTIONS_ARCHITECTURE.md` in its stated order. Do not add a
  cloud AI API, automatic filing/deletion, face scan, or model dependency until
  the local data foundation and model/licence decision are accepted.
- Settled AI delivery boundary: bundle the first quantized classifier in the
  APK for offline first launch. On a trusted future peer, classification labels
  may sync by default; OCR and face/people metadata require separate opt-in.
