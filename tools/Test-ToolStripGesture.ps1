$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath unused -LoadHelpersOnly -ArtifactsDirectory (Join-Path $PWD 'artifacts/tool-strip-host')
$script:commands = [Collections.Generic.List[object]]::new()
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $script:commands.Add($Arguments)
}
foreach ($viewport in @(@(0, 100, 320, 640), @(0, 100, 1080, 1800), @(0, 80, 2560, 1600), @(120, 60, 1200, 1900))) {
    $before = $script:commands.Count
    Scroll-ToolStripLeft -Canvas $viewport -Y 550
    if ($script:commands.Count -ne $before + 1) { throw 'Expected exactly one toolbar scroll gesture.' }
    $command = $script:commands[$before]
    if (($command[0..2] -join ' ') -cne 'shell input swipe' -or $command.Count -ne 8) { throw 'Unexpected gesture command.' }
    $start = [int]$command[3]
    $end = [int]$command[5]
    $width = $viewport[2] - $viewport[0]
    if ($start -le $end -or $start -gt $viewport[2] - $width * 0.15 -or $end -lt $viewport[0] + $width * 0.15) {
        throw 'The toolbar gesture can enter a system edge-navigation region.'
    }
    if ($command[4] -ne '550' -or $command[6] -ne '550') { throw 'Toolbar scroll left its own row.' }
}
$before = $script:commands.Count
$rejected = $false
try { Scroll-ToolStripLeft -Canvas @(100, 0, 50, 100) -Y 50 } catch { $rejected = $true }
if (-not $rejected -or $script:commands.Count -ne $before) { throw 'Invalid viewport issued device input.' }
Write-Output 'PASS: phone/tablet/offset tool-strip swipes stay inside the viewport; invalid geometry produces no input.'
