$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath unused -LoadHelpersOnly
. (Join-Path $PSScriptRoot 'MindMap-Smoke.ps1')
$script:mapFrames = [Collections.Generic.Queue[string]]::new()
$script:mapCommands = [Collections.Generic.List[object]]::new()
function Get-Ui {
    if ($script:mapFrames.Count -eq 0) { throw 'Unexpected additional hierarchy read.' }
    [xml] $taskFixture = $script:mapFrames.Dequeue()
    return $taskFixture.SelectNodes('//node')
}
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)
    $script:mapCommands.Add($Arguments)
}
function Start-Sleep { param([int] $Milliseconds) }
$moving = '<hierarchy><node clickable="true" enabled="true" bounds="[0,100][200,148]"><node text="Add child" bounds="[10,112][100,136]"/></node></hierarchy>'
$settled = '<hierarchy><node clickable="true" enabled="true" bounds="[0,148][200,196]"><node text="Add child" bounds="[10,160][100,184]"/></node></hierarchy>'
@($moving, $settled, $settled) | ForEach-Object { $script:mapFrames.Enqueue($_) }
Tap-MindMapAction 'Add child'
if ($script:mapFrames.Count -ne 0 -or $script:mapCommands.Count -ne 1 -or
    ($script:mapCommands[0] -join '|') -cne 'shell|input|tap|100|172') {
    throw 'Mind-map action must settle before exactly one tap at its fresh clickable owner.'
}
$script:mapCommands.Clear()
$script:mapFrames.Enqueue('<hierarchy><node clickable="true" enabled="true" text="Add child" bounds="[0,0][100,48]"/><node clickable="true" enabled="true" text="Add child" bounds="[0,48][100,96]"/></hierarchy>')
$rejected = $false
try { Tap-MindMapAction 'Add child' } catch {
    if ($_.Exception.Message -cne 'Ambiguous mind-map action: Add child') { throw }
    $rejected = $true
}
if (!$rejected -or $script:mapCommands.Count -ne 0) { throw 'Ambiguous map controls must not be tapped.' }

# Exercise the actual nested input driver without running the installed-package flow.
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'MindMap-Smoke.ps1'), [ref] $tokens, [ref] $errors)
if ($errors.Count) { throw 'Mind-map smoke did not parse.' }
$enter = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Enter-Title' }, $true)
if (!$enter) { throw 'Missing actual title input driver.' }
$script:mapField = [PSCustomObject]@{ text = 'existing root'; 'resource-id' = 'mind_map_node_title'; focused = 'true'; bounds = '[0,0][200,64]' }
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
    @($script:mapCommands | Where-Object { $_ -contains 'text' }).Count -ne 0) {
    throw 'Unfocused field must not receive text or repeated clicks.'
}
Write-Output 'PASS: moving map actions settle before one fresh-owner tap; ambiguous controls, existing text and missing focus are rejected before typing. No device commands executed.'
