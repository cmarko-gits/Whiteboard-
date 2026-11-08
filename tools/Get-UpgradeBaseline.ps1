$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -cne 'true' -or $env:GITHUB_REPOSITORY -cne 'cmarko-gits/Whiteboard-' -or !$env:GITHUB_TOKEN) {
    throw 'Upgrade baseline download is restricted to this repository CI; no local download was attempted.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
# Immutable, previously qualified 0.7.2/source 1176e59 artifact. Never fall back to latest.
$archiveSha = '02BBB925986D10F7FC08414DC82FA96E72704341A63689E7F9C7E69C76600DB2'
$apkSha = '984848CF8B9587C70BCBDF78C42941A70C88E4CF550E5C23845E0669305BC0A5'
$uri = 'https://api.github.com/repos/cmarko-gits/Whiteboard-/actions/artifacts/13446823155/zip'
$headers = @{ 'User-Agent' = 'Whiteboard-upgrade-qualification'; Authorization = 'Bearer ' + $env:GITHUB_TOKEN }
$response = $null
$zip = $null
try {
    $response = Invoke-WebRequest -Uri $uri -Headers $headers -TimeoutSec 60
    $memory = $response.RawContentStream
    if (!$memory.CanSeek -or $memory.Length -gt 33554432) { throw 'Unexpected baseline archive size.' }
    $memory.Position = 0
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = [Convert]::ToHexString($sha.ComputeHash($memory)) } finally { $sha.Dispose() }
    if ($hash -cne $archiveSha) { throw 'Baseline artifact digest changed.' }
    $memory.Position = 0
    $zip = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Read, $true)
    $entry = $zip.GetEntry('app/build/outputs/apk/release/app-release-unsigned.apk')
    if (!$entry -or $entry.Length -ne 2481384) { throw 'Baseline APK length/path changed.' }
    $stream = $entry.Open()
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = [Convert]::ToHexString($sha.ComputeHash($stream)) } finally { $sha.Dispose(); $stream.Dispose() }
    if ($hash -cne $apkSha) { throw 'Baseline APK hash changed.' }
    $directory = Join-Path (Split-Path -Parent $PSScriptRoot) 'artifacts/device-release'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $output = Join-Path $directory 'baseline-0.7.2-unsigned.apk'
    if (Test-Path -LiteralPath $output) { throw 'Baseline destination already exists; refusing to overwrite.' }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $output, $false)
    [ordered]@{ sourceCommit = '1176e599e54d6d1c6e8ab7be1b59c684f3520b09'; artifact = 13446823155; archiveSha256 = $archiveSha; unsignedApkSha256 = $apkSha } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'upgrade-baseline.json') -Encoding utf8
    Write-Output 'Verified the pinned 0.7.2 baseline archive and unsigned APK.'
} finally {
    if ($zip) { $zip.Dispose() }
    if ($response) { $response.RawContentStream.Dispose() }
    $headers.Clear()
}
