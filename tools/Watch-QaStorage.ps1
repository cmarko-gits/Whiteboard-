param(
    [Parameter(Mandatory = $true)] [int] $EmulatorProcessId,
    [string] $AdbPath = 'C:\Users\makro\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [string] $DeviceSerial = 'emulator-5554',
    [ValidateRange(1, 60)] [int] $MaximumMinutes = 30
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskBaseline = Get-Content -LiteralPath (Join-Path $taskRoot 'artifacts/qualification-storage-baseline.json') -Raw | ConvertFrom-Json
$taskReportPath = Join-Path $taskRoot 'artifacts/task-09-runtime-storage.json'
$taskUntil = [DateTime]::UtcNow.AddMinutes($MaximumMinutes)
$taskReport = [ordered]@{ startedAtUtc = [DateTime]::UtcNow.ToString('o'); peakAdditionalBytes = 0L; maxAdditionalBytes = $taskBaseline.maxAdditionalBytes; stoppedForReserve = $false; status = 'monitoring' }
try {
    while ((Get-Process -Id $EmulatorProcessId -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $taskUntil) {
        $taskAdditional = $taskBaseline.freeBytesAtStart - [IO.DriveInfo]::new('C:').AvailableFreeSpace
        $taskReport.peakAdditionalBytes = [Math]::Max($taskReport.peakAdditionalBytes, $taskAdditional)
        if ($taskAdditional -ge $taskBaseline.maxAdditionalBytes - 200000000) {
            $taskAvd = @(& $AdbPath -s $DeviceSerial emu avd name 2>$null | ForEach-Object { $_.Trim() })
            if ($LASTEXITCODE -ne 0 -or $taskAvd.Count -eq 0 -or $taskAvd[0] -notin @('Small_Phone', 'Whiteboard_QA')) {
                throw 'Storage reserve reached, but QA emulator identity could not be verified.'
            }
            & $AdbPath -s $DeviceSerial emu kill | Out-Null
            $taskReport.stoppedForReserve = $true
            $taskReport.status = 'stopped-for-storage-reserve'
            break
        }
        $taskReport | ConvertTo-Json | Set-Content -LiteralPath $taskReportPath
        Start-Sleep -Seconds 1
    }
    if ($taskReport.status -eq 'monitoring') {
        $taskReport.status = if ([DateTime]::UtcNow -ge $taskUntil) { 'monitor-time-limit' } else { 'emulator-exited' }
    }
} finally {
    $taskReport.finishedAtUtc = [DateTime]::UtcNow.ToString('o')
    $taskReport | ConvertTo-Json | Set-Content -LiteralPath $taskReportPath
}
