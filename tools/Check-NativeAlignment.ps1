param(
    [Parameter(Mandatory = $true)] [string] $LibraryDirectory
)
$ErrorActionPreference = 'Stop'
$taskFailures = @()
Get-ChildItem -LiteralPath $LibraryDirectory -Recurse -Filter '*.so' | ForEach-Object {
    $taskBytes = [IO.File]::ReadAllBytes($_.FullName)
    if ($taskBytes[4] -ne 2) { return }
    $taskOffset = [BitConverter]::ToInt64($taskBytes, 32)
    $taskEntrySize = [BitConverter]::ToUInt16($taskBytes, 54)
    $taskEntryCount = [BitConverter]::ToUInt16($taskBytes, 56)
    $taskLoadAligned = $true
    $taskRelroAligned = $true
    $taskLoads = @()
    $taskRelroSegments = @()
    for ($taskIndex = 0; $taskIndex -lt $taskEntryCount; $taskIndex++) {
        $taskAt = [int]($taskOffset + $taskIndex * $taskEntrySize)
        $taskType = [BitConverter]::ToUInt32($taskBytes, $taskAt)
        if ($taskType -eq 1) {
            $taskLoadAligned = $taskLoadAligned -and ([BitConverter]::ToUInt64($taskBytes, $taskAt + 48) -ge 16384)
            $taskLoads += [pscustomobject]@{ Address = [BitConverter]::ToUInt64($taskBytes, $taskAt + 16); Size = [BitConverter]::ToUInt64($taskBytes, $taskAt + 40); Flags = [BitConverter]::ToUInt32($taskBytes, $taskAt + 4) }
        }
        if ($taskType -eq 1685382482) {
            $taskRelroSegments += [pscustomobject]@{ Address = [BitConverter]::ToUInt64($taskBytes, $taskAt + 16); Size = [BitConverter]::ToUInt64($taskBytes, $taskAt + 40) }
        }
    }
    foreach ($taskSegment in $taskRelroSegments) {
        $taskEnd = $taskSegment.Address + $taskSegment.Size
        $taskRoundedEnd = [uint64]([math]::Ceiling($taskEnd / 16384.0) * 16384)
        $taskOwnLoad = @($taskLoads | Where-Object { $_.Address -eq $taskSegment.Address -and $_.Size -eq $taskSegment.Size }).Count -gt 0
        $taskOverlappingWritable = @($taskLoads | Where-Object { ($_.Flags -band 2) -ne 0 -and $_.Address -ge $taskEnd -and $_.Address -lt $taskRoundedEnd }).Count -gt 0
        # Android's official guidance permits RELRO in an isolated LOAD segment even if its end is not page-aligned.
        $taskRelroAligned = $taskRelroAligned -and ((($taskEnd % 16384) -eq 0) -or ($taskOwnLoad -and -not $taskOverlappingWritable))
    }
    [pscustomobject]@{ Library = $_.FullName; LoadAligned = $taskLoadAligned; RelroAligned = $taskRelroAligned }
    if (-not $taskLoadAligned -or -not $taskRelroAligned) { $taskFailures += $_.FullName }
}
if ($taskFailures.Count -gt 0) {
    throw "Native 16 KB compatibility checks failed for $($taskFailures.Count) libraries. Update/rebuild dependencies; do not patch binary headers."
}
