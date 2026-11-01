# Whiteboard privacy policy — publisher review draft

Draft date: 20 October 2026. This document describes the current development Android build with optional account flows. It is not a final policy for the agreed cloud/collaboration/AI release. Publisher identity, support contact, service providers/regions, retention, public URL and effective date must be supplied before publication. Update this draft as online document and AI features are implemented.

## Your boards stay on your device

Whiteboard lets you draw, write notes, import images/PDFs and organize boards. The current build stores board content, names, folders, favorites, recovery drafts, search indexes, thumbnails and settings in the application's private local storage. You can use it without an account or internet connection.

This build has no advertising, analytics, third-party crash-reporting, automatic cloud synchronization or AI SDK. It requests internet access for optional account actions and explicit cloud copies. Signing in does not upload local boards. Production hosting is not yet configured; debug testing connects only to a local development service. Operating-system and Google Play services have their own privacy terms and diagnostic controls.

## Optional account

When an account service is available and you choose to register/sign in, the configured Whiteboard server receives your email and password over HTTPS. The server stores a normalized email address, account ID, verification state and a salted Argon2id password hash, rather than your password. It also stores hashes of random session and one-time action tokens. Verification and password reset send a one-time code through the configured mail provider. Provider identity, region and actual operational retention must be supplied before production use.

The Android app stores session credentials encrypted with a key held by Android Keystore. Account actions may generate operational request metadata, including the connecting IP address, for service security and rate limiting. The application does not intentionally log passwords, session codes or document content. Actual hosting/ingress log retention still needs to be defined.

You can sign out or request account deletion inside the application. Deletion requires the current password and removes the live account, owned cloud boards, memberships, sessions and action codes from the current server database. It does not delete other participants' boards or local boards on this device. Existing contributions in another owner's board remain, with the account link removed from retained operation history. Final comment, recovery and backup retention policies still need publisher review. Signing out while offline clears this device's credentials, but remote revocation may remain unconfirmed until expiry. The complete cloud/sharing/AI user flows require additional disclosures before release.

## Optional cloud copies

When you explicitly publish a saved board, its name, pages, text, drawings and object structure are sent to the configured Whiteboard service for your account. The current flow creates a separate cloud copy; private original edits do not automatically update it. Personal camera position, favorites, folders and local Trash state are removed before upload. Cloud publication of boards containing images/PDFs is not available yet. Production transfer requires HTTPS; development checks use a local loopback service.

The application stores the captured upload request locally before sending. Retrying after a lost response uses the original capture and request identity. The redundant captured body is removed after the cloud copy and its completion receipt are saved; downloaded documents and minimal receipt metadata remain in private storage. They are scoped to the account and hidden while signed out. This development cache can remain after account deletion or session expiry until application data is removed or the app is uninstalled. Completing the production cache deletion/retention policy is a release requirement.

Opening a cloud board can download and cache its contents. A network failure may show the last downloaded copy with an offline/out-of-date notice. Making an editable local copy creates an independent private board. Further edits to that local copy do not change the cloud original. Continuous shared editing, comments, private attachment transfer and AI are not enabled by this flow.

## Files and camera

When you choose a file or photo, Android gives Whiteboard access to that selection. Whiteboard copies imported material into its private storage so a board remains usable when the original provider is unavailable. Images may be resized and converted to PNG; PDFs retain a private source copy. Whiteboard uses an installed camera app when you choose Take photo and gives it temporary access to the capture file. This does not grant Whiteboard general access to your photo library or a background camera feed.

## Export, sharing and backups

Exports and sharing happen when you request them. Android's document picker or sharing menu lets you choose the receiving location/application. A chosen destination may be a cloud service operated by someone else; its handling of the file follows that service's terms. Exported PNG/JPG/PDF files contain the visible board content. Editable ZIP backups contain saved non-Trash local boards and their attachments, including independent local copies you made from a cloud preview. Account caches, upload/edit queues, creation receipts, unsaved edits and Trash are excluded. ZIP backups are not encrypted by Whiteboard. They are not a cloud synchronization backup.

The object clipboard is private to the running Whiteboard process. It can retain copied objects/images while you switch boards. Whiteboard does not read or write the system clipboard.

## Retention and deletion

Saved boards remain until you delete them or uninstall the application. Moving a board to Trash permits restoration; it does not automatically erase it. Permanent deletion removes the board from the local database. Attachments shared with another board, a recovery draft, Undo/Redo history or the current object clipboard remain available while referenced. Unreferenced attachments become eligible for cleanup after 24 hours; cleanup runs during later library maintenance, so this is not a guaranteed deletion deadline.

Recovery drafts help restore recently completed edits after an interruption. They are separate from saved boards and portable backups. Recovery cannot guarantee restoration of unfinished gestures or edits made immediately before an unsuccessful write.

Temporary capture/export files are eligible for cleanup after one day when the associated operation next runs. Files you exported to another application or location must be deleted there. Uninstalling Whiteboard removes its private local data. Automatic Android application backup/device transfer is disabled in this build. Keep editable backups of important work before uninstalling or changing devices.

## Protection and your choices

The current build relies on Android's application sandbox and device security. It does not currently provide a per-board password or end-to-end encrypted cloud storage. Use device locking and choose backup destinations carefully. You can change theme, finger drawing, recognition/alignment settings and optional autosave without creating a profile.

## Contact and updates

Publisher: **to be supplied by the owner**. Support/privacy contact: **to be supplied by the owner**. Public policy URL and effective date: **to be supplied before release**. Material feature or data-handling changes require an updated policy and matching Play declarations before the new behavior ships.
