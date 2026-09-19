# Drive Android — product roadmap

Updated: 2026-09-17. `Plan.md` preserves the original product wishlist. This
file turns it into one implementable Android product without importing the
desktop app's wired-beta scope as Android code.

## Product boundary

Drive Android is the Android companion for local files, photos and document
scans. Its fixed file workspace is `/sdcard/Drive/` in primary
shared storage. Photos remain in their real media locations: `DCIM/Camera/`
and the device's Screenshots collection. The app must not copy or move photos
into an app folder merely to display or later sync them.

Android 11+ all-files access is required for the fixed `Drive/` root. The app
opens the system permission screen once; it never asks the user to choose a
location. Listing photo collections continues to use media permission and
MediaStore; any photo edit, move or deletion must use Android's confirmation.

The desktop project's transferable contracts are:

- previews do not write, move or delete;
- any copy is verified before it is recorded as complete;
- source cleanup is a separate, explicit review, never the side effect of a
  sync;
- history/receipts distinguish what is live now from what was last known;
- device identity is stable and a displayed USB name alone is not trust;
- conflicts preserve both originals until the user decides.

The desktop C++/Qt/KIO engine, its current MTP wired-beta UI, its local API and
its package plan are not copied into this Android application.

## Smallest useful Android release

1. With approved all-files access, create/use fixed `/sdcard/Drive/`
   and list its direct children read-only.
2. Show that folder's files and subfolders, including an empty state and an
   explicit permission-lost state.
3. With explicit photo permission, show real `DCIM/` and Screenshots media in
   a separate, read-only Photos module.
4. Keep local operation history/settings in private app storage; a portable
   metadata format inside `Drive/` is a later sync-protocol decision.

Success: after force-stop/restart, `/sdcard/Drive/` and its visible
contents remain usable, real camera/screenshot files are visible without
duplication, and a denied/revoked permission fails safely and clearly.

## Subsequent slices

### File-manager baseline

Search, list/grid, sorting, properties, bookmarks, tags and bulk actions.
Use the platform file APIs and native Android sharing/open-with facilities.

### Photos and scanning

The home screen is a launcher, not persistent navigation. Opening Drive,
Photos, Sync or New/Scan enters a full module with its own top bar and a
contextual bottom action bar; Home/Back returns to the launcher. This avoids
one global bar trying to contain every module's actions.

The Photos module is a MediaStore timeline over the real photo locations.
Its contextual bottom bar is `Timeline`, `Collections`, and `More`; there is
no duplicate Collections section at the top and no permanent Select button.
The initial system collections are Videos, Screenshots and Trash. Trash is a
collection inside Photos, not a separate settings page; moving an item there
uses Android's required media confirmation and is never a permanent delete by
default.

In the timeline, a long press starts multi-select and dragging across
thumbnails extends that selection. Pinch controls the timeline density and
time scale: zooming out reveals smaller thumbnails and broader week, month,
then year groupings; zooming in returns toward the finer grouping. The viewer
has Share, Favorite, Move to Trash and More; its More sheet holds Details,
Add to collection and Use as cover. Editing capture date is within Details.
Dragging the image upward opens its details, and a draggable thumbnail
filmstrip remains at the bottom for fast browsing.

Add camera document capture, page detection and PDF export only after the
file-manager baseline works. Local classification, OCR, face clustering and
review queues are specified in `AI_COLLECTIONS_ARCHITECTURE.md`; suggestions
must never move/delete files automatically.

### Feature triage from Photos, Drive and iCloud Drive

Adopt in the local product, in this order:

1. **Photos core:** real system collections (Camera, Screenshots, Videos,
   Trash), named local collections, Favorite, multi-select Share and
   Android-confirmed Trash/Restore. The timeline and gesture-led selection are
   already the interaction base.
2. **Find and understand:** filename/date/path filters, Recently added, a
   metadata Details sheet and embedded-GPS map search. Map results stay local;
   do not infer a location from image content or upload an image to search.
3. **Privacy:** an opt-in, biometric-gated Hidden collection only after there
   is an encrypted local metadata model and a recovery story. It must not be a
   superficial visual hide.
4. **Drive core:** Recents, local Favorites shown with a heart (not a star), tags, descriptions, shortcuts (not
   duplicate copies), search/sort, file properties, native share/open-with,
   reversible Trash and a transparent transfer/conflict queue. Local files are
   inherently available offline, so a cloud-style `Make available offline`
   switch is unnecessary.
5. **Documents and local AI:** camera scan, crop/rotate/filter, multi-page
   PDF/JPG export and an explicit destination under `/sdcard/Drive/`.
   Documents-as-Photos, OCR, automatic filing, image classification and People
   remain local-only suggestions with the review contract in
   `AI_COLLECTIONS_ARCHITECTURE.md`.

Do not import cloud-account collaboration, public links, email permissions,
remote AI/face recognition, automatic deletion, or opaque cloud backups. Those
features depend on a service account and create a different privacy and safety
contract from Local Drive.

