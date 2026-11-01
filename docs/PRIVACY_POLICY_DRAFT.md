# Whiteboard privacy policy — local 0.3.0 review draft

Draft date: 31 October 2026. This describes the active local Android application. Publisher identity, support contact, public URL and effective date must be completed before any public distribution that requires them. No Play publication is being performed in this iteration.

## Local data

Whiteboard stores boards, drawings, text, page settings, folders, favorites, search indexes, thumbnails, imported images/PDFs, recovery drafts and preferences in application-private storage. The local application has no account, AI, cloud synchronization, advertisements, analytics or third-party crash-reporting service. It does not request INTERNET or ACCESS_NETWORK_STATE permissions. Android and device-store services have their own privacy and diagnostic controls.

Updating from an earlier development version preserves the Room database and its schema history. Previously created online-related compatibility tables can remain locally; the current application does not read account credentials for network use, schedule uploads or contact the former service. This release does not delete a remote account or data that may exist outside this device.

## Files and camera

Android's document picker grants access to the files you select. Imported material is copied into private storage so the document can work after its original provider is unavailable. Images may be resized/re-encoded as PNG; PDFs retain a private original. Choosing Take photo launches an installed camera application with temporary access to the capture destination. Whiteboard does not require broad photo-library access or a background camera feed. Some devices or work profiles may not provide a usable camera application.

## Export, sharing and backup

Export and sharing occur when you request them. You choose a destination using Android's document picker or sharing interface. The receiving provider may be a third-party cloud service; its treatment of the file follows its own terms. PNG/JPG/PDF exports contain rendered board content. Editable ZIP backups contain saved non-Trash boards and their referenced attachments, not unsaved edits, recovery drafts, settings or historical account/sync caches. Whiteboard does not encrypt exported ZIP files. Treat backups as copies of private documents.

The object clipboard is private to the current Whiteboard process and may retain copied media while switching pages or boards. Whiteboard does not read or write the system clipboard.

## Retention and deletion

Saved boards remain until you delete them or uninstall the application. Trash supports restoration. Permanent deletion removes the local board record, while attachments referenced by other boards, drafts, Undo/Redo or the current clipboard remain available. Unreferenced attachments are eligible for cleanup after 24 hours, during later maintenance; this is not a guaranteed deletion deadline. Disposable previews may be regenerated after Save. Temporary exports/captures are eligible for cleanup after one day when the corresponding operation next runs.

Recovery stores recently completed edits separately from manual Save. It cannot guarantee restoration of unfinished gestures, edits made before a successful recovery write, or data after device/storage failure. Recovery is not a backup.

Uninstalling removes application-private data. Automatic Android application backup and device transfer are disabled. Keep editable backups before uninstalling or changing devices. Files exported elsewhere must be deleted from their destination separately.

## Protection and contact

Local protection relies on Android's application sandbox and the device's security/encryption. The application does not provide a per-board password or its own database encryption. A compromised or unlocked device can expose local data. Exported files intentionally leave the application sandbox.

Publisher and support/privacy contact: to be supplied by the owner. Public policy URL and effective date: to be supplied before applicable public distribution. Feature or data-handling changes require a matching policy update.
