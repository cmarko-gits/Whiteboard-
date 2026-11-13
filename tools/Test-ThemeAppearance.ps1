param([Parameter(Mandatory = $true)][string]$AdbPath)
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or -not $IsLinux) { throw 'Only the disposable Linux qualification device is supported.' }
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath $AdbPath -Package dev.whiteboard.app -LoadHelpersOnly
$taskArtifacts = Join-Path $PWD 'artifacts/qa-extra/themes'
New-Item -ItemType Directory -Path $taskArtifacts -Force | Out-Null
$taskUiPath = Join-Path $taskArtifacts 'current-ui.xml'
$originalFont = ((Invoke-Adb shell settings get system font_scale) -join '').Trim()
$originalNight = ((Invoke-Adb shell cmd uimode night) -join '').Trim()
if ($originalNight -notmatch '^Night mode: (auto|yes|no)$') { throw "Unrecognized original night mode: $originalNight" }
$restoreNight = $Matches[1]
function Open-Settings {
    Tap-Ui (Find-Ui -Description 'Settings' -ExactDescription)
    Find-ThemeSetting -Label 'Appearance' | Out-Null
}
function Find-ThemeSetting {
    param([string]$Label)
    for ($scroll = 0; $scroll -lt 12; $scroll++) {
        $nodes = @(Get-Ui)
        $match = @($nodes | Where-Object { $_.text -ceq $Label -and $_.enabled -eq 'true' })
        if ($match.Count -eq 1) { return $match[0] }
        $containers = @($nodes | Where-Object scrollable -eq 'true')
        if (-not $containers.Count) { break }
        $area = Get-Rect $containers[-1]
        $height = $area[3] - $area[1]
        $x = [int](($area[0] + $area[2]) / 2)
        Invoke-Adb shell input swipe $x ([int]($area[1] + $height * 0.8)) $x ([int]($area[1] + $height * 0.2)) 300 | Out-Null
    }
    throw "Appearance control is not reachable: $Label"
}
function Get-SelectedThemeFromNodes {
    param([object[]]$Nodes)
    $selected = [Collections.Generic.List[string]]::new()
    foreach ($label in @($Nodes | Where-Object { $_.package -ceq 'dev.whiteboard.app' -and $_.text -in @('System', 'Light', 'Dark') })) {
        $owner = $label
        while ($null -ne $owner -and $owner.Name -eq 'node' -and $owner.package -ceq 'dev.whiteboard.app') {
            if ($owner.checkable -eq 'true' -or $owner.selected -eq 'true') {
                if ($owner.checked -eq 'true' -or $owner.selected -eq 'true') { $selected.Add([string]$label.text) }
                break
            }
            $owner = $owner.ParentNode
        }
    }
    if ($selected.Count -ne 1) { throw 'Expected exactly one selected appearance chip in the connected hierarchy.' }
    return $selected[0]
}
function Selected-Theme {
    return Get-SelectedThemeFromNodes -Nodes @(Get-Ui)
}
function Restart-ThemeApp {
    Invoke-Adb -Arguments @('shell', 'am', 'force-stop', 'dev.whiteboard.app') | Out-Null
    Invoke-Adb -Arguments @('shell', 'am', 'start', '-W', '-n', 'dev.whiteboard.app/.MainActivity') | Out-Null
}
$back = @(Get-Ui | Where-Object { $_.'content-desc' -ceq 'Back to boards' })
if ($back.Count) { Tap-Ui $back[0] }
$fixture = @(Get-Ui | Where-Object { $_.text -like 'Recovery_check_*' })
if ($fixture.Count -ne 1) { throw 'Exactly one saved smoke-owned Recovery_check board is required.' }
$board = [string]$fixture[0].text
Open-Settings
$originalTheme = Selected-Theme
Tap-Ui (Find-Ui -Text 'Done')
$cases = @(
    @{ Name = 'light-forced'; Theme = 'Light'; Night = 'yes' },
    @{ Name = 'dark-forced'; Theme = 'Dark'; Night = 'no' },
    @{ Name = 'system-light'; Theme = 'System'; Night = 'no' },
    @{ Name = 'system-dark'; Theme = 'System'; Night = 'yes' }
)
$observations = [Collections.Generic.List[object]]::new()
$status = 'incomplete'
try {
    foreach ($case in $cases) {
        foreach ($font in @('1.0', '2.0')) {
            $name = $case.Name + '-font-' + $font.Replace('.', '')
            Write-Output "Capturing $name with settings/library/editor/color-picker."
            Invoke-Adb shell cmd uimode night $case.Night | Out-Null
            Invoke-Adb shell settings put system font_scale $font | Out-Null
            Find-Ui -Description 'New board' -ExactDescription | Out-Null
            Open-Settings
            Tap-Ui (Find-ThemeSetting -Label $case.Theme)
            Find-ThemeSetting -Label $case.Theme | Out-Null
            if ((Selected-Theme) -cne $case.Theme) { throw 'Requested theme did not become selected.' }
            $observedNight = ((Invoke-Adb shell cmd uimode night) -join '').Trim()
            $observedFont = ((Invoke-Adb shell settings get system font_scale) -join '').Trim()
            if ($observedNight -cne ('Night mode: ' + $case.Night) -or [double]$observedFont -ne [double]$font) { throw 'Requested system appearance/font was not applied.' }
            Capture "$name-settings"
            Tap-Ui (Find-Ui -Text 'Done')
            Find-Ui -Description 'New board' -ExactDescription | Out-Null
            Capture "$name-library"
            Write-Output "Opening exact fixture board: $board"
            Tap-Ui (Find-Ui -Text $board)
            Find-Ui -Description 'Drawing canvas' | Out-Null
            Capture "$name-editor"
            Tap-Ui (Find-Ui -Description 'Color picker' -ExactDescription)
            Find-Ui -Text 'Cancel' | Out-Null
            Capture "$name-color-picker"
            Tap-Ui (Find-Ui -Text 'Cancel')
            Tap-Ui (Find-Ui -Description 'Back to boards' -ExactDescription)
            $observations.Add([ordered]@{ name = $name; selectedTheme = $case.Theme; systemNight = $observedNight; fontScale = $observedFont; scenes = @('settings', 'library', 'editor', 'color-picker') })
        }
    }
    $status = 'passed-ui-capture-only'
} catch {
    # Preserve the original capture failure even if restoring the device also fails.
    Write-Warning ("Appearance capture failed: " + $_.ToString() + "`n" + $_.ScriptStackTrace)
    try {
        if (Test-Path -LiteralPath $taskUiPath) {
            Copy-Item -LiteralPath $taskUiPath -Destination (Join-Path $taskArtifacts 'failure-ui.xml')
        }
    } catch { Write-Warning ("Failure hierarchy unavailable: " + $_.ToString()) }
    try { Capture 'failure-screen' } catch { Write-Warning ("Failure screenshot unavailable: " + $_.ToString()) }
    throw
} finally {
    # A failed capture remains failed; restoration does not dismiss a failure to resume the case.
    if ($originalFont -eq 'null') { Invoke-Adb shell settings delete system font_scale | Out-Null }
    else { Invoke-Adb shell settings put system font_scale $originalFont | Out-Null }
    Invoke-Adb shell cmd uimode night $restoreNight | Out-Null
    Restart-ThemeApp
    Find-Ui -Description 'New board' -ExactDescription | Out-Null
    Open-Settings
    Tap-Ui (Find-ThemeSetting -Label $originalTheme)
    $restoredTheme = Selected-Theme
    Tap-Ui (Find-Ui -Text 'Done')
    $restoredFont = ((Invoke-Adb shell settings get system font_scale) -join '').Trim()
    $restoredNight = ((Invoke-Adb shell cmd uimode night) -join '').Trim()
    $restored = $restoredTheme -ceq $originalTheme -and $restoredFont -ceq $originalFont -and $restoredNight -ceq $originalNight
    $images = @(Get-ChildItem -LiteralPath $taskArtifacts -Filter '*.png' | ForEach-Object { [ordered]@{ name = $_.Name; bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName).Hash } })
    [ordered]@{
        status = $status; sourceCommit = (git rev-parse HEAD).Trim(); package = 'dev.whiteboard.app'
        cases = $observations; images = $images; settingsRestored = $restored
        checkedAtUtc = [datetime]::UtcNow.ToString('o')
        scope = 'EN UI capture for Light/Dark/System light/System dark at font 100/200%, four actual app scenes each. Requires separate visual review; not measured pixel contrast, all locales, all dialogs or spoken TalkBack.'
    } | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $taskArtifacts 'theme-appearance.json')
    if (-not $restored) { throw 'Original appearance/font settings were not restored exactly.' }
}
