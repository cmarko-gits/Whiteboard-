param(
    [string] $AdbPath = 'C:\Users\makro\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [string] $DeviceSerial = 'emulator-5554',
    [ValidateSet('data', 'app', 'all')] [string] $Suite = 'all',
    [switch] $SkipInstall,
    [switch] $InstallOnly,
    [string[]] $Classes = @(),
    [string[]] $ExcludeClasses = @(),
    [ValidatePattern('^[A-Za-z0-9_-]+$')] [string] $ReportPrefix = 'android'
)
$ErrorActionPreference = 'Stop'
if ($SkipInstall -and $InstallOnly) { throw 'Choose installation or verified reuse, not both.' }
if ($Classes.Count -and $ExcludeClasses.Count) { throw 'Choose an explicit class selection or exclusions, not both.' }
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskArtifacts = Join-Path $taskRoot 'artifacts'
New-Item -ItemType Directory -Path $taskArtifacts -Force | Out-Null
function Invoke-TestAdb([string[]] $Arguments, [int] $TimeoutSeconds = 120) {
    $taskStart = [Diagnostics.ProcessStartInfo]::new($AdbPath)
    $taskStart.UseShellExecute = $false
    $taskStart.CreateNoWindow = $true
    $taskStart.RedirectStandardOutput = $true
    $taskStart.RedirectStandardError = $true
    foreach ($taskArgument in @('-s', $DeviceSerial) + $Arguments) { $taskStart.ArgumentList.Add($taskArgument) }
    $taskProcess = [Diagnostics.Process]::Start($taskStart)
    try {
        $taskOut = $taskProcess.StandardOutput.ReadToEndAsync()
        $taskError = $taskProcess.StandardError.ReadToEndAsync()
        $taskClock = [Diagnostics.Stopwatch]::StartNew()
        while (-not $taskProcess.WaitForExit(1000)) {
            if ($taskClock.Elapsed.TotalSeconds -ge $TimeoutSeconds) {
                $taskProcess.Kill()
                throw "ADB timed out on $DeviceSerial; no passing test result is recorded."
            }
        }
        $taskText = $taskOut.GetAwaiter().GetResult() + $taskError.GetAwaiter().GetResult()
        return [pscustomobject]@{ Code = $taskProcess.ExitCode; Text = $taskText }
    } finally { $taskProcess.Dispose() }
}
function Install-TestApk([string] $RelativePath, [string] $Package) {
    if ($SkipInstall) {
        $taskLocation = Invoke-TestAdb -Arguments @('shell', 'pm', 'path', $Package)
        $taskPackageLine = $taskLocation.Text -split "`n" | Where-Object { $_.StartsWith('package:') } | Select-Object -First 1
        if ($taskLocation.Code -ne 0 -or -not $taskPackageLine) { throw "Cannot locate installed package $Package." }
        $taskPath = $taskPackageLine.Trim().Substring(8)
        if ($taskPath -notmatch '^/data/app/[A-Za-z0-9._~+/=-]+/base\.apk$') {
            throw "Cannot verify installed package $Package."
        }
        $taskInstalledHash = Invoke-TestAdb -Arguments @('shell', 'sha256sum', $taskPath)
        $taskExpectedHash = (Get-FileHash -LiteralPath (Join-Path $taskRoot $RelativePath) -Algorithm SHA256).Hash
        if ($taskInstalledHash.Code -ne 0 -or ($taskInstalledHash.Text -split '\s+')[0] -ne $taskExpectedHash) {
            throw "Installed $Package differs from the built APK; rerun without -SkipInstall."
        }
        return
    }
    $taskInstalled = Invoke-TestAdb -Arguments @('install', '--no-streaming', '-r', (Join-Path $taskRoot $RelativePath))
    if ($taskInstalled.Code -ne 0) { throw "Could not install $RelativePath on $DeviceSerial." }
}
function Run-Instrumentation([string] $Package, [string] $Name) {
    $taskArguments = @('shell', 'am', 'instrument', '-w')
    if ($Classes.Count) { $taskArguments += @('-e', 'class', ($Classes -join ',')) }
    if ($ExcludeClasses.Count) { $taskArguments += @('-e', 'notClass', ($ExcludeClasses -join ',')) }
    $taskArguments += "$Package/androidx.test.runner.AndroidJUnitRunner"
    $taskResult = Invoke-TestAdb -Arguments $taskArguments -TimeoutSeconds 600
    $taskText = $taskResult.Text
    $taskText | Set-Content -LiteralPath (Join-Path $taskArtifacts "$ReportPrefix-$Name-tests.txt")
    if ($taskResult.Code -ne 0 -or $taskText -notmatch 'OK \(\d+ tests?\)' -or $taskText -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
        throw "Android $Name checks failed. Inspect artifacts/$ReportPrefix-$Name-tests.txt."
    }
    Write-Output "Android $Name checks passed on $DeviceSerial."
}
if ($Suite -in @('data', 'all')) {
    "INCOMPLETE: run started $([DateTime]::UtcNow.ToString('o')) on $DeviceSerial" |
        Set-Content -LiteralPath (Join-Path $taskArtifacts "$ReportPrefix-data-tests.txt")
    Install-TestApk 'data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk' 'dev.whiteboard.data.test'
    if (-not $InstallOnly) { Run-Instrumentation 'dev.whiteboard.data.test' 'data' }
}
if ($Suite -in @('app', 'all')) {
    "INCOMPLETE: run started $([DateTime]::UtcNow.ToString('o')) on $DeviceSerial" |
        Set-Content -LiteralPath (Join-Path $taskArtifacts "$ReportPrefix-app-tests.txt")
    Install-TestApk 'app/build/outputs/apk/debug/app-debug.apk' 'dev.whiteboard.app.debug'
    Install-TestApk 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk' 'dev.whiteboard.app.debug.test'
    if (-not $InstallOnly) { Run-Instrumentation 'dev.whiteboard.app.debug.test' 'app' }
}
if ($InstallOnly) { Write-Output 'Selected APKs installed; no tests executed.' }
