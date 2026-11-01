param(
    [string] $ApkPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'app/build/outputs/apk/release/app-release-unsigned.apk'),
    [string] $LicenseReport = (Join-Path (Split-Path -Parent $PSScriptRoot) 'artifacts/dependency-licenses.json'),
    [string] $ApacheLicense = (Join-Path (Split-Path -Parent $PSScriptRoot) 'docs/licenses/APACHE-2.0.txt'),
    [switch] $Verify
)
$ErrorActionPreference = 'Stop'
$taskItems = Get-Content -LiteralPath $LicenseReport -Raw | ConvertFrom-Json
if (@($taskItems | Where-Object { $_.Licenses.Count -eq 0 -or -not $_.Licenses[0].Name }).Count) {
    throw 'Resolve missing license metadata before building application notices.'
}
$taskText = [Text.StringBuilder]::new()
[void]$taskText.AppendLine('Whiteboard — open source notices')
[void]$taskText.AppendLine('The following third-party runtime graph components and their published licenses are acknowledged. The graph also includes metadata/BOM components. Upstream package notices follow the inventory.')
foreach ($taskItem in $taskItems) {
    [void]$taskText.AppendLine()
    [void]$taskText.AppendLine($taskItem.Coordinate)
    [void]$taskText.AppendLine($taskItem.ProjectUrl)
    foreach ($taskLicense in $taskItem.Licenses) { [void]$taskText.AppendLine("$($taskLicense.Name) — $($taskLicense.Url)") }
}
[void]$taskText.AppendLine("`nApache License 2.0 — full text`n")
[void]$taskText.AppendLine((Get-Content -LiteralPath $ApacheLicense -Raw))
$taskSeen = @{}
$taskZip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ApkPath))
try {
    foreach ($taskEntry in ($taskZip.Entries | Where-Object { $_.FullName -match '^META-INF/.*(LICENSE|NOTICE)' })) {
        $taskReader = [IO.StreamReader]::new($taskEntry.Open())
        try { $taskContent = $taskReader.ReadToEnd() } finally { $taskReader.Dispose() }
        $taskHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($taskContent)))
        if (-not $taskSeen.ContainsKey($taskHash)) {
            [void]$taskText.AppendLine("`nPackaged notice: $($taskEntry.FullName)`n")
            [void]$taskText.AppendLine($taskContent)
            $taskSeen[$taskHash] = $true
        }
    }
} finally { $taskZip.Dispose() }
$taskAssets = Join-Path (Split-Path -Parent $PSScriptRoot) 'app/src/main/assets'
New-Item -ItemType Directory -Path $taskAssets -Force | Out-Null
$taskNoticePath = Join-Path $taskAssets 'open-source-notices.txt'
if ($Verify) {
    if (-not (Test-Path -LiteralPath $taskNoticePath) -or
        (Get-Content -LiteralPath $taskNoticePath -Raw).Replace("`r`n", "`n").TrimEnd() -cne
        $taskText.ToString().Replace("`r`n", "`n").TrimEnd()) {
        throw 'Packaged license notices differ from the resolved runtime graph. Regenerate notices and rebuild.'
    }
    Write-Output "Verified notices for $($taskItems.Count) runtime graph components."
} else {
    $taskText.ToString() | Set-Content -LiteralPath $taskNoticePath -Encoding utf8
    Write-Output "Generated notices for $($taskItems.Count) runtime graph components and $($taskSeen.Count) distinct packaged notice texts."
}
