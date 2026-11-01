# Synchronization contract under development

Cloud, collaboration and AI belong to the first public release. The shared merge model, server operation transport, typed Android client, durable device queue and explicit publication/preview UI are implemented. Shared editor integration and background scheduling are still pending. Cloud copies are separate from private original boards; this is not yet continuous cross-device editing.

## Implemented model

`core/cloud/DocumentDelta.kt` defines atomic board/page metadata changes and object mutations. `format/ElementDigest.kt` supplies version-1 SHA-256 fingerprints with typed, length-prefixed canonical values and sorted JSON keys. A portable golden fixture is checked on both JVM and Android. Number normalization avoids dependence on JSON map order or integer/decimal representation.

The model excludes viewport, favorites, folders, local Trash and update timestamps from changes. A single completed gesture or logical edit should become one outbox operation. Groups are atomic objects with stable child identities; grouping/ungrouping must preserve surviving children and retire only identities that actually disappear.

| Concurrent scenario | Merge behavior |
|---|---|
| Different objects edited | Apply both changes against their expected object fingerprints |
| Same object changed to different values | Reject the entire conflicting operation for review; retain the existing server document |
| Same metadata field changed differently | Conflict; independent metadata fields can merge |
| Object deleted after its content changed | Deletion of the same live identity wins |
| Stale edit reaches a deleted object/page | Reject; do not resurrect it |
| Creation reuses a retired identity | Reject; an explicit recovery copy uses a new identity |
| Group structure changed while an old leaf/group operation was pending | Conflict for review; do not silently acknowledge a deletion that no longer addresses a live top-level object |
| Reorder meets a concurrent addition | Preserve unknown live objects in their existing order after the requested identities |
| Reorder mentions deleted identities | Ignore missing order entries; never recreate content |
| Deleting the last page | Reject, preserving the existing document |

The merge is atomic: a conflict on one member of a batch exposes none of its preceding tentative changes. The authoritative server will serialize accepted operations and assign revisions. Independent content edits can commute; concurrent insertion/z-order follows the accepted server sequence. This is a centralized operation protocol, not a fully decentralized CRDT.

## Implemented server transport

`POST /v1/boards/{id}/operations` accepts versioned deltas with an operation UUID and observed base revision. Stale bases are allowed when the affected object preconditions still hold; independent edits merge. The server persists the new document, revision, receipt, operation log and tombstones atomically. Retries return the original revision without duplicate writes. The snapshot endpoint still rejects stale revisions and now applies the same identity/tombstone rules, so it cannot bypass deletion protection.

`GET /v1/boards/{id}/changes?after=...` returns a contiguous cursor of up to 100 operations with an approximately 8 MiB aggregate payload bound. Missing/legacy snapshot history requires a fresh snapshot. JDBC cursor fetching bounds transfer from the database. New members cannot read operations predating their invitation acceptance. Revoked access blocks subsequent cursor requests. Deleted contributors are anonymized in retained history; their contributions in another owner's board remain.

Board creation also takes an operation UUID. A repeated creation returns the same board, including its current revision; a reused ID with a different body conflicts. Deleting the board leaves a receipt that prevents a delayed creation retry from resurrecting it.

An owner quota row is locked before a board row. The owner has a provisional aggregate 64 MiB budget for encoded snapshots and retained change payloads, plus bounded counts of receipts, creations and tombstones. Different editors cannot race past that budget on different boards. Limits are not final commercial terms; compaction, provider capacity and the publisher's budget remain release decisions.

Each operation needs an account, board, stable UUID, protocol/fingerprint version, observed base revision and body hash. The same operation UUID/actor/body returns the prior acknowledgment. A different body or actor using the same ID conflicts. Every request and live connection must recheck current membership; role changes and revocation must take effect on reconnect and subsequent messages.

Current deduplication/deletion state is database-backed rather than process memory. Retention/compaction is not yet implemented; bounds return a quota error without deleting current content. Future tombstone compaction requires a minimum accepted base revision/checkpoint policy; a client older than that checkpoint must fetch a fresh snapshot and preserve conflicting local work separately.

## Implemented device durability

`BoardGateway` exposes typed snapshots, listings, operations and revision cursors. The Android adapter rejects malformed identities, roles, numeric revisions and cursor gaps. Identity responses retain a 64 KiB bound; document responses allow a bounded 12 MiB envelope. UTF-8 is decoded strictly. Encoding and document decoding run off the UI thread. Requests name the expected account partition; an identity epoch invalidates delayed responses even after the same account signs back in. An old unauthorized response cannot clear a replacement session.

Room schema 3 adds account-partitioned synchronized shadows, working copies and immutable outgoing operations. Both documents and operation bodies use bounded Unicode-safe chunks. Schema 2 → 3 adds these tables without rewriting private boards. A local edit, its queue entry and local version commit in one transaction. Stale editor versions cannot overwrite another completed edit. The provisional outgoing queue allows at most 1000 operations per board and 32 MiB of delta bodies per account; the complete request must also fit the server's 8 MiB limit.

An acknowledgment retires only the matching head and requires a fresh authoritative snapshot covering its accepted revision. The snapshot, queue removal and rebased working document commit together. Independent pending edits rebase; a conflict keeps the previous visible local work and pending bodies while advancing the separate authoritative shadow. New edits are blocked until recovery is handled. A read-only grant preserves unsent content but prevents new shared edits. Personal camera/favorite/folder/Trash state stays local.

