# Manual check — Photos metadata and collections

## Drive Home and Files

- [ ] Grant Android's All files access. The Drive Home can browse only `/sdcard/Drive/`; it does not offer a location picker.
- [ ] From the app Home, open Files. Its top row is Back, a home SVG and one `Recent files` title; it never displays a `Drive` page title or a duplicate recent heading.
- [ ] Favorites and the Files browser use the same Back + section SVG + title header. Files has only the list/grid control at right; pull down to refresh instead of looking for a Refresh button.
- [ ] Verify Files lists the direct contents of `/sdcard/Drive/`, not the retained legacy `/sdcard/LocalDrive/Drive/` path.
- [ ] In the Drive bottom bar, only the selected Home, Favorites or Files item shows its text label; the inactive Home and Files items are vector icons.
- [ ] In dark theme, the Files island is translucent black. Its selected item is translucent white with black SVG/text; selecting another item reverses the prior item immediately. The Files island sits toward the bottom-left, like the Photos module bar.
- [ ] In Files, the dark/light header island remains above the content. It shows one ellipsized leaf folder name, a chevron Back and the list/grid SVG — never a duplicate `Drive` label, a Refresh button or a full pathname.
- [ ] Open Files and enter a nested folder. Back moves to its parent; Back at the Files root returns to Drive Home; Back again returns to the app Home.
- [ ] Switch list/grid. Both show the same real folders and files, with folders before files. Refresh after changing a fixture outside the app.
- [ ] Tap the top-right View SVG to switch list/grid. It no longer has a sort mode or dropdown; the press highlight stays circular.
- [ ] Swipe the bottom Files island upward. Tags and Trash are the two top actions; Tags opens its own sheet. Name/Date modified sorting and a circular Drive-space breakdown remain below. Select Date modified and return to Files; only the visual order changes.
- [ ] Tap the separate Search circle at the bottom right. Search by a Drive file name and by one of its tags; results stay inside `/sdcard/Drive/`, excluding Trash.
- [ ] Add a tag from a disposable item's three-dot menu. Its card and the expanded Files island show the tag. Tap the tag to filter the Drive list, then clear the tag filter.
- [ ] Open Trash from the expanded island. It shows only `/sdcard/Drive/Trash`, with a red trash SVG, an explicit permanent-deletion warning and Empty Trash control. Confirm that tapping Empty Trash opens its confirmation sheet; run it only against disposable fixtures.
- [ ] Add then remove a disposable item from Favorites. Each three-dot action happens immediately with no second confirmation; removal uses the heart-minus SVG.
- [ ] In grid mode, scroll the final large thumbnail/card to the bottom. It clears the floating Files island instead of being hidden behind it.
- [ ] Scroll the Files list through its top edge. Cards move beneath the floating header as in Photos; they are not clipped below a second hidden boundary.
- [ ] Use the three-dot SVG on a recent, file and folder in both list and grid. Its bottom sheet names that exact item; Favorites changes immediately, while editing actions open their own panels.
- [ ] Add or remove a disposable Drive fixture outside the app, then pull down in Files. The list refreshes and shows the result without changing folders.
- [ ] Open the heart Favorites tab. It initially states `No files in Favorites yet.`; add a disposable item from its three-dot menu and verify it appears here. Remove it and verify it disappears. It never shows a cloud Shared tab.
- [ ] Open a disposable local file with an installed compatible Android viewer. Android's native chooser opens and the file appears first in Drive Home's recent list.
- [ ] In a disposable file's three-dot menu choose `Open with…`, select a compatible app, then tap the file again. It reopens with that saved app. Choose `Open with…` again and select another app; the saved choice changes only for that exact Drive file.
- [ ] Rename or move that disposable file, then reopen it. Its saved app follows the new Drive-relative path. If the selected app is removed, the app falls back to Android's chooser rather than failing or changing any file.
- [ ] Open the same file twice. It has one recent card and its opened time updates. Restart the app and verify the recent item persists.
- [ ] Delete or move a disposable recently opened file outside the app. Return to Drive Home; its stale card disappears without touching other files.
- [ ] Try an unsupported file type or temporarily disable compatible viewers. Android's resolver reports that no compatible app is available; the app does not add a false recent item.

## Files item actions and Drive-only boundary

- [ ] Create a disposable file and folder inside `/sdcard/Drive/` only. Their three-dot menus have a Copy/Move/Rename/Properties grid, a one-tap Favorites action and a Tags editor.
- [ ] Rename the disposable item. The confirmation modal uses the new name field, updates the list after confirmation and never permits `/`, `..` or a blank name.
- [ ] Copy then Move the disposable item to another folder below `/sdcard/Drive/`. A destination picker lists Drive folders only; choosing the current folder, a conflicting name or a folder inside the selected folder reports an error without overwriting or losing data.
- [ ] On a folder, choose Change folder color. It opens a circular color palette with a distinct none/default option; selecting it changes only that folder icon. On a file, the color action is absent.
- [ ] Verify file icon colors: Word blue, Excel green, PDF red, TXT/MD grey, Excalidraw purple, pictures light green and videos pink. Unknown formats remain neutral.
- [ ] Use Properties for a file and folder. The modal shows its Drive-relative location and opens the selected item only when requested.
- [ ] Use Move to Trash on a disposable item. It asks for confirmation and moves it into `/sdcard/Drive/Trash`; it does not permanently delete it.
- [ ] Use Sync now on a folder. Until pairing exists it opens an honest modal saying that no trusted paired device is available; it does not claim a transfer occurred.
- [ ] Try every mutation with an item in `/sdcard/Drive/`. Confirm Camera, Screenshots, Photos and every path outside `/sdcard/Drive/` stay unchanged. The app has no picker or action that can target them.

