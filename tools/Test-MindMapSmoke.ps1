$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath unused -Package dev.whiteboard.app -LoadHelpersOnly
. (Join-Path $PSScriptRoot 'MindMap-Smoke.ps1')
$script:mapCommands = [Collections.Generic.List[object]]::new()
$script:mapReplyPackage = 'dev.whiteboard.app'
function Get-Ui { throw 'Semantic activation must not use cached screen coordinates.' }
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)
    $script:mapCommands.Add($Arguments)
    $taskEncoded = $Arguments[[Array]::IndexOf($Arguments, 'wb_label64') + 1]
    return @("INSTRUMENTATION_RESULT: whiteboard_clicked64=$taskEncoded",
        "INSTRUMENTATION_RESULT: whiteboard_package=$script:mapReplyPackage", 'INSTRUMENTATION_CODE: -1')
}
$label = 'Add child; $(not a command)'
Tap-MindMapAction $label
$expected = @('shell', 'am', 'instrument', '-w', '-r', '-e', 'wb_package', 'dev.whiteboard.app',
    '-e', 'wb_mode', 'text', '-e', 'wb_label64', [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($label)),
    'dev.whiteboard.app.debug.test/dev.whiteboard.app.ReleaseUiBridgeRunner')
if ($script:mapCommands.Count -ne 1 -or ($script:mapCommands[0] -join '|') -cne ($expected -join '|')) {
    throw 'UI client must preserve flags, target, encoded label and component in exactly one invocation.'
}
$script:mapCommands.Clear()
Tap-MindMapAction -Description 'More actions'
if ($script:mapCommands.Count -ne 1 -or $script:mapCommands[0][10] -cne 'description') {
    throw 'Description activation must use the connected description mode.'
}
$script:mapCommands.Clear()
$script:mapReplyPackage = 'dev.whiteboard.other'
$rejected = $false
try { Tap-MindMapAction 'Add child' } catch {
    if ($_.Exception.Message -notlike 'Connected UI action failed:*') { throw }
    $rejected = $true
}
if (!$rejected -or $script:mapCommands.Count -ne 1) { throw 'Wrong-package result must fail without replaying the action.' }
$script:mapCommands.Clear()
$script:mapReplyPackage = 'dev.whiteboard.app'

# Exercise the actual nested input driver without running the installed-package flow.
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'MindMap-Smoke.ps1'), [ref] $tokens, [ref] $errors)
if ($errors.Count) { throw 'Mind-map smoke did not parse.' }
$enter = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Enter-Title' }, $true)
if (!$enter) { throw 'Missing actual title input driver.' }
$script:mapField = [PSCustomObject]@{ text = 'existing root'; 'resource-id' = 'mind_map_node_title'; focused = 'true' }
function Find-Ui { param([string] $Text, [string] $Description, [string] $Class) return $script:mapField }
function Wait-Keyboard { param([bool] $Shown = $true) }
$rejected = $false
try { & $enter.Body.GetScriptBlock() -Caption 'new child' } catch {
    if ($_.Exception.Message -notlike 'Expected an empty create/child/sibling title field*') { throw }
    $rejected = $true
}
if (!$rejected -or $script:mapCommands.Count -ne 0) { throw 'Existing node text must not receive any driver input.' }
$script:mapField.text = ''
$script:mapField.focused = 'false'
$rejected = $false
try { & $enter.Body.GetScriptBlock() -Caption 'new child' } catch {
    if ($_.Exception.Message -cne 'The empty mind-map title field did not retain input focus.') { throw }
    $rejected = $true
}
if (!$rejected -or $script:mapCommands.Count -ne 1 -or
    @($script:mapCommands | Where-Object { $_ -contains 'input' }).Count -ne 0) {
    throw 'Unfocused field must not receive text or repeated actions.'
}
Write-Output 'PASS: connected UI protocol preserves encoded labels/flags, rejects a mismatched package without replay, and rejects existing/unfocused title input. No device commands executed.'
