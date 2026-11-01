param(
    [Parameter(Mandatory = $true)] [string] $AdbPath,
    [string] $DeviceSerial = 'emulator-5554',
    [string] $Package = 'dev.whiteboard.app',
    [string] $VersionName = '0.4.0'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath $AdbPath -DeviceSerial $DeviceSerial -Package $Package -LoadHelpersOnly
$taskOriginalSize = (Invoke-Adb shell wm size) -join "`n"
$taskOriginalDensity = (Invoke-Adb shell wm density) -join "`n"
$taskRestoreSize = if ($taskOriginalSize -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
$taskRestoreDensity = if ($taskOriginalDensity -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
$taskOriginalFont = ((Invoke-Adb shell settings get system font_scale) -join '').Trim()
$taskCases = @(
    @{ Name='compact-320'; Size='320x640'; Density=160; Font='1.0' },
    @{ Name='font-150'; Size='720x1280'; Density=320; Font='1.5' },
    @{ Name='font-200'; Size='720x1280'; Density=320; Font='2.0' },
    @{ Name='wide-1600'; Size='1600x1000'; Density=160; Font='1.0' }
)
$taskResults = [Collections.Generic.List[object]]::new()
try {
    # Only opens the test board created by Smoke-Test; never modifies personal documents.
    $taskBack = @(Get-Ui | Where-Object { $_.'content-desc' -eq 'Back to boards' })
    if ($taskBack.Count) { Tap-Ui $taskBack[0] }
    Find-Ui -Description 'New board' | Out-Null
    $taskFixture = @(Get-Ui | Where-Object { $_.text -like 'Recovery_check_*' })
    if (-not $taskFixture.Count) { throw 'Run Smoke-Test first: its saved Recovery_check board is required.' }
    $taskBoardName = $taskFixture[0].text
    foreach ($taskCase in $taskCases) {
        Write-Output "Checking $($taskCase.Name): $($taskCase.Size), density $($taskCase.Density), font $($taskCase.Font)."
        Invoke-Adb shell wm density $taskCase.Density | Out-Null
        Invoke-Adb shell wm size $taskCase.Size | Out-Null
        Invoke-Adb shell settings put system font_scale $taskCase.Font | Out-Null
        Find-Ui -Description 'New board' | Out-Null
        Capture ("adaptive-"+$taskCase.Name+'-library')
        $taskSettings = @(Get-Ui | Where-Object { $_.'content-desc' -eq 'Settings' -or $_.text -eq 'Settings' })
        if (-not $taskSettings.Count) { throw 'Settings action is inaccessible.' }
        Tap-Ui $taskSettings[-1]
        foreach ($taskTheme in @('System','Light','Dark')) {
            $taskBounds = Get-Rect (Find-Ui -Text $taskTheme)
            if ($taskBounds[2] -le $taskBounds[0] -or $taskBounds[3] -le $taskBounds[1]) { throw "Theme label is clipped: $taskTheme" }
        }
        Capture ("adaptive-"+$taskCase.Name+'-settings')
        $taskFoundVersion = $false
        $taskFoundLicenses = $false
        for ($taskScroll=0; $taskScroll -lt 12; $taskScroll++) {
            $taskNodes = @(Get-Ui)
            if (@($taskNodes | Where-Object text -eq "Whiteboard $VersionName").Count) { $taskFoundVersion=$true }
            if (@($taskNodes | Where-Object text -eq 'Open source licenses').Count) { $taskFoundLicenses=$true }
            if ($taskFoundVersion -and $taskFoundLicenses) { break }
            $taskScrollable = @($taskNodes | Where-Object scrollable -eq 'true')
            if (-not $taskScrollable.Count) { break }
            $taskArea = Get-Rect $taskScrollable[-1]
            $taskX = [int](($taskArea[0]+$taskArea[2])/2)
            $taskHeight = $taskArea[3]-$taskArea[1]
            Invoke-Adb shell input swipe $taskX ([int]($taskArea[3]-$taskHeight*0.15)) $taskX ([int]($taskArea[1]+$taskHeight*0.15)) 350 | Out-Null
        }
        if (-not $taskFoundVersion -or -not $taskFoundLicenses) { throw "Version/license section could not be reached in $($taskCase.Name)." }
        Tap-Ui (Find-Ui -Text 'Done')
        Tap-Ui (Find-Ui -Text $taskBoardName)
        $taskCanvas = Get-Rect (Find-Ui -Description 'Drawing canvas')
        $taskMinimumPixels = $taskCase.Density
        if ($taskCanvas[2]-$taskCanvas[0] -lt $taskMinimumPixels -or $taskCanvas[3]-$taskCanvas[1] -lt $taskMinimumPixels) { throw 'Editor canvas is smaller than 160 dp.' }
        Find-Ui -Description 'More actions' | Out-Null
        Capture ("adaptive-"+$taskCase.Name+'-editor')
        Tap-Ui (Find-Ui -Description 'Back to boards')
        Find-Ui -Description 'New board' | Out-Null
        $taskResults.Add([ordered]@{ name=$taskCase.Name; size=$taskCase.Size; density=$taskCase.Density; font=$taskCase.Font; canvas=$taskCanvas; status='passed' })
    }
} catch {
    try { Capture 'adaptive-failure' } catch { }
    throw
} finally {
    Invoke-Adb shell wm size $taskRestoreSize | Out-Null
    Invoke-Adb shell wm density $taskRestoreDensity | Out-Null
    if ($taskOriginalFont -eq 'null') { Invoke-Adb shell settings delete system font_scale | Out-Null }
    else { Invoke-Adb shell settings put system font_scale $taskOriginalFont | Out-Null }
    [ordered]@{ checkedAtUtc=[DateTime]::UtcNow.ToString('o'); package=$Package; device=$DeviceSerial; coverage='Resized emulator windows and font scaling; semantic access and screenshots, not TalkBack or physical foldable qualification.'; cases=@($taskResults.ToArray()) } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $taskArtifacts 'local-adaptive-results.json') -Encoding utf8
}
Write-Output 'PASS: compact/wide window, font 150/200%, theme controls, scrollable settings/version/licenses and editor canvas.'
