# Inactive online sources

The current application is local-only. Files under online/ preserve earlier online-only Android code and tests; they are not in an Android source set. The server/, sync/ and feature/cloud/ directories at the repository root are also excluded from settings.gradle.kts. They are retained for reference, not built, deployed or advertised as current functionality.

Room schema 4 and historical migrations remain compatible with earlier installations. Legacy outbox table/entity declarations are schema compatibility data, not a running network client. The local APK has no INTERNET permission, account graph or WorkManager dependency. Compiled APK inspection and the runtime permission test enforce that boundary.
