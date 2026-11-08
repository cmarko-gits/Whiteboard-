$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Measure-ReleaseRuntime.ps1') -LoadHelpersOnly
$observed = Read-LaunchTiming "Status: ok`nTotalTime: 321`nWaitTime: 456`n"
if ($observed.totalMs -ne 321 -or $observed.waitMs -ne 456) { throw 'Valid launch timings were changed.' }
foreach ($invalid in @('Status: timeout', "Status: ok`nWaitTime: 4", "Status: ok`nTotalTime: -3`nWaitTime: 4")) {
    $rejected = $false
    try { Read-LaunchTiming $invalid | Out-Null } catch { $rejected = $true }
    if (!$rejected) { throw 'Incomplete, negative or failed launches must not become usable timing samples.' }
}
if ((Read-ProcessPss 'TOTAL PSS: 12345 TOTAL RSS: 45678') -ne 12345) { throw 'Modern memory sample changed.' }
if ((Read-ProcessPss "    TOTAL  23456  5678  1234`n") -ne 23456) { throw 'Legacy memory sample changed.' }
if ($null -ne (Read-ProcessPss 'Unknown memory format')) { throw 'Missing memory must remain unknown, not zero.' }
Write-Output 'PASS: release timing/memory parsing preserves observations and rejects incomplete samples; no device commands executed.'
