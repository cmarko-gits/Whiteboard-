param(
    [string] $DependencyReport = '',
    [string] $RuntimeInventory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'artifacts/security/runtime-dependencies.tsv'),
    [string] $OutputPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'artifacts/dependency-licenses.json'),
    [switch] $FetchMissing
)
$ErrorActionPreference = 'Stop'
$taskGradleHome = if ($env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME
} else {
    Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle'
}
$taskCache = Join-Path $taskGradleHome 'caches/modules-2/files-2.1'
$taskPomDirectory = Join-Path (Split-Path -Parent $OutputPath) 'license-poms'
New-Item -ItemType Directory -Path $taskPomDirectory -Force | Out-Null

function Read-Pom {
    param([string] $Group, [string] $Artifact, [string] $Version)
    $taskPath = Join-Path $taskCache "$Group/$Artifact/$Version"
    $taskFile = if (Test-Path -LiteralPath $taskPath) {
        Get-ChildItem -LiteralPath $taskPath -Recurse -Filter '*.pom' | Select-Object -First 1
    } else { $null }
    if (-not $taskFile) {
        $taskDownloaded = Join-Path $taskPomDirectory "$Group-$Artifact-$Version.pom"
        if (-not (Test-Path -LiteralPath $taskDownloaded) -and $FetchMissing) {
            $taskRepository = if ($Group.StartsWith('androidx.')) { 'https://dl.google.com/dl/android/maven2' } else { 'https://repo.maven.apache.org/maven2' }
            $taskRelative = $Group.Replace('.', '/') + "/$Artifact/$Version/$Artifact-$Version.pom"
            try { Invoke-WebRequest -Uri "$taskRepository/$taskRelative" -OutFile $taskDownloaded } catch { return $null }
        }
        if (-not (Test-Path -LiteralPath $taskDownloaded)) { return $null }
        $taskFile = Get-Item -LiteralPath $taskDownloaded
    }
    $taskSettings = [Xml.XmlReaderSettings]::new()
    $taskSettings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
    $taskSettings.XmlResolver = $null
    $taskReader = [Xml.XmlReader]::Create($taskFile.FullName, $taskSettings)
    try {
        $taskDocument = [Xml.XmlDocument]::new()
        $taskDocument.XmlResolver = $null
        $taskDocument.Load($taskReader)
        return $taskDocument
    } finally { $taskReader.Dispose() }
}

$taskCoordinates = @{}
$taskCoordinateLines = if ($DependencyReport) { Get-Content -LiteralPath $DependencyReport } else {
    Import-Csv -LiteralPath $RuntimeInventory -Delimiter "`t" | ForEach-Object { "$($_.package):$($_.version)" }
}
$taskCoordinateLines | ForEach-Object {
    if ($_ -match '([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)(?: -> ([A-Za-z0-9_.-]+))?') {
        $taskGroup = $Matches[1]
        $taskArtifact = $Matches[2]
        $taskVersion = if ($Matches[4]) { $Matches[4] } else { $Matches[3] }
        $taskCoordinates["${taskGroup}:${taskArtifact}:${taskVersion}"] = @($taskGroup, $taskArtifact, $taskVersion)
    }
}
if ($taskCoordinates.Count -eq 0) { throw 'No resolved runtime components available for license inventory.' }
$taskItems = foreach ($taskCoordinate in ($taskCoordinates.Keys | Sort-Object)) {
    $taskParts = $taskCoordinates[$taskCoordinate]
    $taskPom = Read-Pom $taskParts[0] $taskParts[1] $taskParts[2]
    $taskProjectUrl = if ($taskPom) { [string]$taskPom.project.url } else { '' }
    for ($taskDepth = 0; $taskDepth -lt 8 -and $taskPom -and -not $taskPom.project.licenses.license; $taskDepth++) {
        $taskParent = $taskPom.project.parent
        if (-not $taskParent) { break }
        $taskPom = Read-Pom ([string]$taskParent.groupId) ([string]$taskParent.artifactId) ([string]$taskParent.version)
    }
    $taskLicenses = if ($taskPom) {
        @($taskPom.project.licenses.license | ForEach-Object { [pscustomobject]@{ Name = [string]$_.name; Url = [string]$_.url } })
    } else { @() }
    [pscustomobject]@{ Coordinate = $taskCoordinate; ProjectUrl = $taskProjectUrl; Licenses = @($taskLicenses) }
}
$taskItems | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputPath -Encoding utf8
$taskMissing = @($taskItems | Where-Object { $_.Licenses.Count -eq 0 -or -not $_.Licenses[0].Name })
Write-Output "Runtime graph components: $($taskItems.Count); missing license metadata: $($taskMissing.Count)."
if ($taskMissing.Count) { $taskMissing.Coordinate | Write-Output }
