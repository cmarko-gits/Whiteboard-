$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath unused-host-probe -Package dev.whiteboard.app -LoadHelpersOnly
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Smoke-Test.ps1'), [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Smoke script syntax failed.' }
$clause = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.CatchClauseAst] -and $node.Body.Extent.Text -match '\$taskOriginalError = \$_'
}, $true)
if (-not $clause) { throw 'Missing actual smoke failure handler.' }
$handler = [scriptblock]::Create(($clause.Body.Statements.Extent.Text -join "`n"))
$taskArtifacts = Join-Path $PWD ('artifacts/apply-probe-host-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskArtifacts | Out-Null
$DiagnoseFailedApply = $true
function Capture { param([string]$Name) }
function Get-Ui { return $script:probeDocument.SelectNodes('//node') }
function Find-Ui { param([string]$Description, [int]$TimeoutSeconds); return [pscustomobject]@{ 'content-desc' = $Description } }
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    if (($Arguments -join ' ') -ceq 'shell dumpsys input_method') { return 'mInputShown=true' }
    if (($Arguments -join ' ') -notmatch '^shell am instrument ') { throw 'Unexpected command in host probe.' }
    $script:actionCount++
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('Apply'))
    return "INSTRUMENTATION_STATUS: whiteboard_clicked64=$encoded`nINSTRUMENTATION_STATUS: whiteboard_package=dev.whiteboard.app`nOK (1 test)`nINSTRUMENTATION_CODE: -1"
}
foreach ($case in @(
    @{ Phase = 'color-landscape-apply'; Hex = '65B7F2'; Package = 'dev.whiteboard.app'; Expected = 1 },
    @{ Phase = 'new-board'; Hex = '65B7F2'; Package = 'dev.whiteboard.app'; Expected = 0 },
    @{ Phase = 'color-landscape-apply'; Hex = '123456'; Package = 'dev.whiteboard.app'; Expected = 0 },
    @{ Phase = 'color-landscape-apply'; Hex = '65B7F2'; Package = 'other.package'; Expected = 0 }
)) {
    $taskStage = $case.Phase
    $script:actionCount = 0
    [xml]$script:probeDocument = '<hierarchy><node package="' + $case.Package + '" class="android.widget.EditText" text="' + $case.Hex + '"/><node package="' + $case.Package + '" text="Apply" enabled="true"/></hierarchy>'
    $retained = $false
    try {
        try { throw 'original-physical-apply-sentinel' } catch { & $handler }
    } catch { $retained = $_.Exception.Message -ceq 'original-physical-apply-sentinel' }
    $report = Get-Content (Join-Path $taskArtifacts 'color-apply-probe.json') -Raw | ConvertFrom-Json
    if (-not $retained -or -not $report.originalFailureRetained -or $script:actionCount -ne $case.Expected) {
        throw 'Probe changed the original failure or sent an action outside its exact phase/package/HEX scope.'
    }
    if ($case.Expected -eq 1 -and $report.status -cne 'canvas-opened-after-connected-accessibility-action') { throw 'Successful diagnostic action was not recorded.' }
}
Write-Output 'PASS: actual failure handler preserves its original error after the probe; rejects wrong phase, HEX and package. No device commands.'
