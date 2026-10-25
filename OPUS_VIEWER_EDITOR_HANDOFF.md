# Opus handoff — Viewer ↔ Editor audit, 2 October 2026

Read this before the older entries in `HANDOFF.md` or `CONTINUE.md`.

## Goal and current state

The user requested: “check the project for bugs between the viewer and the editor and fix them. I have phone connecting so you can test. It has the development version.”

The fixes are implemented and the final debug APK is installed on the connected phone. They are **uncommitted and not integrated into the main checkout**. Full editor pagination/footer parity is still unfinished; this audit does not complete the entire Word compatibility roadmap.

## Where the code actually lives

- Main project: `/home/teo/Έγγραφα/Claude/Coding/Drive-Android`, branch `writer-library`.
- Fix worktree: `/home/teo/.codex/worktrees/viewer-editor-fixes/Drive-Android`, branch `codex/viewer-editor-fixes`.
- Both start at `250e0b3f24c8f968d74a64a06ec19cbd0f0ac8e2`.
- **No commit was made. Merging the branch alone will not bring in these edits.** Read the worktree diff and its three untracked source/test files.
- A complete snapshot, including those untracked files, is saved beside this handoff: [`handoff/viewer-editor-fixes-2026-10-02.patch`](handoff/viewer-editor-fixes-2026-10-02.patch). Its applicability to the main checkout was checked with `git apply --check` when this handoff was written. Recheck before applying if the checkout changes.
- The main checkout's unrelated untracked `Assets/Notes.png` and screenshots were preserved. Leave them alone.
- The older `/home/teo/.codex/worktrees/bug-audit/Drive-Android` contains a different, unfinished Notes/file-operation audit. Do not mix its edits into this repair.

## Implemented fixes

1. **Editor display:** uses the viewer's bundled document fonts, including linked text and paragraph-font fallback; paragraph before/after spacing is added once at the paragraph boundary rather than multiplied by wrapped lines. Contextual spacing, explicit page breaks and first-paragraph space-before are handled. Exact/minimum heading line heights are respected.
2. **Small mobile tables:** the library's unstyled block separator no longer imposes a default text-line height on a shorter table.
3. **Tabs and contents entries:** a visual hair-space representation supplies tab widths and leaders while keeping real tabs in the editable/saved document and preserving cursor offsets. Viewer and editor share `tabGap`. Width uses the actual editor layout; right-aligned mobile stops fit the available width. Tab geometry and leaders follow zoom.
4. **Switching layouts:** geometry reflow preserves the library's typing Undo/Redo history. Switching to Pages commits the active table cell before laying out the viewer. The phone View ribbon separates navigation tools from zoom/layout tools so Mobile view is reachable.
5. **Save concurrency:** overlapping saves are guarded; completion compares against the snapshot actually written. Typing or Undo during a save remains dirty and is flushed to recovery before completion. The saved baseline updates even when newer edits exist. Saving a copy/new file changes recovery ownership to the current URI; autosave reads the live URI rather than a captured composition value.
6. **Recovery:** typing explicitly observes the rich editor's Compose text, so ordinary edits enable Save and trigger drafts. Active cell text/formatting is overlaid into a non-mutating recovery snapshot without committing the cell or altering Undo history. Undo back to saved content deletes a stale draft. Draft replacement is checked and atomic via a sibling temporary file under a mutex.
7. **Images/charts after save:** `DocImage.with(key = ...)` preserves all image/chart metadata during rebinding. Draft restoration reads the complete saved package rather than rebuilding lossy image instances.

## Changed files

| File | Purpose |
| --- | --- |
| `app/src/main/java/com/kalotrapezis/drive/Writer.kt` | Display bridge, reflow, save/draft reconciliation, cell recovery, View ribbon |
| `app/src/main/java/com/kalotrapezis/drive/WriterTabs.kt` **new** | Offset-preserving tab display and measurement |
| `app/src/main/java/com/kalotrapezis/drive/OfficePages.kt` | Shared viewer/editor tab-gap calculation |
| `app/src/main/java/com/kalotrapezis/drive/OfficeFormats.kt` | Complete image metadata preservation when changing keys |
| `app/src/test/java/com/kalotrapezis/drive/WriterBridgeTest.kt` | Fonts, spacing, heading line heights, reflow Undo/Redo regressions |
| `app/src/test/java/com/kalotrapezis/drive/ChartsTest.kt` | New chart → save → rebind → second save round trips in DOCX/ODT |
| `app/src/test/java/com/kalotrapezis/drive/WriterDraftTest.kt` **new** | Failed draft writes preserve the last complete recovery file |
| `app/src/androidTest/java/com/kalotrapezis/drive/WriterLayoutDeviceTest.kt` **new** | Five physical-device layout tests |

