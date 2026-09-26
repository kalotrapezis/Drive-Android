# 26 September — 0.3.0-beta.1 built, not published

Built today: **Notes** in both apps (SYNC_PLAN D7), a drive as a folder album with its own Screenshots/Documents and
Delete to the purgatory (D5b), the PC sidebar as one entry per part with bottom islands (Photos, Files, Notes, T7,
Devices), a two-column tablet home with a yellow Notes group, Android sync every 30 min on Wi-Fi (`SyncJob`), a
note changed on the PC nudges the devices, split screen no longer resets the app to Home.

**Fixed: a photo trashed on the PC came back** — `/have` now answers `declined` for what was deleted here on purpose
(`deleted_here`, filled by the PC's Trash and a drive's Delete; cleared when the photo is back in the library). A
device neither re-sends it nor counts it as safe on the computer, so a Move never lets its copy go because of it. Still
open: whether a device should trash its own copy (Both-ways trash, D6).

**Trap found today**: the first import swept 85 notes that were in the old app's Trash (their dates were months old);
restored from `~/.local/share/Notes`, and an imported trashed note now gets its 30 days from moving in. The tablet's
`deletions.json` had to be cleaned too, or its next sync would have deleted them again — a tombstone travels.

State: PC runs 0.2.0-alpha.10 (installed); the beta .deb/AppImage are in `Drive/desktop/release/`; the tablet has the
latest debug build; the phone has not had any of today's builds (not plugged in). Release APK: `app-arm64-v8a-release.apk`
(installing it over a debug build needs an uninstall, which wipes app data — keep debug on the two devices).

---


# Evening of 25 September — beta from here; next: a Notes applet

The user starts using Tetra for real now ("most of it works fine"). Tomorrow: **a Notes applet**, then beta.

State: both repos pushed on `bidirectional-sync`; PC runs the installed 0.2.0-alpha.4 .deb (16:13 build); phone and
tablet run the latest debug build. Read SYNC_PLAN **D3 built**, **D6 built**, **Move, as agreed and built** first —
the rules there were each asked for by the user; do not change what syncs, shows or deletes without asking.

What was done today, in one line each:
- Storage drive as a library location; Free space to T7 (read back, then deleted — a Trash frees nothing); 2,795
  photos (21 GB) live on T7-TEO; the old Trash copies were let go (Trash 43 → 22 GB, disk 94 → 92 %).
- Move: always keeps a window (default 1 month + favorites), dialog with numbers first, photos and files; the
  tablet went from ~4,500 photos to ~410. Deletes, never trashes, what the computer confirmed.
- Trash → Purgatory → gone (30 + 30 days, purgatory on the PC or a chosen drive; now on T7). History page.
- Folder albums are folders (Remove moves the file; Move to folder); On anywhere in a two-way chain is On everywhere.
- Map: red pins, group grid panel. Devices: copies colours by halving, holdings on cards, this PC's counts,
  storage drive counted as in the library; the same overview on the phones' Sync page.
- Fixed today: fast-scroll strip took Empty Trash's tap; >2,000 photos per Android request; a device's holdings
  now drop at once after a complete (numbered) inventory; empty-looking collections on a Move device are hidden.
- **Trap**: a syntax error in `main.js` shipped once (no window, no sync). `test/syntax.test.js` parses every file
  the package runs now — keep it. Always check the PC is running the build you think (`stat` the dpkg list).

Still open: Both-ways trash/restore mirroring; Hidden mirrored to the PC; long-press deselects without a drag;
Trash lists oldest first; first-run guide (download → pair by QR → rules card by card → Sync now); showing the
computer's photos inside a collection on a Move device.

---

# Morning of 25 September — Move is built, test it together at noon

Built on the computer (SYNC_PLAN "D3, built"): a photo's **location** (this PC or a storage drive), opening from
the drive or "Plug in T7-TEO", a drive card with **Backup / Storage**, **Offload on (a line) / off (a window)**,
copies required and favorites, the **"Free 21 GB?"** dialog with *Try 10 first*, notifications and a tray item,
and the copies bar with one band per count. 55 desktop tests. Built **0.2.0-alpha.4** (.deb + AppImage in
`desktop/release/`), not released, not committed.

**The old alpha.3 must never run after a Move**: its scan reads moved photos as deleted (and forgets their faces).
Install alpha.4 before moving anything, and do not go back.

Noon test, in order:

```bash
cp ~/.local/share/local-drive-desktop/library.db ~/Έγγραφα/Claude/Coding/Drive-Android-backups/library.db.before-move-2026-09-25
sudo dpkg -i ~/Έγγραφα/Claude/Coding/Drive/desktop/release/local-drive-desktop_0.2.0-alpha.4_amd64.deb
```

1. Quit Tetra from the tray, start the new one. T7-TEO plugged in. Devices → T7 card → **Storage**, Offload
   off, keep the last 1 year. The card should say ~2,664 photos, ~21 GB.
