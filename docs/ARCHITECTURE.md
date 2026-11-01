> Aktuelna lokalna arhitektura je u [LOCAL-ARCHITECTURE.md](LOCAL-ARCHITECTURE.md). Ovaj dokument čuva ranije online odluke koje više ne određuju aktivni obim.

# Architecture decisions

## ADR-001 — native Android and bounded modules

Accepted implementation direction: Kotlin, Android Studio, offline-first Android phones/tablets. Eleven Gradle modules enforce concrete boundaries. The publisher has requested cloud, collaboration and AI in the first public release; the historical V1/V2/V3/V4 labels describe work areas, not permission to defer the agreed online features until after publication.

`app` composes `core`, `format`, `data`, `canvas`, `core:ui`, `platform`, `sync` and the workspace/cloud features. `feature:workspace` and `feature:cloud` depend only on `core`, `core:ui` and `canvas`. `platform → core, format, canvas`; `sync → core`; `core:ui → core`; `data → core, format`; `canvas → core`; `format → core`; `server → core, format`. Core models do not import Android, Hilt, Room or Compose. Repository contracts live in core and are implemented in data/platform adapters. Hilt binds those contracts in app. `checkArchitecture` enforces project dependencies, forbidden presentation imports and domain purity. The portable format supplies no Android implementation and uses the platform JSON runtime on Android; the JVM server declares its own JSON runtime.

## ADR-002 — document state and gestures

The editor owns one DocumentSession. Its committed objects are exposed through immutable UI snapshots. The canvas adapter owns transient samples, drag preview and camera interactions. Completed edits become commands; moving one object creates one history entry. The current UI adapter uses MVVM with StateFlow and one-way actions.

The native View renderer is embedded in Compose. Completed stroke paths use an estimated 32 MB cache; active paths append samples incrementally. Objects outside the visible area are skipped. A spatial index and stylus-specific rendering need measurements on physical devices. This decision does not claim a measured 60 FPS guarantee.

## ADR-003 — Room and explicit document encoding

Room stores library metadata separately from document/recovery chunks. Document JSON is divided into bounded SQLite rows without splitting UTF-16 surrogate pairs; all chunks and metadata are replaced inside one transaction. This avoids CursorWindow failures when a complete document exceeds a single row's practical read limit. The schema 1 → 2 migration splits legacy payloads inside SQL, including recovery drafts, without transferring their full size through a cursor. Schema 2 → 3 adds separate account-partitioned sync tables; schema 3 → 4 adds durable initial-publication receipts and cached-list metadata. All schema snapshots are retained.

The document format writes version 3 and reads versions 1–3. Historical JSON fixtures and Room schema snapshots are retained. Unknown versions/types are rejected. A strict preflight bounds nesting and numeric literals before platform parsing. No destructive Room fallback is allowed. Local Save still encodes a complete document; cloud operations have a separate object/conflict protocol.

Images/PDF originals are durable private files referenced by validated asset IDs. Display loads run on worker threads with a bounded bitmap cache; thumbnails and export share the renderer. Backups include referenced attachments and validate them before committing imported board copies. Existing documents and attachment IDs are not overwritten during restore. Active sessions retain asset IDs referenced by Undo/Redo as well as current objects so garbage collection cannot remove material needed for undo.

Folders and board assignments use database transactions. Folder moves reject cycles; deleting a folder moves its children/documents to the parent. Text search uses a separate normalized index so large text is searched inside SQLite instead of being transferred to the library UI. Library and editor state have separate ViewModels.

Groups contain editable leaf objects and are deliberately flat. Grouping existing groups merges their children. Group/ungroup preserves child identities; duplicate/clipboard produces new group and child IDs. Transform, eraser, search, export, archive remapping and asset retention traverse group children. Erasing the final stroke alongside one remaining object unwraps that object, with the full operation reversible by Undo.

## ADR-004 — save, recovery and privacy

Manual Save is primary. Draft recovery is debounced after completed document edits and flushed on stop when execution time permits. `EditorPersistence` owns the workflow and mutex coordinating save/discard/draft operations. A save marks its captured revision saved; edits made while it is running remain dirty. ViewModels consume document/settings ports and expose typed, scoped retry actions; creation/import is not blindly repeated after ambiguous completion. Notice IDs prevent an old snackbar acknowledgment from clearing a newer failure. Small dialog values and object IDs use saved instance state; document graphs and Android Views do not enter the Bundle.

Development defaults now include multiple pages, a stroke eraser, indexed text search, rectangle multi-selection, batch transforms, private object copy/paste, style changes and z-order. Continuous group transforms and batch insertion are single history commands. These are implementation decisions under the user's authorization to continue; publisher identity, pricing and cloud choices remain open.

