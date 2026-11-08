param(
    [string]$AdbPath,
    [string]$DeviceSerial = 'emulator-5554',
    [string]$Package = 'dev.whiteboard.app',
    [switch]$LoadHelpersOnly
)
$ErrorActionPreference = 'Stop'

function Read-LaunchTiming([string]$Output) {
    if ($Output -notmatch '(?m)^Status:\s+ok\s*$') { throw 'Activity launch did not report success.' }
    $total = [regex]::Match($Output, '(?m)^TotalTime:\s+(\d+)\s*$')
    $wait = [regex]::Match($Output, '(?m)^WaitTime:\s+(\d+)\s*$')
    if (!$total.Success -or !$wait.Success) { throw 'Activity launch timing is incomplete.' }
    return [ordered]@{ totalMs = [long]$total.Groups[1].Value; waitMs = [long]$wait.Groups[1].Value }
}

function Read-ProcessPss([string]$Output) {
    $modern = [regex]::Match($Output, 'TOTAL PSS:\s+(\d+)')
    if ($modern.Success) { return [long]$modern.Groups[1].Value }
    $legacy = [regex]::Match($Output, '(?m)^\s*TOTAL\s+(\d+)\s+')
    if ($legacy.Success) { return [long]$legacy.Groups[1].Value }
    return $null
}

if ($LoadHelpersOnly) { return }
if (!$AdbPath -or $DeviceSerial -notmatch '^[a-zA-Z0-9_.:-]+$' -or $Package -cne 'dev.whiteboard.app') {
    throw 'Expected an explicit device and the release application package.'
}
function Invoke-ProfileAdb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = (& $AdbPath -s $DeviceSerial @Arguments 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Device measurement failed: $result" }
    if ([Text.Encoding]::UTF8.GetByteCount($result) -gt 1048576) { throw 'Measurement output exceeds 1 MiB.' }
    return $result
}
$directory = Join-Path (Split-Path -Parent $PSScriptRoot) 'artifacts/device-release'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$packageInfo = Invoke-ProfileAdb shell dumpsys package $Package
if ($packageInfo -match '\bDEBUGGABLE\b') { throw 'Release measurement refuses a debuggable package.' }
$memory = Invoke-ProfileAdb shell dumpsys meminfo $Package
$frames = Invoke-ProfileAdb shell dumpsys gfxinfo $Package framestats
[IO.File]::WriteAllText((Join-Path $directory 'runtime-meminfo.txt'), $memory)
[IO.File]::WriteAllText((Join-Path $directory 'runtime-framestats.txt'), $frames)
$launches = @()
for ($sample = 1; $sample -le 3; $sample++) {
    Invoke-ProfileAdb shell am force-stop $Package | Out-Null
    $launch = Invoke-ProfileAdb -Arguments @('shell', 'am', 'start', '-W', '-n', "$Package/dev.whiteboard.app.MainActivity")
    [IO.File]::WriteAllText((Join-Path $directory "runtime-launch-$sample.txt"), $launch)
    $launches += Read-LaunchTiming $launch
}
$samples = @($launches | ForEach-Object { $_.totalMs } | Sort-Object)
$pss = Read-ProcessPss $memory
[ordered]@{
    schema = 1
    source = $env:GITHUB_SHA
    package = $Package
    api = (Invoke-ProfileAdb shell getprop ro.build.version.sdk).Trim()
    model = (Invoke-ProfileAdb shell getprop ro.product.model).Trim()
    scope = 'Software-GPU CI emulator; force-stop library launches with OS caches retained; memory/frame snapshot after release smoke. Not physical GPU, retained-memory, peak-memory or stylus-latency proof.'
    launchSamples = $launches
    launchMedianMs = $samples[1]
    launchMaxMs = $samples[-1]
    pssKbAfterSmoke = $pss
    pssStatus = $(if ($null -eq $pss) { 'unreported-format' } else { 'observed' })
    rawMemory = 'runtime-meminfo.txt'
    rawFrames = 'runtime-framestats.txt'
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $directory 'runtime-profile.json') -Encoding utf8
Write-Output 'Recorded release launch, process-memory and frame diagnostics; no universal performance threshold asserted.'