2. **Free 21 GB… → Try 10 first.** Check: 10 files in the system Trash, the 10 still in Photos (oldest, 2008),
   they open (from `/mnt/T7`), Details says "On T7-TEO".
3. Unmount/unplug T7: open one of them → "Plug in T7-TEO to open this" over the thumbnail. Plug back in.
4. Sync the phone and the tablet: they must not send those 10 back, and the Devices bar must not call them missing.
5. Restore one from the Trash (Photos → Trash): it is back on this PC (Details loses "On T7-TEO").
6. If all is right: the full Yes. Wait a minute for the notification path (tray item "Free …").

Also this morning:
- **Map** (desktop): red pins drawn by the map itself (the photo markers lagged every pan), light style, a group
  opens a grid panel from the bottom and the viewer pages only that group. Phone pins are red too.
- **A folder album is a folder** (both apps): in a collection that is an included folder, Remove *moves the file*
  out of the folder (a picker, Camera first); **Move to folder…** is in the selection bar (and the desktop viewer).
  Never overwrites (a taken name becomes "(2)"). Desktop: `folders.move`, test in folders.test.js. Phone:
  `movePhotosToFolder` (FolderAlbums.kt) after Android's write consent; the key includes the folder, so
  `rekeyPhoto(…, sameContent = true)` carries favorites, collections, location, faces and labels to the new key.
  Not tested on a device yet. Installed on the tablet; the phone was not plugged in.
- **Folder rule over Both ways** (user, 2026-09-25): a folder that is On on any device in a two-way chain is On on
  every device in it; a No given on a device does not hide it there. A device that only sends keeps its own answers.
  The folder's album is the yes that travels (`folderChoices` on the phone, `Folders.choices` with `bothWays` on the
  computer). The tablet had hidden DCIM, so the synced DCIM album arrived empty and its member was dropped for good;
  METADATA_EPOCH 6 pulls everything once more. Open: how an Off travels (today only deleting the album does).
  A device still *sends* only the folders it shows — I changed that to "every folder" by mistake and undid it.

Found on the phone, 25 September afternoon (not fixed yet):
- **Long-press selects, then deselects on release**; it only sticks if the finger moves a little.
- **Photos Trash lists oldest first** (user confirmed: "the view counts backwards, the latest is at the bottom"), so
  what was just trashed is at the bottom and looks missing.
- **Empty Trash "did not work"**: the fast-scroll strip covered the whole right edge, top bar included, and took the
  tap. Fixed (the strip is only as tall as its track) and installed on the phone; the user then emptied the
  phone's Photos Trash on purpose (broken copies from an earlier buggy sync). It deletes for good with no Android
  prompt (Media management) — Purgatory (SYNC_PLAN D6) should come before this is trusted with real photos. The
  Empty button still has no accessibility label.
- Asked: **a history — a manifest of what goes and what comes** (every transfer, trash, purgatory and deletion,
  per device), because sync rules are only safe if they can be read back.

---

# Handoff — night of 24 September 2026

Read `SYNC_PLAN.md` for *why* anything is the way it is — **section D3** is the design for everything below. This is
the first ten minutes, the state of the devices, and the traps that cost time today.

## Where everything is

