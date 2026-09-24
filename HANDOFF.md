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