## Validation actually performed

From the fix worktree, with Java 17 and the installed Android SDK:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/teo/Android/Sdk ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

- **194 JVM unit tests: 0 failures/errors.** Debug APK and instrumentation APK assembly completed.
- **5 physical-phone instrumentation tests: passed.** Fonts/linked text, multiline paragraph spacing, compact mobile table height, right-aligned contents tabs, and tabs at 70%/200% zoom.
- **Lint failed:** 10 errors, 172 warnings and 26 hints. All 10 error IDs/messages exactly match a live lint run of the original checkout: WrongConstant ×3, NewApi ×2, RememberReturnType ×2, SuspiciousIndentation ×1, NonObservableLocale ×1, UnusedBoxWithConstraintsScope ×1. Do not describe the combined Gradle command as passing: its final failure is lint.
- `git diff --check` passed.
- A read-only Sol review was performed; the final high-priority recovery findings were fixed and tested. No new dependencies were added.

Device test command (install the test APK first; it was uninstalled after testing):

```bash
/home/teo/Android/Sdk/platform-tools/adb -s 208c8192 shell am instrument -w -r -e class com.kalotrapezis.drive.WriterLayoutDeviceTest com.kalotrapezis.drive.test/androidx.test.runner.AndroidJUnitRunner
```

Manual checks on the phone:

- Synthetic Word document: fonts, Greek text, wrapped paragraphs, two short tables, contents tab/leader.
- Type in a table cell → immediately Pages → Save → inspect saved DOCX XML: new cell text is present and real `<w:tab>` remains.
- Deliberately slow save using a synthetic document with a 16 MB payload: typing during save remains unsaved; the captured file snapshot and newer recovery draft are distinct; restart restores newer typing.
- Undo during that slow save: editor remains dirty relative to the snapshot actually written; restart restores the undone state.
- Unfinished table-cell typing → restart without committing: cell text recovers.
- Save a copy → type more → restart/open the copy: edits recover under the copied file.
- Type → pause long enough for a draft → Undo back to saved text → restart: stale text does not return.
- Switch Mobile/Print layout: geometry rebuilds, contents number stays aligned, switching display alone does not mark the document changed.

## Phone and evidence

- ADB: `/home/teo/Android/Sdk/platform-tools/adb`; serial `208c8192`, Xiaomi `24129PN74G`, Android 16/SDK 36.
- Installed app: `com.kalotrapezis.drive`, debug version `0.3.0-beta.3` from the fix worktree. The tablet was not updated or tested in this audit.
- **Mobile view is restored to `true`. Preserve this user preference.**
- Debug APK: `<fix worktree>/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
- Writer log: `adb -s 208c8192 shell cat /sdcard/Android/data/com.kalotrapezis.drive/files/logs/writer.log` (since 25 Oct; the installed build is not debuggable).
- Session evidence/helper/screenshots and pulled synthetic DOCX files: `/tmp/tetra-viewer-audit/`. Logs: `/tmp/tetra-viewer-final-checks.log`, `/tmp/tetra-viewer-device-tests.log`, `/tmp/tetra-viewer-lint-baseline.log`. These temporary paths may disappear; the patch and this handoff are durable.
- Generated QA documents were moved out of Tetra's document tree into `/sdcard/Download/Tetra-viewer-editor-QA-20261002/`. Synthetic private history/recovery was cleaned; the test APK was removed.
- The original suite documents were not edited. Byte hashes of 01, 02 and 04 matched the copies taken before testing. The modification timestamp of 01 was touched while bringing it to Office Recent; its contents are unchanged. Other tests used generated files/copies. No medical report was accessed.

## Next for Opus

1. Read this handoff, inspect the actual fix worktree and the complete patch. Keep unrelated assets and the older audit intact.
2. Integrate the verified repair into `writer-library` if continuing in the main checkout. A branch merge alone cannot transfer uncommitted changes. From the main project, first recheck:

   ```bash
   git apply --check handoff/viewer-editor-fixes-2026-10-02.patch
   ```

   Then apply that patch only if the target files still match; alternatively make an authorized checkpoint commit in the fix worktree and integrate that commit. Do not apply the patch twice.
3. Run the relevant checks after integration. Report the existing lint failure separately; do not assume it is fixed by this work.
4. Continue Phase 1 from the older `HANDOFF.md`: full editor page gaps, footers and page starts inside a paragraph remain architectural limitations. This patch does not replace the editor with `OfficePages`' paginated layout.
5. Still unverified here: tablet layout, comprehensive real-document comparison against LibreOffice, complete ODT visual parity, and the medical letterhead case. Preserve originals and follow the existing explicit request to ask before accessing that medical file.

The user's next instruction determines further scope. No commit, merge, PR or release was performed by this audit.
