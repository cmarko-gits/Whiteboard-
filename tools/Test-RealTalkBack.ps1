param([Parameter(Mandatory = $true)][string]$AdbPath)
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or -not $IsLinux) { throw 'Only the disposable Linux qualification device is supported.' }
$directory = Join-Path $PWD 'artifacts/qa-extra'
$component = 'com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService'
function Adb([string[]]$Arguments) {
    $value = & $AdbPath -s emulator-5554 @Arguments 2>&1
    if ($LASTEXITCODE) { throw ($value -join "`n") }
    return ($value -join "`n").Trim()
}
$names = @('enabled_accessibility_services', 'accessibility_enabled', 'touch_exploration_enabled')
$original = @{}
foreach ($name in $names) { $original[$name] = Adb @('shell', 'settings', 'get', 'secure', $name) }
$report = [ordered]@{ status = 'incomplete'; sourceCommit = (git rev-parse HEAD).Trim(); spokenAudioReviewed = $false }
try {
    $location = Adb @('shell', 'pm', 'path', 'com.google.android.marvin.talkback')
    if ($location -notmatch '^package:') { throw 'Real TalkBack is not preinstalled. No substitute service or third-party APK is allowed.' }
    Adb @('shell', 'dumpsys', 'package', 'com.google.android.marvin.talkback') | Set-Content (Join-Path $directory 'talkback-package.txt')
    $services = if ($original.enabled_accessibility_services -eq 'null') { @() } else { @($original.enabled_accessibility_services -split ':') }
    $services = @($services | Where-Object { $_ })
    if ($component -notin $services) { $services += $component }
    Adb @('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', ($services -join ':')) | Out-Null
    Adb @('shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1') | Out-Null
    $deadline = [datetime]::UtcNow.AddSeconds(30)
    do {
        $state = Adb @('shell', 'dumpsys', 'accessibility')
        if ($state -match 'touchExplorationEnabled=true' -and $state -match 'TalkBackService') { break }
        Start-Sleep -Milliseconds 250
    } while ([datetime]::UtcNow -lt $deadline)
    $state | Set-Content (Join-Path $directory 'talkback-service.txt')
    if ($state -notmatch 'touchExplorationEnabled=true' -or $state -notmatch 'TalkBackService') { throw 'Real TalkBack did not bind with touch exploration.' }
    & ./tools/Test-Android.ps1 -AdbPath $AdbPath -Suite app -SkipInstall -Classes dev.whiteboard.app.RealTalkBackTest -ReportPrefix additional-talkback
    $output = Get-Content artifacts/additional-talkback-app-tests.txt -Raw
    if ($output -notmatch 'OK \(2 tests\)' -or $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED') { throw 'Expected both real-service integration cases.' }
    foreach ($name in @('qualification-talkback.json', 'qualification-talkback-settings.json', 'qualification-talkback.png')) {
        Adb @('pull', "/sdcard/Android/data/dev.whiteboard.app.debug/files/$name", (Join-Path $directory $name)) | Out-Null
    }
    $report.status = 'passed-real-service-integration-only'
    $report['cases'] = 2
    $report['scope'] = 'Actual TalkBack binding, touch exploration, focused switch changes, board journey and virtual canvas activation. Not a spoken-audio or complete usability review.'
} finally {
    # Restore every changed secure setting even if either integration case fails.
    foreach ($name in $names) {
        if ($original[$name] -eq 'null') { Adb @('shell', 'settings', 'delete', 'secure', $name) | Out-Null }
        else { Adb @('shell', 'settings', 'put', 'secure', $name, $original[$name]) | Out-Null }
    }
    $restored = @{}
    foreach ($name in $names) { $restored[$name] = Adb @('shell', 'settings', 'get', 'secure', $name) }
    $report['originalSettings'] = $original
    $report['restoredSettings'] = $restored
    $report['settingsRestored'] = @($names | Where-Object { $original[$_] -cne $restored[$_] }).Count -eq 0
    $report['checkedAtUtc'] = [datetime]::UtcNow.ToString('o')
    $report | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $directory 'talkback-integration.json')
    if (-not $report.settingsRestored) { throw 'Accessibility settings were not restored exactly.' }
}