`SyncCoordinator` performs a serialized pass of up to 20 sends, checking the active account around network calls. Lost responses leave the original operation identity/body/base revision in Room; the next pass retries that same request. It fetches an authoritative snapshot before retiring an acknowledged entry. The background runner uses a smaller two-operation limit per board and bounded account-scoped WorkManager passes. Client snapshots do not include the server's complete tombstone history; server authorization and conflict responses remain authoritative. Shared editor/conflict recovery integration is still required.

## Explicit publishing and preview

Room schema 4 adds immutable initial-publication requests, their chunked documents and minimal completed receipts. A request is scoped by account and private source-board ID. Its operation UUID and captured saved content exist before any network request. Retrying uses that original capture even if the private original was subsequently changed. Completion installs the synchronized cache and receipt in one transaction, then drops the redundant captured body. A completed receipt opens the same remote board; it does not silently create a newer copy or resurrect a deleted remote board. Limits are 100 pending publications, 32 MiB of captured bodies and 10,000 lifetime receipts per account, with an 8 MiB bound including the wire envelope.

The library exposes an explicit publish confirmation showing the saved board and destination account. Viewport, favorites, folders and local Trash are removed before transfer. Images/PDFs are rejected until private attachment transfer exists. Pending requests remain visible with a manual retry action after network loss or reopening the app. WorkManager also schedules delivery of these already confirmed captures when network/battery constraints permit. Login by itself never stages an upload.

The cloud screen lists accessible boards with pagination and account-scoped downloaded metadata. Preview supports page navigation, pan/zoom and Fit to content, with an explicit offline/possibly-outdated notice for cached content after a network failure. Current server revocation/credential errors do not open a cached fallback. An editable local copy receives a new board identity and is clearly described as independent. Preview has no editing tools or shared Undo. Cloud views hide immediately when their account is no longer active.

Schema 3 → 4 preserves the old shadow, working copy and queue. Existing cached titles have an empty metadata default and use a fallback label until the next cache update; full document titles are retained.

## Device integration still required

The editor must route completed shared edits through the transactional store and present pending/offline/error/conflict states. Logging in does not upload private boards. Publication retry supports manual action and constrained background delivery. Shared-edit recovery and the final account-deletion/cache-retention policy still require implementation. Logout cancels the preceding account's scheduled work and invalidates delayed responses. Cached documents and creation receipts currently remain in private local storage while signed out; another account cannot see them.

The scheduler partitions unique work by account and verifies the configured server origin before execution. Each pass handles at most three captures and three boards, with two operations per board and a three-minute timeout. Continuations append rather than dropping a wake-up that arrives during worker completion. Transient failures use exponential backoff with a six-attempt bound; access/conflict/certificate errors stop automatic sending and preserve queued content. A blocked batch does not generate an endless continuation chain. The cloud screen exposes waiting/running/attention status and an explicit retry action. WorkManager execution is subject to OS scheduling/force-stop behavior.

Private media transfer must precede an operation that references a new asset. The server checks board ownership, file size/type/hash and asset references. Downloads stage, validate and atomically install files before exposing a document that needs them. Orphaned uploads, account deletion and restore require a defined cleanup policy.

Live notifications are hints about committed revisions; durable state is recovered from a revision cursor. The client rebases pending object operations against received state and stores a recovery copy when an operation conflicts. It must not resolve conflicts by sending its complete local snapshot over the server document.

## Undo and restoration gate

The pure model tests conditional inverse edits: Undo cannot overwrite a later edit by another participant. Current local editor history is not yet connected to this shared policy. Page-list history, groups, deletion Undo and identity restoration need explicit integration.

The current model rejects retired IDs. A copied recovery object can use a new ID, but ordinary shared Undo should use a deliberate causal restoration operation tied to an observed deletion, with stale/restoration races tested. Do not silently convert any old pending creation/update into restoration. Do not enable collaborative Undo until the editor history, outbox and server agree on these rules.

## Verification gates

Pure-model and server tests cover independent changes, conflicting edits, delete versus update, ID reuse, grouping/ungrouping, atomic structural conflict, page deletion/last page, personal metadata exclusion, concurrent reorder, conditional inverse edits, durable replay, invitation-history privacy, anonymous contributor retention, creation idempotency and concurrent owner quotas. The same backend cases pass on H2 and PostgreSQL. Seven Room device cases cover edit-queue durability; five more cover immutable initial publication, completion/cache rollback, quotas/media rejection and a version-3 migration fixture preserving unsent work. The Android/backend integration checks lost edit and creation acknowledgments, database reopen, original identities/body preservation, one board/event and cursor/queue completion. It also publishes a saved private board through the UI, previews it at phone/tablet sizes, creates a separate editable local copy and republishes without another cloud creation. These tests do not establish a complete automatic online/offline sync system.

Still required: two real clients, offline edits in both directions, interrupted/ambiguous sends, server restart, duplicate messages, history retention gaps, attachment failures, page/group structural races, role changes during active work, account switching and measured participant/load limits. Presence and comments follow the same authorization/reconnect discipline but have separate storage and delivery semantics.
