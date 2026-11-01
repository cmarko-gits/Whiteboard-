param(
    [Parameter(Mandatory = $true)] [string] $AdbPath,
    [string] $DeviceSerial = 'emulator-5554',
    [string] $Package = 'dev.whiteboard.app'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath $AdbPath -DeviceSerial $DeviceSerial -Package $Package -LoadHelpersOnly
$taskLocaleText = (Invoke-Adb shell cmd locale get-app-locales $Package) -join "`n"
if ($taskLocaleText -notmatch 'are \[([^\]]*)\]') { throw 'Cannot preserve the current app locale.' }
$taskRestoreLocales = $Matches[1].Replace(' ', '')
$taskResults = [Collections.Generic.List[object]]::new()
function Set-TestLocale([string] $Locales) {
    if ($Locales) { Invoke-Adb shell cmd locale set-app-locales $Package --locales $Locales | Out-Null }
    else { Invoke-Adb shell cmd locale set-app-locales $Package | Out-Null }
}
try {
    $taskBackToLibrary = @(Get-Ui | Where-Object { $_.'content-desc' -eq 'Back to boards' })
    if ($taskBackToLibrary.Count) { Tap-Ui $taskBackToLibrary[0] }
    Find-Ui -Description 'New board' | Out-Null
    $taskFixtures = @(Get-Ui | Where-Object { $_.text -like 'Recovery_check_*' })
    if (-not $taskFixtures.Count) { throw 'Run Smoke-Test first; its saved board is required.' }
    $taskFixtureName = $taskFixtures[0].text
    Set-TestLocale 'sr-Latn'
    Find-Ui -Description 'Nova tabla' | Out-Null
    Find-Ui -Description 'Podešavanja' | Out-Null
    Capture 'locale-sr-library'
    Tap-Ui (Find-Ui -Text $taskFixtureName)
    Find-Ui -Description 'Nazad na table' | Out-Null
    Find-Ui -Description 'Dodatne radnje' | Out-Null
    Find-Ui -Text 'Sačuvano na uređaju' | Out-Null
    Capture 'locale-sr-editor'
    Tap-Ui (Find-Ui -Description 'Nazad na table')
    Find-Ui -Description 'Nova tabla' | Out-Null
    $taskResults.Add(@{ locale='sr-Latn'; status='passed'; coverage='Localized library/editor navigation, saved state and unchanged document name.' })
    # Arabic is a layout stress case with English resource fallback, not a claimed translation.
    Set-TestLocale 'ar'
    Find-Ui -Description 'New board' | Out-Null
    Capture 'locale-rtl-library'
    Tap-Ui (Find-Ui -Text $taskFixtureName)
    $taskBack = Get-Rect (Find-Ui -Description 'Back to boards')
    $taskMore = Get-Rect (Find-Ui -Description 'More actions')
    if ($taskBack[0] -le $taskMore[0]) { throw 'Expected mirrored RTL editor navigation.' }
    $taskActivity = (Invoke-Adb shell dumpsys activity "$Package/dev.whiteboard.app.MainActivity") -join "`n"
    $taskActivity | Set-Content -LiteralPath (Join-Path $taskArtifacts 'local-locale-configuration.txt') -Encoding utf8
    if ($taskActivity -notmatch 'mCurrentConfig=\{[^}]*\bldrtl\b') { throw 'The activity did not receive an RTL configuration.' }
    Capture 'locale-rtl-editor'
    Tap-Ui (Find-Ui -Description 'Back to boards')
    Find-Ui -Description 'New board' | Out-Null
    $taskResults.Add(@{ locale='ar'; status='passed'; coverage='Actual ldrtl configuration, English fallback and mirrored editor actions.' })
} catch {
    try { Capture 'locale-failure' } catch { }
    throw
} finally {
    Set-TestLocale $taskRestoreLocales
    [ordered]@{ checkedAtUtc=[DateTime]::UtcNow.ToString('o'); package=$Package; device=$DeviceSerial; cases=@($taskResults.ToArray()); restoredLocales=$taskRestoreLocales } | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $taskArtifacts 'local-locale-results.json') -Encoding utf8
}
Write-Output 'PASS: Serbian Latin UI and real RTL configuration with English fallback; original app locale restored.'
