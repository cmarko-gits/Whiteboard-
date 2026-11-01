param(
    [string] $AdbPath = 'C:\Users\makro\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [string] $DeviceSerial = 'emulator-5554',
    [string] $JavaHome = 'C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskServerRoot = Join-Path $taskRoot 'server'
$taskArtifacts = Join-Path $taskRoot 'artifacts'
New-Item -ItemType Directory -Path $taskArtifacts -Force | Out-Null
"INCOMPLETE: cloud check started $([DateTime]::UtcNow.ToString('o')) on $DeviceSerial" |
    Set-Content -LiteralPath (Join-Path $taskArtifacts 'cloud-account-test.txt')
$taskClasspath = Join-Path $taskServerRoot 'build/install/server/lib/*'
if (-not (Test-Path -LiteralPath (Join-Path $taskServerRoot 'build/install/server/lib'))) {
    throw 'Build :server:installDist and :app:assembleDebugAndroidTest before running this check.'
}
$taskExistingListener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($taskExistingListener) { throw 'Port 8080 is already in use; this check must own its development server.' }
if ($env:WHITEBOARD_ENV -and $env:WHITEBOARD_ENV -ne 'development') { throw 'This check requires a development environment.' }
if ($env:WHITEBOARD_JDBC_URL -or $env:WHITEBOARD_SMTP_HOST) { throw 'Unset external database/mail overrides for this disposable local check.' }
if ($env:WHITEBOARD_PORT -and $env:WHITEBOARD_PORT -ne '8080') { throw 'This check requires development port 8080.' }
$taskProcess = Start-Process -FilePath (Join-Path $JavaHome 'bin/java.exe') -ArgumentList @(
    '-Xmx512m', '-cp', "`"$taskClasspath`"", 'dev.whiteboard.server.MainKt'
) -WorkingDirectory $taskServerRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskArtifacts 'cloud-server-out.log') -RedirectStandardError (Join-Path $taskArtifacts 'cloud-server-error.log')
$taskEmail = 'android-' + [Guid]::NewGuid().ToString('N') + '@example.invalid'
$taskAccess = $null
try {
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 40; $taskAttempt++) {
        if ($taskProcess.HasExited) { throw 'Development server exited. Inspect artifacts/cloud-server-error.log.' }
        try {
            $taskHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/health' -TimeoutSec 2
            if ($taskHealth.status -eq 'ok') { $taskReady = $true; break }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $taskReady) { throw 'Development server did not become ready.' }
    & (Join-Path $PSScriptRoot 'Test-BackendEndpoint.ps1') -Origin 'http://127.0.0.1:8080'
    $taskCredentials = @{ email = $taskEmail; password = 'disposable integration passphrase' } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri 'http://127.0.0.1:8080/v1/auth/register' -Method Post -ContentType 'application/json' -Body $taskCredentials | Out-Null
    $taskMail = Get-Content -LiteralPath (Join-Path $taskServerRoot 'build/dev-mail.jsonl') | ForEach-Object { ConvertFrom-Json $_ } | Where-Object { $_.email -eq $taskEmail -and $_.purpose -eq 'verify' } | Select-Object -Last 1
    if (-not $taskMail) { throw 'Missing local development verification message.' }
    $taskVerification = @{ code = $taskMail.token } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri 'http://127.0.0.1:8080/v1/auth/verify' -Method Post -ContentType 'application/json' -Body $taskVerification | Out-Null
    $taskLogin = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/v1/auth/login' -Method Post -ContentType 'application/json' -Body $taskCredentials
    $taskAccess = $taskLogin.accessToken
    & $AdbPath -s $DeviceSerial reverse tcp:8080 tcp:8080 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not forward the loopback development service to the Android device.' }
    & $AdbPath -s $DeviceSerial install -r (Join-Path $taskRoot 'app/build/outputs/apk/debug/app-debug.apk') | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the Android application.' }
    & $AdbPath -s $DeviceSerial install -r (Join-Path $taskRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk') | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the Android test application.' }
    $taskResult = & $AdbPath -s $DeviceSerial shell am instrument -w -e class dev.whiteboard.app.CloudAccountFlowTest -e whiteboard.cloudEmail $taskEmail dev.whiteboard.app.debug.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $taskResult | Set-Content -LiteralPath (Join-Path $taskArtifacts 'cloud-account-test.txt')
    $taskResultText = $taskResult -join "`n"
    if ($LASTEXITCODE -ne 0 -or $taskResultText -notmatch 'OK \(1 test\)' -or $taskResultText -match 'INSTRUMENTATION_STATUS_CODE: -3|AssumptionViolated') { throw 'Android cloud account test failed or skipped; inspect artifacts/cloud-account-test.txt.' }
    & $AdbPath -s $DeviceSerial pull /sdcard/Android/data/dev.whiteboard.app.debug/files/account-signed-in.png (Join-Path $taskArtifacts 'account-signed-in.png') | Out-Null
    & $AdbPath -s $DeviceSerial pull /sdcard/Android/data/dev.whiteboard.app.debug/files/cloud-preview.png (Join-Path $taskArtifacts 'cloud-preview.png') | Out-Null
    & $AdbPath -s $DeviceSerial pull /sdcard/Android/data/dev.whiteboard.app.debug/files/cloud-library.png (Join-Path $taskArtifacts 'cloud-library.png') | Out-Null
    & $AdbPath -s $DeviceSerial pull /sdcard/Android/data/dev.whiteboard.app.debug/files/cloud-phone-landscape.png (Join-Path $taskArtifacts 'cloud-phone-landscape.png') | Out-Null
    & $AdbPath -s $DeviceSerial pull /sdcard/Android/data/dev.whiteboard.app.debug/files/cloud-tablet-preview.png (Join-Path $taskArtifacts 'cloud-tablet-preview.png') | Out-Null
    Write-Output 'Android login, encrypted session restore, durable drawing/group queue reopen, lost-acknowledgment retry, cursor replay, real WorkManager delivery, logout and deletion passed against the local Ktor backend.'
} finally {
    & $AdbPath -s $DeviceSerial pull /sdcard/Android/data/dev.whiteboard.app.debug/files/cloud-account-failure.xml (Join-Path $taskArtifacts 'cloud-account-failure.xml') 2>$null | Out-Null
    & $AdbPath -s $DeviceSerial pull /sdcard/Android/data/dev.whiteboard.app.debug/files/cloud-account-failure.png (Join-Path $taskArtifacts 'cloud-account-failure.png') 2>$null | Out-Null
    if ($taskAccess) {
        try {
            Invoke-RestMethod -Uri 'http://127.0.0.1:8080/v1/account' -Method Delete -Headers @{ Authorization = "Bearer $taskAccess" } -ContentType 'application/json' -Body (@{ password = 'disposable integration passphrase' } | ConvertTo-Json -Compress) | Out-Null
        } catch { }
    }
    & $AdbPath -s $DeviceSerial reverse --remove tcp:8080 2>$null | Out-Null
    if (-not $taskProcess.HasExited) { Stop-Process -Id $taskProcess.Id }
}
