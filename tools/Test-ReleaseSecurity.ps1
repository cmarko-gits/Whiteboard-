param(
    [Parameter(Mandatory = $true)] [string] $ApkPath,
    [Parameter(Mandatory = $true)] [string] $Aapt2Path
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskDirectory = Join-Path $taskRoot 'artifacts/security'
New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null
function Read-ApkXml([string] $File) {
    $taskLines = @(& $Aapt2Path dump xmltree $ApkPath --file $File)
    if ($LASTEXITCODE -ne 0 -or $taskLines.Count -eq 0) { throw "Cannot inspect APK XML: $File" }
    $taskStack = [Collections.Generic.List[object]]::new()
    $taskNodes = [Collections.Generic.List[object]]::new()
    foreach ($taskLine in $taskLines) {
        if ($taskLine -match '^(\s*)E: ([\w-]+)') {
            $taskDepth = $Matches[1].Length
            while ($taskStack.Count -gt 0 -and $taskStack[$taskStack.Count - 1].Depth -ge $taskDepth) {
                $taskStack.RemoveAt($taskStack.Count - 1)
            }
            $taskNode = [pscustomobject] @{ Name = $Matches[2]; Depth = $taskDepth; Attributes = @{} }
            $taskNodes.Add($taskNode)
            $taskStack.Add($taskNode)
        } elseif ($taskStack.Count -gt 0 -and $taskLine -match '^\s*A: (?:http://schemas.android.com/apk/res/android:)?([\w-]+)(?:\([^)]*\))?=(.+)$') {
            $taskKey = $Matches[1]
            $taskValue = ($Matches[2] -split ' \(Raw: ', 2)[0].Trim('"')
            $taskStack[$taskStack.Count - 1].Attributes[$taskKey] = $taskValue
        }
    }
    return $taskNodes.ToArray()
}
$taskManifest = @(Read-ApkXml 'AndroidManifest.xml')
$taskApp = @($taskManifest | Where-Object Name -eq 'application')
if ($taskApp.Count -ne 1) { throw 'Expected one application.' }
$taskAttributes = $taskApp[0].Attributes
if (@($taskManifest | Where-Object {
    $_.Name -eq 'meta-data' -and $_.Attributes.name -eq 'androidx.emoji2.text.EmojiCompatInitializer'
}).Count) { throw 'Automatic downloadable emoji font initialization is outside the offline release policy.' }
if ($taskAttributes.debuggable -eq 'true' -or $taskAttributes.testOnly -eq 'true' -or
    $taskAttributes.allowBackup -ne 'false' -or $taskAttributes.fullBackupContent -ne 'false' -or
    $taskAttributes.usesCleartextTraffic -ne 'false' -or $taskAttributes.ContainsKey('networkSecurityConfig')) {
    throw 'Release backup/debug/cleartext policy failed.'
}
if (($taskManifest | Where-Object Name -eq 'manifest').Attributes.package -ne 'dev.whiteboard.app') { throw 'Wrong release application ID.' }
$taskAllowedPermissions = @('dev.whiteboard.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION')
foreach ($taskNode in $taskManifest | Where-Object Name -eq 'uses-permission') {
    if ($taskNode.Attributes.name -notin $taskAllowedPermissions) { throw 'Unexpected permission requires review.' }
}
$taskProtected = @{
    'androidx.profileinstaller.ProfileInstallReceiver' = 'android.permission.DUMP'
}
foreach ($taskNode in $taskManifest | Where-Object { $_.Name -in @('activity', 'activity-alias', 'service', 'receiver', 'provider') }) {
    $taskName = $taskNode.Attributes.name
    if ($taskName -eq 'dev.whiteboard.app.MainActivity') { continue }
    if ($taskNode.Attributes.exported -eq 'false') { continue }
    if ($taskNode.Attributes.exported -eq 'true' -and $taskProtected.ContainsKey($taskName) -and
        $taskNode.Attributes.permission -eq $taskProtected[$taskName]) { continue }
    throw "Unexpected accessible component: $taskName"
}
$taskResources = (& $Aapt2Path dump resources $ApkPath) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK resource table.' }
function Find-XmlResource([string] $Reference) {
    if ($Reference -notmatch '^@0x[0-9a-fA-F]+$') { throw 'Expected an XML resource reference.' }
    $taskId = [Regex]::Escape($Reference.TrimStart('@'))
    $taskMatch = [Regex]::Match($taskResources, "(?m)^\s*resource $taskId xml/[^\r\n]+\r?\n\s*\(\) \(file\) ([^\s]+) type=XML")
    if (-not $taskMatch.Success) { throw 'Cannot resolve packaged XML resource.' }
    return $taskMatch.Groups[1].Value
}
$taskFileProviderMetadata = @($taskManifest | Where-Object { $_.Name -eq 'meta-data' -and $_.Attributes.name -eq 'android.support.FILE_PROVIDER_PATHS' })
if ($taskFileProviderMetadata.Count -ne 1) { throw 'Expected one scoped FileProvider configuration.' }
$taskPaths = @(Read-ApkXml (Find-XmlResource $taskFileProviderMetadata[0].Attributes.resource))
if (@($taskPaths | Where-Object { $_.Name -ne 'paths' -and
    ($_.Name -ne 'cache-path' -or $_.Attributes.path -notin @('exports/', 'camera/')) }).Count -gt 0) {
    throw 'FileProvider path scope is too broad.'
}
$taskBackup = @(Read-ApkXml (Find-XmlResource $taskAttributes.dataExtractionRules))
if (@($taskBackup | Where-Object Name -eq 'include').Count -gt 0 -or
    @($taskBackup | Where-Object { $_.Name -eq 'exclude' -and $_.Attributes.path -eq '.' }).Count -ne 8) {
    throw 'Data-extraction exclusion rules changed; review required.'
}
$taskArchive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ApkPath).Path)
try {
    foreach ($taskEntry in $taskArchive.Entries) {
        if ($taskEntry.FullName -match '(?i)(\.keystore$|\.jks$|(^|/)\.env($|\.)|dev-mail|debug_network_security)') { throw 'Development/credential artifact in release.' }
        if ($taskEntry.FullName -match '^classes\d*\.dex$') {
            $taskStream = $taskEntry.Open()
            $taskMemory = [IO.MemoryStream]::new()
            try {
                $taskStream.CopyTo($taskMemory)
                if ([Text.Encoding]::ASCII.GetString($taskMemory.ToArray()) -match 'Lleakcanary/|Lshark/|Lcom/squareup/leakcanary/') {
                    throw 'Debug memory-analysis classes present in release.'
                }
                if ([Text.Encoding]::ASCII.GetString($taskMemory.ToArray()) -match 'Lokhttp3/|Lio/ktor/|Landroidx/work/|Ldev/whiteboard/data/cloud/RemoteAccounts;|Ldev/whiteboard/data/cloud/RemoteBoards;') {
                    throw 'Online client or background scheduling classes present in local release.'
                }
            } finally { $taskStream.Dispose(); $taskMemory.Dispose() }
        }
    }
} finally { $taskArchive.Dispose() }
[ordered] @{
    checkedAtUtc = [DateTime]::UtcNow.ToString('o')
    apkSha256 = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash
    status = 'passed'
    checks = @('offline permission/component allowlist', 'backup exclusions', 'cleartext disabled', 'scoped FileProvider', 'no online clients or WorkManager', 'no debug leak-analysis classes or credential files')
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $taskDirectory 'release-policy.json') -Encoding utf8
Write-Output 'Release APK security policy passed.'
