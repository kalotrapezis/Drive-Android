# Local Drive for Android

A local-first "super app" for Android: a **Gallery**, a **Files** manager, a
**Document Scanner** and a **Codes** scanner (QR/barcode, text, RF payment
codes). Nothing is uploaded; all analysis runs on the phone.

Kotlin + Jetpack Compose, min SDK 30 (Android 11), target SDK 36. Package
`com.kalotrapezis.drive`.

## Modules

| Module | What it does |
|---|---|
| **Gallery** | MediaStore photos and videos: timeline, collections, People/Documents (local AI), Hidden vault, map, search, viewer, photo editor. Can be set as the phone's default gallery. |
| **Files** | File manager rooted at `/sdcard/Drive/`: browse, search, tags, favorites, copy/move/rename/share, reversible Trash. |
| **Scanner** | Multi-page document scanning to PDF with auto crop, filters, retake, reorder and manual painting. |
| **Codes** | QR/barcode scanner, text recognition, RF payment-code copy/share. |

Planned: Notes, desktop Sync (see `ROADMAP.md`).

## Build

Requires JDK 17 and the Android SDK (Gradle 8.14 does not run on JDK 25).

```bash
export ANDROID_HOME=~/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Branches

- `alpha` — active development.
- `main` — initial commit only; merge `alpha` when a release is accepted.
- `codex/android-storage-baseline` — earlier history, superseded by `alpha`.

## Documentation

| File | Purpose |
|---|---|
| `FEATURES.md` | Authoritative list of what is implemented now, per module |
| `DOCUMENT_SCANNER.md` | Scanner pipeline, code map and rules |
| `AI_COLLECTIONS_ARCHITECTURE.md` | Local People/Documents analysis design |
| `ROADMAP.md`, `Plan.md` | Product direction and the original wishlist |
| `MANUAL_CHECKLIST.md` | On-device acceptance steps |
| `CONTINUE.md` | Handoff notes between work sessions |
| `THIRD_PARTY_NOTICES.md` | Bundled models and library licences |

## Safety rules

- Originals are never changed silently: editing a photo in place goes through
  Android's own consent prompt; everything else saves a new file.
- Deletions go to a reversible Trash; permanent deletion is always confirmed.
- Files never leave `/sdcard/Drive/`; paths are checked against traversal.
- Camera, media and location permissions are asked only where they are used.
