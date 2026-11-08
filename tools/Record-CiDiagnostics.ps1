param([string]$Directory='artifacts/device-release')
$ErrorActionPreference='Stop'
if($env:GITHUB_ACTIONS -cne 'true' -or $env:GITHUB_SHA -cnotmatch '^[0-9a-f]{40}$'){throw 'CI source provenance is required.'}
function Read-BoundedJson([string]$Name){
    $path=Join-Path $Directory $Name
    if((Get-Item -LiteralPath $path).Length -gt 524288){throw 'Oversized diagnostic JSON.'}
    return Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
}
$memory=Read-BoundedJson 'qualification-memory.json'
$frames=Read-BoundedJson 'qualification-frames.json'
if($memory.api -ne 36 -or $memory.cycles -ne 6 -or $memory.watchedEditorViews -ne 12 -or
    $memory.actualEditorUi -ne $true -or $memory.leakCanaryAssertion -cne 'passed' -or
    $memory.remainingCanvasInstances -ne 0 -or $memory.pssKbPerCycle.Count -ne 6 -or
    $memory.heapDumpBytes -le 0 -or $memory.heapDumpBytes -gt 134217728){throw 'Incomplete or failed editor memory qualification.'}
foreach($value in @($memory.pssKbPerCycle)+@($memory.pssKbAfterGc)){
    if($value -isnot [ValueType] -or ![double]::IsFinite([double]$value) -or $value -le 0){throw 'Invalid memory observation.'}
}
if($frames.api -ne 36 -or $frames.frames -lt 20 -or $frames.objects -ne 1000 -or $frames.points -ne 100000){throw 'Incomplete Window frame workload.'}
foreach($value in @($frames.p50Ms,$frames.p95Ms,$frames.p99Ms)){
    if($value -isnot [ValueType] -or ![double]::IsFinite([double]$value) -or $value -lt 0){throw 'Invalid Window frame observation.'}
}
if($frames.p50Ms -gt $frames.p95Ms -or $frames.p95Ms -gt $frames.p99Ms){throw 'Frame percentile order is invalid.'}
$trace=Join-Path $Directory 'qualification-cpu.trace'
if((Get-Item -LiteralPath $trace).Length -le 0 -or (Get-Item -LiteralPath $trace).Length -gt 16777216){throw 'Missing or oversized sampled trace.'}
$instrumentation=Join-Path $Directory 'diagnostic-instrumentation.txt'
if((Get-Item -LiteralPath $instrumentation).Length -gt 524288 -or
    (Get-Content -LiteralPath $instrumentation -Raw) -notmatch 'OK \(2 tests\)'){throw 'Two passing diagnostic instrumentation cases are required.'}
$app='app/build/outputs/apk/debug/app-debug.apk'
$tests='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
[ordered]@{
    schema=1;source=$env:GITHUB_SHA;api=36;status='passed-diagnostic-emulator-scope'
    checkedAtUtc=[DateTime]::UtcNow.ToString('o');cases=2
    diagnosticAppSha256=(Get-FileHash -LiteralPath $app -Algorithm SHA256).Hash
    diagnosticTestsSha256=(Get-FileHash -LiteralPath $tests -Algorithm SHA256).Hash
    traceSha256=(Get-FileHash -LiteralPath $trace -Algorithm SHA256).Hash
    memory=$memory;frames=$frames
    scope='Debug with LeakCanary/Shark and sampled tracing; exact source, different binary from release. Six media/editor recreation cycles; PSS observations are not peak memory. No physical GPU, latency, thermal or universal leak guarantee.'
}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $Directory 'diagnostic-qualification.json') -Encoding utf8
Write-Output 'PASS: diagnostic editor collection and actual Window frame observations; no hardware FPS claim.'
