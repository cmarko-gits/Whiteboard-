# Runtime dependencies and notices

The release runtime graph was inventoried again on 30 October 2026. All 154 graph components, including metadata/BOM entries, have published license metadata. This is a metadata/package review, not a legal opinion. The separate runtime advisory check covers 218 distinct Android/backend Maven package/version pairs and returned zero known OSV findings; scope and limits are recorded in SECURITY.md.

`app/src/main/assets/open-source-notices.txt` contains the component inventory, upstream/license URLs, the full Apache 2.0 text and distinct LICENSE/NOTICE texts preserved from the generated APK. Settings exposes a locally readable Open source licenses dialog. Upstream notices remain in the APK as well.

To refresh after changing dependencies:

```powershell
.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath --console=plain --quiet > artifacts/release-dependencies.txt
.\tools\Get-DependencyLicenses.ps1 -FetchMissing
.\gradlew.bat :app:assembleRelease
.\tools\Build-LicenseNotices.ps1
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:lintRelease
```

The report tool reads cached Maven POMs and, with `-FetchMissing`, downloads missing metadata only from Google Maven or Maven Central. XML DTD/external-entity processing is disabled. It reports unresolved licenses rather than assigning one by inference. `Build-LicenseNotices.ps1` refuses unresolved entries. The full Apache license text used by that script is downloaded from `https://www.apache.org/licenses/LICENSE-2.0.txt` into `artifacts/apache-license-2.0.txt`.

Review changed libraries, notices, network behavior, native compatibility and privacy declarations before distribution. Do not treat this snapshot as approval for future SDKs. The Android app now requests internet access for optional account actions through platform HTTPS/Keystore APIs; it has no analytics, advertising or third-party cloud SDK. The portable format's JVM JSON dependency is compile-only on Android, which uses the platform JSON implementation.

The server has a separate runtime graph (Ktor/Netty, JDBC/Hikari, Argon2id/Bouncy Castle, mail and JSON) and needs its own final deployment notice/license inventory. Its original JAR notices remain in the distribution. Embedded PostgreSQL and native test binaries are test-only and are excluded from both the production server distribution and APK. The Android inventory above must not be represented as a completed server license review.
