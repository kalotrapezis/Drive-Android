# Home and Files visual QA

## Evidence

- Reference: `/home/teo/Εικόνες/Screenshots/Screenshot_20260919_103203.png` (user-supplied Home layout).
- Device render: `/tmp/local-drive-home-groups.png`, captured from the connected Xiaomi 15 at 1200 × 2670 px after installing the debug APK.
- Functional interaction evidence: the same device was used to open a Drive item’s three-dot menu, add `#phone-checkyv`, save it, open Files tools, and filter the list by that tag.

## Comparison

| Reference intention | Implemented result |
| --- | --- |
| Dominant Photos entry | Large left-hand Photos card opens the real media timeline. |
| Small text-and-SVG categories | Screenshots, Documents, Favorites and Recent use the existing vector icons with text only. |
| Files with document graphic | The Files card contains a transparent, generated document-stack illustration behind the Files label. |
| Colored category groups | Photos/Screenshots/Documents share a green container; Files/Favorites/Recent share a blue container. |
| Letter shadow | The Local Drive title has a restrained dark offset shadow. |
| Settings icon | Settings uses a standard gear vector rather than the former palette icon. |
| Local sync area | A card retains the Sync entry but honestly reports that no trusted device is connected. |
| Tags via Files “More” | The Files pull-up starts with Tags and Trash actions; Tags opens its own sheet where tags can be created or selected to filter Files immediately. Each item’s three-dot menu can still select or add tags. |
| Trash affordance | The Trash folder uses a red trash icon. Its view has an Empty Trash control and a permanent-deletion warning; the action opens a confirmation sheet. |

## Findings

- The Home screen has no clipped labels or overlapping controls in the 1200 × 2670 phone capture.
- The Files pull-up hides both the navigation island and Search together, then restores them with the faster shared animation.
- The Tags sheet and Trash screen were opened on the device; Empty Trash was not confirmed or run.
- No cloud or sync-progress state is simulated.

final result: passed
