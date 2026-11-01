param([string] $InventoryPath = '')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskDirectory = Join-Path $taskRoot 'artifacts/security'
New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null
if (-not $InventoryPath) { $InventoryPath = Join-Path $taskDirectory 'runtime-dependencies.tsv' }
$taskReportPath = Join-Path $taskDirectory 'dependency-audit.json'
$taskReport = [ordered] @{
    checkedAtUtc = [DateTime]::UtcNow.ToString('o')
    status = 'incomplete'
    source = 'https://api.osv.dev/v1/querybatch'
    coverage = 'Resolved local Android release runtime Maven packages; inactive online modules, build tools and native operating system packages excluded.'
    packages = 0
    inventorySha256 = $null
    findings = @()
}
try {
    $taskRows = @(Import-Csv -LiteralPath $InventoryPath -Delimiter "`t")
    if ($taskRows.Count -eq 0) { throw 'No resolved runtime dependency inventory. Run exportRuntimeDependencies first.' }
    $taskReport.inventorySha256 = (Get-FileHash -LiteralPath $InventoryPath -Algorithm SHA256).Hash
    $taskPackages = @($taskRows | Group-Object package,version | ForEach-Object { $_.Group[0] })
    $taskFindings = [System.Collections.Generic.List[object]]::new()
    for ($taskOffset = 0; $taskOffset -lt $taskPackages.Count; $taskOffset += 100) {
        $taskBatch = @($taskPackages | Select-Object -Skip $taskOffset -First 100)
        $taskQueries = @($taskBatch | ForEach-Object { @{ package = @{ ecosystem = 'Maven'; name = $_.package }; version = $_.version } })
        $taskResponse = Invoke-RestMethod -Uri $taskReport.source -Method Post -ContentType 'application/json' -TimeoutSec 60 `
            -Body (@{ queries = $taskQueries } | ConvertTo-Json -Depth 8 -Compress)
        if (@($taskResponse.results).Count -ne $taskBatch.Count) { throw 'OSV returned an incomplete dependency result.' }
        for ($taskIndex = 0; $taskIndex -lt $taskBatch.Count; $taskIndex++) {
            $taskItem = $taskResponse.results[$taskIndex]
            if ($taskItem.next_page_token) { throw 'OSV pagination requires additional review; scan is incomplete.' }
            foreach ($taskVulnerability in @($taskItem.vulns)) {
                if (-not $taskVulnerability) { continue }
                $taskCoordinate = $taskBatch[$taskIndex]
                $taskFindings.Add([ordered] @{
                    package = $taskCoordinate.package; version = $taskCoordinate.version; id = $taskVulnerability.id
                    scopes = @($taskRows | Where-Object { $_.package -eq $taskCoordinate.package -and $_.version -eq $taskCoordinate.version } |
                        Select-Object -ExpandProperty scope -Unique)
                    reference = "https://osv.dev/vulnerability/$($taskVulnerability.id)"
                })
            }
        }
    }
    $taskReport.packages = $taskPackages.Count
    $taskReport.findings = @($taskFindings.ToArray())
    $taskReport.status = if ($taskFindings.Count -eq 0) { 'no-known-vulnerabilities-reported' } else { 'findings-require-review' }
} finally {
    $taskReport | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $taskReportPath -Encoding utf8
}
Write-Output "Runtime dependencies checked: $($taskReport.packages); findings: $($taskReport.findings.Count)."
if ($taskReport.status -ne 'no-known-vulnerabilities-reported') { throw 'Dependency security check did not pass. Inspect artifacts/security/dependency-audit.json.' }
