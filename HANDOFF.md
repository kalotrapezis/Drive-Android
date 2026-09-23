# Handoff — end of 23/24 September 2026

For whoever picks this up tomorrow, which is me. Read `SYNC_PLAN.md` for *why*
anything is the way it is; this is only what you need in the first ten minutes,
and the traps that cost time today.

## Where everything is

| | |
|---|---|
| **Phone** | `~/Έγγραφα/Claude/Coding/Drive-Android`, branch **`bidirectional-sync`**, pushed, clean |
| **Computer** | `~/Έγγραφα/Claude/Coding/Drive` (the Electron app is in `desktop/`), branch **`bidirectional-sync`**, pushed, clean |
| **Plan** | `SYNC_PLAN.md`, kept identical in both repos — copy it across after editing, it is not a symlink |
| **Releases** | [phone v0.1.0-alpha.1](https://github.com/kalotrapezis/Drive-Android/releases/tag/v0.1.0-alpha.1), [desktop v0.2.0-alpha.1](https://github.com/kalotrapezis/Drive/releases/tag/v0.2.0-alpha.1), both pre-release |
| **Backups** | `~/Έγγραφα/Claude/Coding/Drive-Android-backups/` — the phone's databases (23 Sept, verified), the desktop library before two-way, **and a copy of the release keystore** |

The phone has the current debug build installed, with everything from today.

## Running things

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug -q
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

The desktop: `cd desktop && npm start` (vite build + electron; takes ~30s before
it is listening). `npm test` for its 39 tests.

**Tests on the phone**: unit tests with `./gradlew testDebugUnitTest`. Device
tests with **install + `am instrument`, never `connectedAndroidTest`** — that
wiped the phone's app data once:

```bash
./gradlew assembleDebugAndroidTest -q
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.kalotrapezis.drive.IncomingFaceDeviceTest \
  com.kalotrapezis.drive.test/androidx.test.runner.AndroidJUnitRunner
```

`IncomingFaceDeviceTest` writes to its own scratch database (a `ContextWrapper`
that prefixes the name), so it can never touch the real library. Keep it that way.

**Reading the phone's state** (debug build, so `run-as` works):

```bash
adb shell run-as com.kalotrapezis.drive cat databases/photo_metadata.db > /tmp/phone.db
adb shell run-as com.kalotrapezis.drive cat databases/sync.db > /tmp/sync.db
```

**Making a sync happen on demand.** The phone syncs when the app opens (15-minute
gap) or when the computer says there is something new. That nudge, by hand:

```bash
cd desktop && node -e "
const { DatabaseSync } = require('node:sqlite'); const https = require('node:https')
const db = new DatabaseSync(require('node:os').homedir() + '/.local/share/local-drive-desktop/library.db')
const d = db.prepare('SELECT * FROM sync_devices').get()
const req = https.request({ host: d.peer_hosts.split(',')[0], port: d.peer_port, method: 'POST', path: '/sync',
  timeout: 5000, rejectUnauthorized: false, headers: { authorization: 'Bearer ' + d.peer_token, 'content-length': 0 } },
  res => { res.resume(); console.log('nudge', res.statusCode) })
req.on('error', e => console.log('error', e.message)); req.end()"
```

It only works while the Tetra app is **open on the phone** (that is when it
listens), and it is ignored if that phone synced less than a minute ago.

**Re-measuring the face numbers**: `python3 tools/measure-faces.py --phone /tmp/phone.db`.
Every threshold in the plan came out of it.

## Traps that cost time today

- **A stale APK.** Enabling ABI splits changed the output filename, and the old
  `app/build/outputs/apk/debug/app-debug.apk` sat there for hours being installed
  instead of the new build. It is deleted; install `app-arm64-v8a-debug.apk`.
- **`?since=` hides old data.** When the app starts accepting something it used
  to drop, everything dropped is outside every future window. Bump
  `METADATA_EPOCH` in `SyncClient.kt` and every device asks from the beginning
  once. This is why 35 named people were invisible on the phone.
- **SQLite will not resolve an outer column inside a subquery's `ORDER BY`.** Cost
  a confusing "no such column: p.cover_face_id". Apply that kind of choice in JS.
- **The phone cannot be trashed from a service.** Removing a photo needs
  Android's own confirmation, which needs an Activity — hence the Move queue.
- **Test vectors in a plane.** Building "a stranger" as `0.05 * base + 0.998 * away`
  makes it nearly identical to a probe built the same way. Use a direction
  orthogonal to everything (`stranger` in the device test).

## What today decided, that must not be undone by accident

1. **A guess and a decision are different kinds of thing.** "Person 41" never
   overwrites a name, on either device, in either direction. Anything automatic —
   a scan, a sync, a prune — may add to a named person and may never destroy one:
   not the name, not their faces, not when their photos leave the gallery.
2. **Two decisions that disagree are a question, not a race.** One Help organize
   card per pair of people; nothing moves until it is answered; answers cross and
   clear everywhere.
3. **Nothing is resurrected.** The phone skips photos it has a receipt for; the
   computer skips files the device's last manifest held and its current one does
   not.
4. **Nothing is deleted by a sync.** A Move is the device offering, after a
   verified receipt, through Android's own dialog, into Android's Trash.
5. **Measure before tuning.** 0.60 → 0.68 → 0.75 all came from measurements, and
   the first one measured the wrong statistic (random pairs, not a face against a
   person). `tools/measure-faces.py` is the one that measures the right thing.

## Tomorrow: the tablet

The full list is at the end of `SYNC_PLAN.md`. Short version: the tablet pairs
**with the computer**, not with the phone — every device holds one pairing and the
computer is the hub, so nothing needs building first. Set its rows to Photos
`receive`, Files `both` before the first sync. Steps 1–4 (pair, rows, first sync
moves nothing, people arrive with names and chosen portraits) should hold. Step 6
— two devices disagreeing about who someone is — has **never run between two real
devices**, only in tests. Expect it to be the one that breaks.

Take a backup of the phone and the desktop library before the first tablet sync.
`Drive-Android-backups/` has the command in its README.

## Still open, in the user's order

`SYNC_PLAN.md` "Roadmap" has all of it with what exists behind each. The headline:

- Finish what sync carries before trusting it further — the cheapest real gap is
  **labels only travel phone → computer** (`photo_labels` has no `updated_at`).
- **Documents** should learn the five things People needed (§ Roadmap B).
- **Multiple pairings on the phone** is the biggest structural hole and the only
  one that blocks a tablet from ever talking to a phone directly.
- Then: folders as albums, settings island, notes, tray, light theme.
- Never run for real: **photos, computer → phone** (42 of them waiting).
