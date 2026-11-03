$ErrorActionPreference='Stop'
$script:adbInvocations=[Collections.Generic.List[object]]::new()
function Invoke-CiAdbProbe {
    $script:adbInvocations.Add([string[]]$args)
    $global:LASTEXITCODE=0
}
# Load command construction only. The fake command cannot access a device, SDK or AVD.
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath Invoke-CiAdbProbe -DeviceSerial argument-probe -Package dev.whiteboard.argumentprobe -LoadHelpersOnly
$tokens=$null
$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Test-AppLanguage.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Language smoke script did not parse'}
$restart=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Restart-LanguageApp'},$true)
if(!$restart){throw 'Missing production language restart function'}
& $restart.Body.GetScriptBlock()
if($script:adbInvocations.Count -ne 2){throw 'Expected exactly one force-stop and one launch command'}
$stop=$script:adbInvocations[0] -join '|'
$start=$script:adbInvocations[1] -join '|'
if($stop -cne '-s|argument-probe|shell|am|force-stop|dev.whiteboard.argumentprobe'){
    throw 'Force-stop arguments changed'
}
if($start -cne '-s|argument-probe|shell|am|start|-W|-n|dev.whiteboard.argumentprobe/dev.whiteboard.app.MainActivity'){
    throw 'Launch flags were consumed by PowerShell parameter binding'
}
Write-Output 'PASS: actual release restart function preserves -W/-n as adb arguments; no device commands executed.'