| | |
|---|---|
| **Phone** | `~/Έγγραφα/Claude/Coding/Drive-Android`, branch `bidirectional-sync`, merged into `alpha` (PR #5) |
| **Computer** | `~/Έγγραφα/Claude/Coding/Drive` (Electron app in `desktop/`), same branch, merged into `electron-desktop` (PR #4) |
| **Released** | **0.2.0-alpha.3** on GitHub, both repos (APK; AppImage + .deb) |
| **Installed** | PC: the .deb (`/opt/Tetra`, menu entry "Tetra"). Phone and tablet: **debug** builds of alpha.3 — keep installing debug over them; a release APK needs an uninstall, which wipes app data |
| **Folders** | `~/Tetra/Photos`, `~/Tetra/Files` on the PC; `/sdcard/Tetra` on phone and tablet (renamed from `Drive` tonight, `desktop/home.js`, `TetraFolder` in DriveFiles.kt) |
| **T7 drive** | `T7-TEO`, ext4, UUID `f0544ced-…`, mounted `/mnt/T7`. **Full verified copy** under `/mnt/T7/Tetra/Photos` (3,969, 29 GB) and `/mnt/T7/Tetra/Files` (44, 103 MB), done 22:26. Four 0-byte `*.jpg.part` from 15:10 are old leftovers, harmless |
| **Backups** | `Drive-Android-backups/` — tonight's `2026-09-24-evening-*`, `2026-09-24-before-folders/`, and `~/.local/share/local-drive-desktop/library.db.before-tetra-folder` |

Build and test:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=$HOME/Android/Sdk
./gradlew testDebugUnitTest assembleDebug -q
~/Android/Sdk/platform-tools/adb -s 208c8192 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk   # phone
~/Android/Sdk/platform-tools/adb -s 971f6b37 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk   # tablet
```

Desktop: `cd desktop && npm test` (54 tests), `npm run dist` for the AppImage and .deb. The user runs the
**installed** app now — to test a dev build, quit Tetra from the tray first (it lives in the tray; closing the
window does not quit it, and a second copy exits at once).

## Next session, in the user's words

> complete the hard drive backup and move functionality, implement and test the move, the notification system
> for "free this much space" — "Yes" and they are gone. Could we use the hard drive as a library in the app as a
> new location so I can continue seeing and opening all the images, videos and files?

So, in order (all designed in SYNC_PLAN D3 — nothing here is hard-coded, every choice is a setting):

1. **The drive as a library location.** Each photo gets a location: this PC, or a storage drive. Thumbnails stay
   in the PC's cache, so the grid, People, collections and search keep working; opening reads from the drive when it
   is plugged in and says "Plug in T7-TEO" when not. Files: the drive is a location in Files. This comes first —
   without it a Move makes photos disappear from view.
2. **The storage role for a drive** (next to Backup), and **"do you have it?" answered for the library** (PC *or*
   its storage drive), or every device re-sends what was moved — the loop the user spotted.
3. **Move / Offload on the sender (the PC):** release to the drive what is verified there (read back, not just
   receipted), oldest first — either *keep the disk under N %* (Offload on, a slider) or *keep a year / month /
   week* (Offload off). The PC's copy goes to the system Trash, never a hard delete.
4. **The notification:** *"Free 22 GB — 3,100 photos older than a year are safe on T7-TEO. Yes?"* — Yes and they
   go. Plus a full-disk warning that is always shown (nothing watches free space today). The PC had ~80 GB free
   before syncing, 57 GB tonight.
5. **Test Move for real**, on a handful of photos first, then watch the phone and tablet not send them back.

Also:
- **The copies bar on the Devices page is fixed at three buckets** ("One copy · Two places · Three or more"). With
  the T7 added there are four places (PC, phone, tablet, T7) and every photo is in all four, which the bar cannot
  show. It should have one bucket per count, 1…N, where N grows with the devices that hold photos (the user noticed
  it right after adding the T7). `overview()` in sync.js already returns a row per `copies` value; the fixed three
  are in the Devices page's rendering. One-copy stays red and two amber — the colours mean risk, not count.
- The **T7's card on the Devices page** still offers "T7-TEO → this PC" and "Both ways", which do nothing for a
  drive — the Add-a-drive guide was fixed tonight, the card was not.
- A **folder-emblem catalog** (finance, medical, education, receipts…) on the personalisation card, next to
  colour — asked for, deferred.
- The AppImage needs **libfuse2** on Ubuntu 24.04+; a static-runtime AppImage would not.
- The phone's post-rename sync check did not finish (the phone was unplugged): compare `/sdcard/Tetra` with
  `~/Tetra/Files` next time it is plugged in.

## What tonight changed, shortest possible

- **ChatGPT's session reviewed and repaired**: names beat guesses again, no guess-vs-guess cards, combined people
  stay combined (a deleted "Εγώ" was holding six faces), Help organize is a page of cards.
- **People**: pencil menu (Rename · Choose face · Forget), History with Restore, forgetting syncs.
- **Folders** as user collections, asked about once, on both apps; files folders never offered.
- **Desktop**: scanning and analysis on a **worker thread**, the tray, bigger dialogs, system folders (Documents,
  Scanned Documents) with emblem icons on both apps, drive backups of photos and files (auto when plugged in).
- **The Devices page froze the app**: a missing index made its overview 1.2 s a call, asked every 3 s. Fixed
  (38 ms), with a test.
- **The Tetra folder** on every device, by renames.

## Traps that cost time today

- **sharp segfaults outside Electron's main process** on this machine (utility process, ELECTRON_RUN_AS_NODE) —
  that is why `analyzer.js` is a `worker_threads` Worker. Do not move it back into a process.
- **Every new desktop module must be added to `package.json` build.files** — analyzer, folders, drives and home
  were each missing once, and the AppImage would not have started. Check with `npx asar list`.
- **Freezes: pause the stuck process, do not guess.** `kill -USR1 <pid>` opens the inspector on 9229; a
  `Debugger.pause` over CDP names the function (that is how the Devices overview was found).
- **A file's time stamp through `utimes` comes back a hair early** — compare within 2 ms, not to the millisecond.
- **The scan refuses a missing Photos folder, or an empty one where 20+ photos were known**, instead of reading it
  as everything deleted. Keep that guard when drives become library locations — an unplugged storage drive must
  never read as deleted either.
- `pkill -f <pattern>` kills the shell that typed it when the pattern is in its own command line. Kill by PID.
