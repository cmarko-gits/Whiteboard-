param([Parameter(Mandatory = $true)] [string] $GitleaksPath)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskReportDirectory = Join-Path $taskRoot 'artifacts/security'
$taskSnapshot = Join-Path $taskReportDirectory ('source-scan-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskSnapshot -Force | Out-Null
Push-Location $taskRoot
try {
    # Includes untracked/hidden project sources; respects .gitignore, excluding generated files and local secrets.
    $taskFiles = if (Get-Command rg -ErrorAction SilentlyContinue) {
        @(& rg --files --hidden -g '!.git/**')
    } else {
        @(& git -c core.quotePath=false ls-files --cached --others --exclude-standard)
    }
    if ($LASTEXITCODE -ne 0 -or $taskFiles.Count -eq 0) { throw 'Source inventory failed.' }
    foreach ($taskFile in $taskFiles) {
        $taskDestination = Join-Path $taskSnapshot $taskFile
        New-Item -ItemType Directory -Path (Split-Path -Parent $taskDestination) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $taskRoot $taskFile) -Destination $taskDestination
    }
    & $GitleaksPath dir $taskSnapshot --no-banner --redact=100 --report-format json `
        --report-path (Join-Path $taskReportDirectory 'source-secrets.json') --exit-code 1
    if ($LASTEXITCODE -ne 0) { throw 'Source secret scan failed. Review the redacted security report.' }
    [ordered] @{
        checkedAtUtc = [DateTime]::UtcNow.ToString('o')
        files = $taskFiles.Count
        scanner = (& $GitleaksPath version | Out-String).Trim()
        coverage = 'Current nonignored project files including untracked files; excludes generated artifacts and ignored local credentials. No Git-history scan.'
        status = 'no-secrets-reported'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskReportDirectory 'source-secrets-summary.json') -Encoding utf8
} finally { Pop-Location }
