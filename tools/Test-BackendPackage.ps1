param([string] $JavaHome = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskPort = 18080
$taskClasspath = Join-Path $taskRoot 'server/build/install/server/lib/*'
if (-not (Test-Path -LiteralPath (Split-Path -Parent $taskClasspath))) { throw 'Build :server:installDist first.' }
if (Get-NetTCPConnection -LocalPort $taskPort -State Listen -ErrorAction SilentlyContinue) { throw 'Isolated probe port already in use.' }
$taskStart = [Diagnostics.ProcessStartInfo]::new((Join-Path $JavaHome 'bin/java.exe'))
$taskStart.UseShellExecute = $false
$taskStart.CreateNoWindow = $true
$taskStart.WorkingDirectory = Join-Path $taskRoot 'server'
$taskStart.RedirectStandardOutput = $true
$taskStart.RedirectStandardError = $true
foreach ($taskArgument in @('-Xmx512m', '-cp', $taskClasspath, 'dev.whiteboard.server.MainKt')) { $taskStart.ArgumentList.Add($taskArgument) }
$taskStart.Environment['WHITEBOARD_ENV'] = 'development'
$taskStart.Environment['WHITEBOARD_HOST'] = '127.0.0.1'
$taskStart.Environment['WHITEBOARD_PORT'] = "$taskPort"
$taskStart.Environment['WHITEBOARD_JDBC_URL'] = 'jdbc:h2:mem:package-probe;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1'
$taskStart.Environment['WHITEBOARD_DB_USER'] = 'sa'
$taskStart.Environment['WHITEBOARD_DB_PASSWORD'] = ''
[void] $taskStart.Environment.Remove('WHITEBOARD_SMTP_HOST')
$taskProcess = [Diagnostics.Process]::Start($taskStart)
$taskOutput = $taskProcess.StandardOutput.ReadToEndAsync()
$taskErrors = $taskProcess.StandardError.ReadToEndAsync()
try {
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 40; $taskAttempt++) {
        if ($taskProcess.HasExited) { throw 'Packaged backend exited before becoming ready.' }
        try {
            if ((Invoke-RestMethod -Uri "http://127.0.0.1:$taskPort/health" -TimeoutSec 2).status -eq 'ok') {
                $taskReady = $true
                break
            }
        } catch { Start-Sleep -Milliseconds 250 }
    }
    if (-not $taskReady) { throw 'Packaged backend did not become ready.' }
    & (Join-Path $PSScriptRoot 'Test-BackendEndpoint.ps1') -Origin "http://127.0.0.1:$taskPort"
    Write-Output 'Packaged backend startup/migration and HTTP policy passed using an isolated in-memory database.'
} finally {
    if (-not $taskProcess.HasExited) { $taskProcess.Kill() }
    [void] $taskProcess.WaitForExit(5000)
    $taskArtifacts = Join-Path $taskRoot 'artifacts'
    New-Item -ItemType Directory -Path $taskArtifacts -Force | Out-Null
    $taskOutput.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $taskArtifacts 'backend-package-out.log')
    $taskErrors.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $taskArtifacts 'backend-package-error.log')
    $taskProcess.Dispose()
}
