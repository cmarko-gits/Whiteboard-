# Backend build and operations

The service is packaged locally; no public deployment is implied. Build with JDK 21 and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat :server:test :server:postgresTest :server:installDist :server:distZip
```

`server/build/install/server` includes the startup scripts and runtime JARs. Test databases/native test executables, source credentials, development mail and Android APKs are excluded. ZIP entries have stable ordering and timestamps. Dependency versions are fixed in the version catalog; artifact verification and an image digest belong in the production build pipeline.

On Windows, `tools/Test-BackendPackage.ps1` launches that distribution on loopback port 18080 using an isolated in-memory H2 database, checks startup/migrations and HTTP response policy, then stops its own process. It does not use a production database or SMTP endpoint. This check passed on 31 October 2026; it does not qualify public infrastructure or container execution.

The optional container recipe takes the already built distribution, runs as UID/GID 10001 and defaults to production. From the repository root: `docker build -f server/Dockerfile -t whiteboard-backend:0.2.0 .`. Docker is not available on the current workstation, so container execution and image vulnerability scanning require the deployment environment. Pin the JRE base digest there and scan the complete image, including OS/JRE packages. The Maven audit does not cover those packages.

Provide all production values listed in BACKEND.md through the platform secret store. Do not bake them into an image or commit an environment file. PostgreSQL must present a valid certificate matching its hostname with `sslmode=verify-full`; install the issuing CA using the driver's supported root-certificate configuration. SMTP must support verified STARTTLS. An unset service origin in the Android release disables cloud and is a release blocker.

Run with a read-only root filesystem, a bounded temporary directory, dropped Linux capabilities, no privilege escalation, a memory/CPU limit and graceful termination. Only the trusted HTTPS ingress may reach port 8080. Restrict outbound traffic to the selected database, SMTP service and future approved AI endpoint. Enforce distributed abuse limits and request/idle/connection bounds at the ingress. Do not trust arbitrary forwarded client-address headers.

`GET /health` reports process responsiveness; it is not a database/SMTP readiness test. Startup migrates the database before accepting requests and rejects migration checksum mismatch/downgrade. For deployment promotion, exercise a disposable account through registration/mail verification, publishing, edits, membership authorization, reset and deletion against the actual staging services. Check the public transport/headers with:

```powershell
.\tools\Test-BackendEndpoint.ps1 -Origin 'https://your-staging-origin.example'
```

That probe sends no credentials and does not bypass TLS validation. It checks health, unauthenticated denial and missing-route headers. It does not prove account flows, distributed limiting or backup restoration. Do not disable certificate checks to make it pass.

Before migration, take an encrypted database backup and verify retention/access policy. Rehearse restoration into an isolated database and compare schema versions, account/board counts and representative document digests. Keep the previous application artifact available; rollback must respect forward-only migrations, and a database downgrade is never automatic. Record recovery time/data-loss objectives, on-call ownership and deletion/backup retention before public launch.

Monitor bounded aggregate counts and timings for HTTP failures, database pool pressure, sync conflicts, mail failures and resource use. Do not log Authorization headers, request bodies, passwords, action/invite codes or board content. Alert on sustained failure; route and status metadata are sufficient for baseline operational dashboards.

Reference: [Docker build stages and artifact separation](https://docs.docker.com/build/building/multi-stage/). Container execution, production TLS/SMTP, restore drills, distributed abuse limits and live staging qualification remain deployment gates.
