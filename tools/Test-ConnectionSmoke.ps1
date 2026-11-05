$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath unused -LoadHelpersOnly
$script:frames = [Collections.Generic.Queue[string]]::new()
$script:commands = [Collections.Generic.List[object]]::new()
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)
    $script:commands.Add($Arguments)
}
function Find-Ui {
    param([string] $Description)
    if ($Description -eq 'Ellipse') { return [PSCustomObject]@{ bounds = '[800,200][1000,400]' } }
    if ($Description -eq 'Drawing canvas') { return [PSCustomObject]@{ bounds = '[0,100][1080,1800]' } }
    throw "Unexpected lookup: $Description"
}
function Start-Sleep { param([int] $Milliseconds) }
function Get-ConnectionFrames { return $script:frames.Dequeue() }
$before = '800,200,1000,400:400,250,800,500'
$targetOnly = '692,200,892,400:400,250,800,500'
$after = '692,200,892,400:400,250,692,500'
@($before, $before, $targetOnly, $after) | ForEach-Object { $script:frames.Enqueue($_) }
$movement = Move-ConnectionTarget
if ($movement.Before -cne $before -or $movement.After -cne $after -or $script:frames.Count -ne 0) {
    throw 'Connection movement must wait until both target and connection frames change.'
}
if ($script:commands.Count -ne 1 -or ($script:commands[0] -join '|') -cne 'shell|input|swipe|900|300|792|300|700') {
    throw 'Connection smoke did not issue exactly one bounded drag from the fresh target center.'
}
@($after, $targetOnly, $before) | ForEach-Object { $script:frames.Enqueue($_) }
Assert-ConnectionFrames $before
if ($script:frames.Count -ne 0 -or $script:commands.Count -ne 1) { throw 'Restoration check must observe both frames without another edit.' }
Write-Output 'PASS: connection smoke waits for both moved/restored frames and issues exactly one drag; no device commands executed.'
