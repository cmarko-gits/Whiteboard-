# Only this immutable, already qualified candidate may be used by the additional matrix.
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or -not $IsLinux -or -not $env:GITHUB_TOKEN) {
    throw 'This download is restricted to the read-only Linux qualification job.'
}
$source = '36cd15a6415d66eff7be10c97802ddc61f522f59'
if ((git rev-parse HEAD).Trim() -cne $source) { throw 'Not the pinned qualified candidate checkout.' }
$directory = Join-Path $PWD 'artifacts/qa-extra'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$headers = @{ 'User-Agent' = 'Whiteboard-qualification'; Authorization = "Bearer $env:GITHUB_TOKEN" }
$client = $null
$zip = $null
$memory = $null
try {
    $url = 'https://api.github.com/repos/cmarko-gits/Whiteboard-/actions/artifacts/13850827469'
    $metadata = Invoke-RestMethod -Uri $url -Headers $headers
    if ($metadata.expired -or $metadata.size_in_bytes -gt 33554432 -or $metadata.workflow_run.head_sha -cne $source) {
        throw 'The pinned artifact is expired, oversized or from another source. No latest fallback is allowed.'
    }
    $client = [Net.Http.HttpClient]::new()
    $client.DefaultRequestHeaders.Add('User-Agent', 'Whiteboard-qualification')
    $client.DefaultRequestHeaders.Add('Authorization', $headers.Authorization)
    $bytes = $client.GetByteArrayAsync("$url/zip").GetAwaiter().GetResult()
    $archiveHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
    if ($bytes.Length -gt 33554432 -or $archiveHash -cne '76FEA8F035A772B50C4CE9462E9325A6DCEA759A58F2FC66A803F391C6E3C200') {
        throw 'Pinned artifact archive digest mismatch.'
    }
    $memory = [IO.MemoryStream]::new($bytes, $false)
    $zip = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Read)
    $entries = @($zip.Entries | Where-Object FullName -ceq 'artifacts/device-release/whiteboard-ci-preview.apk')
    if ($entries.Count -ne 1 -or $entries[0].Length -gt 8388608) { throw 'Missing unique bounded preview entry.' }
    $stream = $entries[0].Open()
    $payload = [IO.MemoryStream]::new()
    try { $stream.CopyTo($payload); $apk = $payload.ToArray() } finally { $stream.Dispose(); $payload.Dispose() }
    $apkHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($apk))
    if ($apkHash -cne '7989B3779C32EDDD5E15B0A82A3D60EE9ADE3CCC90D9FAE180E76DD50CCD0F9C') {
        throw 'Qualified APK digest mismatch.'
    }
    [IO.File]::WriteAllBytes((Join-Path $directory 'qualified-preview.apk'), $apk)
    [ordered]@{
        sourceCommit = $source; originalRunId = 43008205011; artifactId = 13850827469
        archiveSha256 = $archiveHash; apkSha256 = $apkHash; entry = $entries[0].FullName
        bytes = $apk.Length; downloadedAtUtc = [datetime]::UtcNow.ToString('o')
        scope = 'Original qualified R8 binary, ephemeral CI development certificate; no owner key or new distribution package.'
    } | ConvertTo-Json | Set-Content (Join-Path $directory 'qualified-preview-provenance.json')
} finally {
    if ($zip) { $zip.Dispose() }; if ($memory) { $memory.Dispose() }; if ($client) { $client.Dispose() }
    $headers.Clear()
}
