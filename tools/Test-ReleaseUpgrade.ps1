param(
    [Parameter(Mandatory = $true)][string]$AdbPath,
    [Parameter(Mandatory = $true)][string]$NewApkPath,
    [string]$DeviceSerial = 'emulator-5554'
)
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -cne 'true') { throw 'Binary upgrade qualification is restricted to disposable CI devices.' }
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath $AdbPath -DeviceSerial $DeviceSerial -Package dev.whiteboard.app -LoadHelpersOnly
$directory = Join-Path $taskRoot 'artifacts/device-release'
Find-Ui -Description 'New board' | Out-Null
$fixture = @(Get-Ui | Where-Object { $_.text -like 'Recovery_check_*' -and $_.class -eq 'android.widget.TextView' })
if ($fixture.Count -ne 1) { throw 'Expected exactly one saved baseline board in this fresh release installation.' }
$name = [string]$fixture[0].text
Tap-Ui $fixture[0]
Find-Ui -Text 'Saved on this device' | Out-Null
$before = (Get-Rect (Find-Ui -Description 'Drawing stroke')) -join ','
Tap-Ui (Find-Ui -Description 'Back to boards')
# Same ephemeral signer, same application ID, install -r only. No uninstall/clear data.
Invoke-Adb install -r $NewApkPath | Out-Null
Invoke-Adb shell am force-stop dev.whiteboard.app | Out-Null
Invoke-Adb -Arguments @('shell', 'am', 'start', '-W', '-n', 'dev.whiteboard.app/dev.whiteboard.app.MainActivity') | Out-Null
Find-Ui -Description 'New board' | Out-Null
Tap-Ui (Find-Ui -Text $name -Class 'android.widget.TextView')
Find-Ui -Text 'Saved on this device' | Out-Null
$after = (Get-Rect (Find-Ui -Description 'Drawing stroke')) -join ','
if ($after -cne $before) { throw 'Saved baseline drawing geometry changed across the binary upgrade.' }
Capture 'release-upgrade-preserved'
Tap-Ui (Find-Ui -Description 'Back to boards')
Find-Ui -Description 'New board' | Out-Null
$baseline = Get-Content -LiteralPath (Join-Path $directory 'upgrade-baseline.json') -Raw | ConvertFrom-Json
[ordered]@{
    status = 'passed'
    baselineSource = $baseline.sourceCommit
    baselineUnsignedSha256 = $baseline.unsignedApkSha256
    candidateSource = $env:GITHUB_SHA
    candidateSignedSha256 = (Get-FileHash -LiteralPath $NewApkPath -Algorithm SHA256).Hash
    fixture = $name
    beforeStrokeFrame = $before
    afterStrokeFrame = $after
    scope = 'API 36 CI binary install -r upgrade; saved 0.7.2 drawing reopened without uninstall or data reset. Separate legacy migration tests verify the document/recovery payload.'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'release-upgrade.json') -Encoding utf8
Write-Output 'PASS: optimized 0.7.2 binary upgrade preserves the saved board and drawing without clearing data.'