### Backup and USB

Support a user-selected removable destination and a deliberate verified copy
preview. A connected drive is not enough authority for automatic backup:
the app must retain a user-approved storage identity and surface ambiguity.

### Sync with Linux

Define and approve a paired, authenticated protocol with the desktop project
before network code. Exchange metadata before file content, show the pending
queue, preserve conflicts, and never infer live availability from stale history.
Wi-Fi sync, Google Drive interoperability, cross-network relay, and two-way
deletion are intentionally deferred.

#### Automatic device discovery and reconnection (planned protocol)

Discovery may be automatic; trust and content transfer are not automatic by
default. Each installed app generates a random `deviceId` and a signing/TLS key
pair stored in the platform keystore. The paired record stores the device's
user-visible name, stable `deviceId`, public-key fingerprint, capabilities and
last-known state. The MAC address is neither available to normal Android apps
nor stable enough to be a trusted identifier; it is never a pairing key.

On every active network, an authenticated Local Drive service advertises
`_localdrive._tcp.local` through DNS-SD/mDNS with a display name, protocol
version, `deviceId`, current port and short public-key fingerprint. A peer
browses and resolves that service after a network/IP change, then reconnects
with mutual TLS and a nonce challenge. The current IP and port are therefore
resolved immediately before connection rather than remembered as identity.

Initial pairing is deliberate: one device displays a short-lived QR/code
containing an ephemeral pairing token and its public-key fingerprint; the other
device displays the matching name/fingerprint before the user accepts. Only
then are the peer key and `deviceId` persisted. Ordinary discovery packets are
not authority to pair, execute a transfer, or delete anything.

- Same trusted device and matching fingerprint: show **Online now** and update
  its endpoint silently.
- Same `deviceId` but different key: show **Identity changed — pair again**;
  never silently trust it.
- Not currently discoverable: show **Last seen**, not Online.
- Router multicast/client isolation: show a clear discovery problem and offer a
  deliberate QR/manual endpoint recovery flow; never scan a subnet broadly.
- Android performs discovery while the feature is in use, respecting the
  platform local-network permission and battery rules. A later Wi-Fi Direct
  fallback can cover nearby/off-network devices, but it is not part of the
  initial Wi-Fi sync slice.

Acceptance for this protocol slice:

1. Pair phone and laptop once, then change DHCP address/SSID while both are on
   the same reachable LAN; the app resolves the new endpoint with no IP entry.
2. A fake advertisement using the same display name, or the same `deviceId`
   with a new key, cannot connect or overwrite the saved peer.
3. A lost service moves from Online now to Last seen; it never silently becomes
   an error-free live connection.
4. A user can remove a paired device, which revokes its key/route but never
   deletes its local files, history or backups.

The fixed collection map will need no per-transfer folder question:

- phone files: `/sdcard/Drive/` ↔ desktop `LocalDrive/Drive/`;
- phone camera: `DCIM/Camera/` ↔ desktop `LocalDrive/Photos/`;
- phone screenshots: `Pictures/Screenshots/` ↔ desktop `LocalDrive/Photos/`.

The catalog records `camera` or `screenshot` with each photo. On return to the
phone it uses that collection to write to the canonical location. Read support
also recognises `DCIM/Screenshots/` as an existing OEM/legacy source. Unknown
photo origin requires review, not path guessing. Phone `Drive/` never
holds duplicate photo bytes.

## Non-goals for the first release

- a full replacement for Android's file manager;
- background polling every 5/10/15 minutes;
- automatic USB backup based only on a volume label;
- Google Photos clone, cloud replacement, remote AI, Takeout migration and
  cross-network sync;
- automatic deletion, duplicate deletion or automatic filing.

## Flower module identity

Home represents the whole flower; every sub-application is one coloured petal
with a simple, accessible symbol on it. The petal colour is a module identity,
not the only signal: every icon keeps a text label and `contentDescription`.
The same hue uses light/dark tonal variants so it remains legible in system
themes.

| Module | Petal | Symbol |
| --- | --- | --- |
| Photos / Gallery | red-rose | photo frame |
| Drive | blue | folder with sync arrows |
| Sync | green | two circular arrows |
| Scan | amber | document scanner |
| Notes | violet | note/pencil |
| Home | multi-petal | flower centre |

The first icon pass should use Android Vector Drawables/Material symbols inside
an adaptive icon, not raster artwork. The sketched petal-and-photo relationship
is the visual source; detailed adaptive-icon geometry is a dedicated design
slice after the core modules are usable.

## Desktop collection boundary (deferred)

The desktop app uses a separate physical structure: `LocalDrive/Drive/` for
ordinary files and `LocalDrive/Photos/` for synced photo bytes plus their
metadata/settings. It will
read the computer's real screenshot collection from the XDG Pictures directory
(`Pictures` or localized `Images`) and its `Screenshots/` child. Desktop
collection discovery and sync implementation are deferred; this Android app
does not create, copy, or reconcile those desktop directories.
