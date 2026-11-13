$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath unused -LoadHelpersOnly -ArtifactsDirectory (Join-Path $PWD 'artifacts/board-input-host')
$script:events = [Collections.Generic.List[string]]::new()
$script:focused = 'true'
$script:value = ''
$script:accept = $true
function Find-Ui {
    param([string]$Class, [string]$Text)
    if ($Text) {
        $script:events.Add('check-complete-name')
        if (-not $script:accept) { throw 'Complete requested name was not retained.' }
    }
    return [pscustomobject]@{ class = $Class; text = ''; focused = 'true'; enabled = 'true'; bounds = '[100,100][500,200]' }
}
function Tap-Ui { param($Node); $script:events.Add('focus') }
function Wait-Keyboard { $script:events.Add('keyboard-ready') }
function Get-Ui { return [pscustomobject]@{ class = 'android.widget.EditText'; text = $script:value; focused = $script:focused; enabled = 'true' } }
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    if (($Arguments -join ' ') -cne 'shell input text Recovery_check_123') { throw 'Unexpected board keyboard command.' }
    $script:events.Add('type-once')
}
Enter-SmokeBoardName -Name Recovery_check_123
if (($script:events -join ',') -cne 'focus,keyboard-ready,type-once,check-complete-name') { throw 'Typing did not wait for the keyboard or complete-name verification.' }
foreach ($case in @(@{ Focus = 'false'; Text = '' }, @{ Focus = 'true'; Text = 'Existing title' })) {
    $script:events.Clear(); $script:focused = $case.Focus; $script:value = $case.Text
    $rejected = $false
    try { Enter-SmokeBoardName -Name Recovery_check_123 } catch { $rejected = $true }
    if (-not $rejected -or 'type-once' -in $script:events) { throw 'Unfocused/nonempty field received text.' }
}
$script:events.Clear(); $script:focused = 'true'; $script:value = ''; $script:accept = $false
$rejected = $false
try { Enter-SmokeBoardName -Name Recovery_check_123 } catch { $rejected = $true }
if (-not $rejected -or @($script:events | Where-Object { $_ -eq 'type-once' }).Count -ne 1) { throw 'Truncated text was accepted or keyboard input replayed.' }
Write-Output 'PASS: focus/IME before typing, exact complete name, no text on invalid fields and no replay after truncated input.'