## Sync and Settings shell

- [ ] From Home, open Sync and Settings. Both retain a pinned title and left chevron Back while their contents scroll.
- [ ] Sync honestly shows `Not connected`, `No paired devices`, the fixed collection map and `No sync actions yet`; it does not present a fake device or transfer progress.
- [ ] In Settings, verify the displayed Drive, Camera and Screenshots locations match the fixed Android paths and are not editable folder pickers.
- [ ] Use the Photos and Drive permission buttons. Android opens its system permission screen; returning to Settings updates the displayed permission state.
- [ ] Settings → Open Sync and Sync → Open settings move between the modules without changing files, photos or permissions.

Run this after installing the debug APK on a device with photo permission.

- [ ] Long-press one timeline photo. Until the Archive-removal slice lands, record Archive as legacy behaviour only; the target actions are Share, Add to collection, Favorite and Android Trash. Cancel clears selection.
- [ ] Create `Test collection`, add one or more selected photos, then open it from Collections. Its count and contents match the chosen photos.
- [ ] Try an empty collection name and a duplicate name with different letter case. The app reports an error and keeps existing metadata intact.
- [ ] Favorite a photo from selection and from the viewer. Open Favorites; it appears there. Unfavorite it and confirm it leaves Favorites.
- [ ] After the AI Collections foundation lands, verify that Archive is absent and that Documents and People state their local-only analysis/review status truthfully. Do not treat this as implemented before the corresponding slice.
- [ ] Open Screenshots from Collections and verify only screenshot-path media appears.
- [ ] Open a photo from a collection. Its bottom filmstrip stays within that active collection/filter.
- [ ] Move a selected photo to Trash. Android shows its own confirmation; deny it once and verify no media is moved. Confirm it once only with a disposable photo.
- [ ] Force-stop/reopen the app: Favorites and custom memberships remain. Legacy Archive metadata is scheduled for removal, not a continuing feature. Uninstall/reinstall only after noting that private metadata is intentionally cleared while original photos remain.

## Viewer quality and Timeline navigation

- [ ] Add a disposable DCIM/Screenshots image outside the app, then pull down in Timeline and Collections. Both reload their photo list without requiring app restart.
- [ ] Leave the app, change a disposable Drive or DCIM/Screenshots fixture, then return. The open Files view and Photos view refresh automatically.
- [ ] Open a high-resolution photo. It stays sharp at the available viewer size and has the same orientation as the system gallery; thumbnails may remain lower resolution.
- [ ] Scroll the Timeline through more than one period. The translucent label at the top follows the visible Week, Month or Year grouping.
- [ ] On a large library, tap or drag the narrow right-edge scrollbar. It moves quickly through the timeline, briefly shows the target year, and does not start photo selection.
- [ ] Long-press and drag photos close to, but not on, the right edge. Multi-select and edge auto-scroll still work.

## Timeline visual UI

- [ ] On the first Timeline screen, the grid and first date header begin below the top back and More islands; no app control overlaps the status bar.
- [ ] At the Timeline top, the current period appears once in the centered sticky pill. The first grid group does not repeat that period as a second heading.
- [ ] The top-left control is the tail-less chevron Back icon, not a `Home` text button. More remains a separate three-dot island at top right.
- [ ] Scroll through at least two date groups. Every later period has a centered, spaced date heading; there is no large, persistent date overlay over thumbnails.
- [ ] While scrolling through a period, the centered top island stays on its current Week, Month or Year label. It changes only when that next group reaches the top of the grid.
- [ ] When idle, the right fast-scroll rail and knob are invisible. Touch or drag the right-edge hit area: both appear while held, show the target period, and disappear about 0.7 seconds after release.
- [ ] Timeline remains image-first: thumbnails keep one-pixel gaps, while date headers are the only deliberately spaced full-width grid rows.
- [ ] In dark theme, SVG icons and text are white on translucent black islands. In light theme, they are black on translucent white islands. Check this on the top controls, bottom module bar, Search and viewer actions.
- [ ] Pinch through Week, Month and Year. The pill follows the visible period on every scale.
- [ ] On a narrow 360dp device, Timeline, Collections and More remain distinct, tappable and have a visible active state.
- [ ] Long-press photos immediately beside (but outside) the 48dp scrub hit area; selection works. Tap or drag on the rail; only fast scrolling occurs.
- [ ] In both light and dark system themes, overlays have contrast and thumbnails, empty and error states remain readable.
- [ ] More is the three-dot control at the top right. The circular Search control is alone at the bottom right and filters local photo names and folders; clear restores the same active collection.
