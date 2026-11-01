> Istorijski plan/prethodna faza. Aktuelni lokalni obim i kriterijumi su u [LOCAL-REBUILD-PLAN.md](LOCAL-REBUILD-PLAN.md), a provereni rezultati u [LOCAL-STATUS.md](LOCAL-STATUS.md). Online/AI zahtevi iz ranije faze trenutno nisu aktivni.

# Development defaults

These choices make the authorized implementation concrete. They are working product decisions, not claims that the publisher has approved final commercial terms or that device qualification is complete.

**Release scope confirmed on 20 October 2026:** cloud, collaboration and AI must be included in the first Play release. Rows below describe the current Android build where stated, not a reduced release scope. Backend work is documented in BACKEND.md. Provider, region, budget and production accounts are still pending publisher input.

| Area | Current behavior |
|---|---|
| Platforms | Native Android, Kotlin; minimum API 26, target API 36. Both orientations and resizable windows are allowed. Physical reference devices remain to be selected. |
| Local use | Local editing needs no account. Optional account actions use the configured service; login alone never uploads boards. No analytics or ads. No commercial local board-count limit; storage and document safety limits apply. |
| Save and recovery | Manual Save; optional autosave after draft persistence. Separate draft debounce 700 ms plus an on-stop attempt. Incomplete gestures and edits before a successful write can be lost. Save/Discard/Cancel on dirty exit. |
| Eraser | Entire strokes; no partial/pixel erase yet. |
| Selection | Single and rectangle multi-select, move/resize/delete/duplicate, flat groups, private object copy/cut/paste, front/back and stroke/shape/line style changes. Image quarter-turn rotation and crop. Arbitrary vector rotation remains separate work. |
| Clipboard | Process-local, available across pages/boards, never reads or writes the system clipboard. Replacing the clipboard releases retained attachment references. |
| Pages | Multiple pages, up to 1000. Each owns its background, name and viewport. Page operations support Undo/Redo. |
| Backgrounds | Plain, grid, dots and ruled; preset background colors. App theme does not change the document background. |
| Export | PNG/JPG active page; all pages as PDF. Content bounds plus margin; PNG/JPG default maximum dimension 4096 and 16 million pixels, scale at most 2. Background is included. System sharing uses a temporary PNG. |
| Imports | Files up to 16 MB; images sampled to at most 3072 pixels on the longest side and normalized to PNG; PDF up to 100 pages with a page-range selection. |
| Backup | Explicit editable ZIP export of saved non-Trash boards and attachments. Restore creates copies; existing documents are preserved. Limits: 1000 boards, 16 MB per entry, 64 MB total uncompressed. Automatic scheduled backup remains open. |
| History | Per open editor session, up to 150 operations and an estimated 32 MB retained payload. Continuous gesture and batch operations are one command. History is not persisted after closing. |
| Organization | Favorites, folders/subfolders, Trash until manual permanent deletion. Deleting a folder reparents its direct children and keeps all boards. No timed Trash purge. |
| Search | Case-insensitive board/folder/page/text/note search, including Serbian accents and Cyrillic. First matching page/object opens; handwriting and OCR search await recognition support. |
| Languages | English and Serbian Latin UI. Text objects accept Unicode, including Cyrillic. Template starter content currently uses English. |
| Privacy | Private local database/assets; optional account requests and Keystore-encrypted session credentials. Platform device backup/transfer excluded. Uninstall removes local data. User-selected export destinations can be third-party document providers. The release policy must cover the final cloud/AI behavior. |
| Performance | Viewport culling, incremental live stroke, bounded path and bitmap caches. No measured physical-device FPS or latency guarantee yet. |

Publisher inputs still required: permanent application ID, store identity/contact, developer-account status, signing ownership, public privacy URL, cloud region/provider/budget and AI provider/data handling. Monetization remains to be defined. No paid service or public release has been enabled.