The Android build requests network access for optional account actions and stores session credentials using AES-GCM with Android Keystore. Login never uploads boards. The updated privacy draft describes current account behavior; the final cloud/AI behavior requires further disclosures before release. Device automatic backup/transfer remains excluded. Editable ZIP backups include saved documents and their attachments, while PNG/PDF are rendered copies. The private clipboard remains within the current application process, can cross pages/documents and retains copied media until replaced or the process ends; the system clipboard is not read.

## ADR-005 — publication

Target SDK 36; minimum SDK 26 initially. The current application ID is a development candidate requiring publisher confirmation. Release enables shrinking/optimization, but production signing must use publisher-owned credentials outside version control. Play testing, device qualification and actual publication remain separate acceptance gates.

## ADR-006 — backend and identity

The server is a Kotlin/Ktor process separate from the APK. JDBC repositories use prepared parameters and transactions, a bounded connection pool and checksummed SQL migrations. Production uses PostgreSQL with verified TLS. H2 provides fast local tests; a separate task runs the backend suite against a real, isolated loopback PostgreSQL process. This verifies database semantics locally; deployed TLS, backup/restore and operational qualification remain separate gates.

Accounts require a verified email. Passwords use Argon2id (64 MiB, three iterations, one lane); at most two hash jobs execute concurrently. Access tokens expire after 15 minutes and refresh families after 30 days. Only hashes of random tokens are stored. Refresh rotation invalidates its predecessor and replay revokes the family. Account row locks serialize issuance, password reset, deletion and authorized writes. Reset revokes all sessions. SMTP requires STARTTLS and server identity verification. Development messages are retained only in an ignored local file; public responses never include identity action codes.

Board ownership and membership are checked on every service operation. Only the owner changes sharing; invitations target a verified recipient email, expire after seven days and are single-use. Roles are Owner, Editor, Commenter and Viewer. Snapshot commits require an expected revision and idempotency operation ID. Object deltas merge independent changes against explicit preconditions; conflicting edits preserve the newer document. Durable tombstones prevent stale operations from resurrecting deleted identities. Reconnect delivery, presence, attachments and shared Undo still need full product integration and convergence tests.

Viewport, favorites, folder assignment and local Trash state do not belong to the shared document. Uploads strip those personal fields and assign a distinct server board identity. Attachment documents are rejected until private asset transfer/reference validation is implemented. No upload is silently accepted with broken media.

The shared object-delta/fingerprint model and server operation transport are specified in SYNCHRONIZATION.md. Atomic commits include the document, revision, receipt, log and deletion state. Bounded replay excludes pre-invitation history; owner quota locking prevents parallel editors from exceeding the aggregate budget. Server schema 1 → 2 preserves legacy data and snapshot receipts. Android has a typed account-fenced transport, transactional durable outbox and bounded synchronization coordinator. Room schema 4 adds durable publication captures/receipts and cloud-list metadata. Explicit publishing, read-only previews and independent local copies are connected to the UI. Conflicting edits retain existing content and require review.

## ADR-007 — durable scheduling and transport

WorkManager work is partitioned by account and bound to the configured service origin. A pass processes at most three captures and three writable boards with two operations each, retains original operation IDs, and appends bounded continuation work when needed. Network/battery constraints, a three-minute pass timeout and six-attempt transient-error limit bound background execution. Access/conflict/certificate failures require attention; queued content is retained. Account changes cancel the previous partition. The observer schedules only explicitly queued publications/edits and never uploads private originals after login. Automatic scheduling does not make the read-only cloud preview a collaborative editor.

The OkHttp adapter uses platform certificate/hostname validation, refuses redirects/cookies and automatic POST replay, bounds complete response bodies and total call duration, and keeps cancellation active throughout streaming. Credentials are sent only to the configured origin. Release permits HTTPS; debug cleartext is limited to loopback. UI dispatchers/document storage are injectable and coroutine tests control virtual time.

## ADR-008 — evidence and security gates

`ENGINEERING-EXECUTION.md` and `SECURITY.md` track implementation and qualification separately. CI checks formatting, architecture, JVM/backend tests, Android lint, instrumented emulator tests, runtime Maven advisories, source secrets and final APK policy. Secret scanning covers nonignored current files, including untracked sources; it does not replace history/credential rotation review. Package inspection checks actual compiled XML resources, including resource-shrunk paths. Dependency scans do not cover OS/JRE/container images or undisclosed vulnerabilities. Public deployment, physical stylus/performance qualification and independent penetration testing remain separate gates.
