param(
    [Parameter(Mandatory = $true)] [string] $AdbPath,
    [string] $DeviceSerial = 'emulator-5554',
    [string] $Package = 'dev.whiteboard.app.debug',
    [string] $ApkPath,
    [switch] $CheckRotation,
    [switch] $SkipAdaptiveResize,
    [switch] $LoadHelpersOnly
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskArtifacts = Join-Path $taskRoot 'artifacts'
New-Item -ItemType Directory -Path $taskArtifacts -Force | Out-Null
$taskUiPath = Join-Path $taskArtifacts 'smoke-ui.xml'

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)
    $taskOutput = & $AdbPath -s $DeviceSerial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($taskOutput -join "`n") }
    return $taskOutput
}
function Get-Ui {
    $taskDumped = $false
    for ($taskAttempt = 0; $taskAttempt -lt 6; $taskAttempt++) {
        $taskDumpOutput = (Invoke-Adb shell uiautomator dump /sdcard/whiteboard-smoke-ui.xml) -join "`n"
        if ($taskDumpOutput -match 'dumped to:') { $taskDumped = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $taskDumped) { throw "UI not ready: $taskDumpOutput" }
    Invoke-Adb pull /sdcard/whiteboard-smoke-ui.xml $taskUiPath | Out-Null
    [xml]$taskXml = Get-Content -LiteralPath $taskUiPath -Raw
    return $taskXml.SelectNodes('//node')
}
function Find-Ui {
    param([string] $Text, [string] $Description, [string] $Class, [int] $TimeoutSeconds = 15)
    $taskUntil = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $taskNodes = @(Get-Ui | Where-Object {
            (-not $Text -or $_.text -eq $Text) -and
            (-not $Description -or $_.'content-desc' -like "$Description*") -and
            (-not $Class -or $_.class -eq $Class) -and
            $_.SelectNodes('ancestor-or-self::node[@enabled="false"]').Count -eq 0
        })
        if ($taskNodes.Count -gt 0) { return $taskNodes[-1] }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $taskUntil)
    throw "Missing UI element: $Text $Description $Class"
}
function Get-Rect {
    param($Node)
    if ($Node.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { throw 'Invalid UI bounds' }
    return @([int]$Matches[1], [int]$Matches[2], [int]$Matches[3], [int]$Matches[4])
}
function Tap-Ui {
    param($Node)
    $taskRect = Get-Rect $Node
    Invoke-Adb shell input tap ([int](($taskRect[0] + $taskRect[2]) / 2)) ([int](($taskRect[1] + $taskRect[3]) / 2)) | Out-Null
}
function Capture {
    param([string] $Name)
    Invoke-Adb -Arguments @('shell', 'screencap', '-p', "/sdcard/whiteboard-$Name.png") | Out-Null
    Invoke-Adb pull "/sdcard/whiteboard-$Name.png" (Join-Path $taskArtifacts "$Name.png") | Out-Null
}
function Wait-Keyboard([bool] $Shown = $true) {
    $taskUntil = [DateTime]::UtcNow.AddSeconds(15)
    $taskExpected = '\bmInputShown=' + $Shown.ToString().ToLowerInvariant() + '\b'
    do {
        $taskIme = (Invoke-Adb shell dumpsys input_method) -join "`n"
        if ($taskIme -match $taskExpected) { return }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $taskUntil)
    throw "Keyboard visibility did not become $Shown; the keyboard regression is not qualified."
}

if ($LoadHelpersOnly) { return }
if ($ApkPath) { Invoke-Adb install -r $ApkPath | Out-Null }
$taskSize = (Invoke-Adb shell wm size) -join "`n"
$taskDensity = (Invoke-Adb shell wm density) -join "`n"
$taskPreviousSize = if ($taskSize -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
$taskPreviousDensity = if ($taskDensity -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
$taskName = 'Recovery_check_' + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
try {
    Write-Output 'Checking create and unsaved recovery.'
    Invoke-Adb shell am force-stop $Package | Out-Null
    Invoke-Adb shell am start -f 0x10008000 -n "$Package/dev.whiteboard.app.MainActivity" | Out-Null
    $taskInitial = @(Get-Ui)
    $taskOnboarding = $taskInitial | Where-Object text -eq 'Start creating' | Select-Object -First 1
    if ($taskOnboarding) { Tap-Ui $taskOnboarding }
    Find-Ui -Description 'New board' | Out-Null
    Wait-Keyboard $false
    Tap-Ui (Find-Ui -Description 'New board')
    Find-Ui -Text 'Board name' | Out-Null
    Tap-Ui (Find-Ui -Class 'android.widget.EditText')
    Invoke-Adb shell input text $taskName | Out-Null
    Wait-Keyboard
    Tap-Ui (Find-Ui -Text 'Confirm')
    Tap-Ui (Find-Ui -Description 'Pen')
    Write-Output 'Checking color input and keyboard/landscape restoration in the installed build.'
    Tap-Ui (Find-Ui -Description 'Color picker')
    Tap-Ui (Find-Ui -Class 'android.widget.EditText')
    Wait-Keyboard
    # Replace the six-digit initial HEX value using real keyboard input.
    Invoke-Adb shell input keyevent 123 67 67 67 67 67 67 | Out-Null
    Invoke-Adb shell input text 65B7F2 | Out-Null
    Find-Ui -Text '65B7F2' -Class 'android.widget.EditText' | Out-Null
    Invoke-Adb shell wm size 1280x720 | Out-Null
    # Configuration changes can hide the IME; explicitly reopen it in the narrow-height layout.
    Tap-Ui (Find-Ui -Text '65B7F2' -Class 'android.widget.EditText')
    Wait-Keyboard
    Find-Ui -Text '65B7F2' -Class 'android.widget.EditText' | Out-Null
    Capture 'release-color-keyboard-landscape'
    Tap-Ui (Find-Ui -Text 'Apply')
    Find-Ui -Description 'Drawing canvas' | Out-Null
    Invoke-Adb shell wm size $taskPreviousSize | Out-Null
    $taskCanvas = Get-Rect (Find-Ui -Description 'Drawing canvas')
    $taskWidth = $taskCanvas[2] - $taskCanvas[0]
    $taskHeight = $taskCanvas[3] - $taskCanvas[1]
    Invoke-Adb shell input swipe ([int]($taskCanvas[0] + $taskWidth / 4)) ([int]($taskCanvas[1] + $taskHeight / 3)) ([int]($taskCanvas[0] + $taskWidth * 3 / 4)) ([int]($taskCanvas[1] + $taskHeight / 2)) 700 | Out-Null
    Find-Ui -Text 'Unsaved changes' | Out-Null
    if ($CheckRotation) {
        Write-Output 'Checking arbitrary object rotation in the optimized installed package.'
        $taskPen = Get-Rect (Find-Ui -Description 'Pen')
        $taskToolY = [int](($taskPen[1] + $taskPen[3]) / 2)
        # Reach the shape palette by scrolling its own toolbar; the tablet may need no scroll.
        Invoke-Adb shell input swipe ([int]($taskCanvas[2] - 24)) $taskToolY ([int]($taskCanvas[0] + 24)) $taskToolY 350 | Out-Null
        Tap-Ui (Find-Ui -Description 'Shapes')
        Tap-Ui (Find-Ui -Text 'Rectangle')
        # Synchronize the native canvas/tool update before injecting the next gesture.
        $taskCanvas = Get-Rect (Find-Ui -Description 'Drawing canvas')
        $taskWidth = $taskCanvas[2] - $taskCanvas[0]
        $taskHeight = $taskCanvas[3] - $taskCanvas[1]
        Write-Output "Creating the rotation rectangle inside canvas [$($taskCanvas -join ',')]."
        Invoke-Adb shell input swipe ([int]($taskCanvas[0] + $taskWidth / 3)) ([int]($taskCanvas[1] + $taskHeight / 4)) ([int]($taskCanvas[0] + $taskWidth * 2 / 3)) ([int]($taskCanvas[1] + $taskHeight * 3 / 4)) 500 | Out-Null
        Find-Ui -Description 'Rectangle' | Out-Null
        Tap-Ui (Find-Ui -Description 'More actions')
        Tap-Ui (Find-Ui -Text 'Select all objects')
        $taskOriginalRectangle = Get-Rect (Find-Ui -Description 'Rectangle')
        Tap-Ui (Find-Ui -Description 'Rotate selection')
        Tap-Ui (Find-Ui -Class 'android.widget.EditText')
        Wait-Keyboard
        Invoke-Adb shell input keyevent 123 67 67 | Out-Null
        Invoke-Adb shell input text 45 | Out-Null
        Find-Ui -Text '45' -Class 'android.widget.EditText' | Out-Null
        Tap-Ui (Find-Ui -Text 'Apply')
        Wait-Keyboard $false
        $taskRotatedRectangle = Get-Rect (Find-Ui -Description 'Rectangle')
        if (($taskRotatedRectangle[2] - $taskRotatedRectangle[0]) -eq ($taskOriginalRectangle[2] - $taskOriginalRectangle[0]) -and
            ($taskRotatedRectangle[3] - $taskRotatedRectangle[1]) -eq ($taskOriginalRectangle[3] - $taskOriginalRectangle[1])) {
            throw 'The rectangle bounds did not change after a 45-degree rotation.'
        }
        Capture 'release-rotated-objects'
        Tap-Ui (Find-Ui -Description 'Undo')
        if (((Get-Rect (Find-Ui -Description 'Rectangle')) -join ',') -ne ($taskOriginalRectangle -join ',')) {
            throw 'Undo did not restore the rectangle bounds in the optimized package.'
        }
        Tap-Ui (Find-Ui -Description 'Redo')
        if (((Get-Rect (Find-Ui -Description 'Rectangle')) -join ',') -ne ($taskRotatedRectangle -join ',')) {
            throw 'Redo did not restore the rotated rectangle bounds in the optimized package.'
        }
    }
    Start-Sleep -Milliseconds 1500
    Invoke-Adb shell am force-stop $Package | Out-Null
    Invoke-Adb shell am start -f 0x10008000 -n "$Package/dev.whiteboard.app.MainActivity" | Out-Null
    Find-Ui -Description 'New board' | Out-Null
    Wait-Keyboard $false
    Tap-Ui (Find-Ui -Text $taskName -Class 'android.widget.TextView')
    Tap-Ui (Find-Ui -Text 'Recover')
    Find-Ui -Text 'Unsaved changes' | Out-Null
    if (@(Get-Ui | Where-Object text -eq 'Make room for an idea.').Count -gt 0) { throw 'Recovered canvas was empty' }
    if ($CheckRotation) { Find-Ui -Description 'Rectangle' | Out-Null }
    Capture 'recovered-editor'
    Tap-Ui (Find-Ui -Description 'Back to boards')
    Tap-Ui (Find-Ui -Text 'Save and close')
    Find-Ui -Description 'New board' | Out-Null
    Tap-Ui (Find-Ui -Text $taskName)
    Find-Ui -Text 'Saved on this device' | Out-Null
    if ($CheckRotation) {
        Find-Ui -Description 'Rectangle' | Out-Null
        Write-Output 'PASS: optimized rotation, Undo/Redo, recovery and saved reopen.'
    }
    Tap-Ui (Find-Ui -Description 'Back to boards')
    Capture 'phone-library'
    if ($SkipAdaptiveResize) {
        Write-Output 'PASS: keyboard, color input, recovery, Save and reopen on the configured device profile. Runtime DPI/tablet resizing was explicitly skipped.'
        return
    }
    Write-Output 'Checking adaptive tablet layout.'
    Invoke-Adb shell wm size 1600x2560 | Out-Null
    Invoke-Adb shell wm density 180 | Out-Null
    Find-Ui -Text 'Whiteboard' | Out-Null
    Capture 'tablet-library'
    Tap-Ui (Find-Ui -Text $taskName)
    Find-Ui -Description 'Drawing canvas' | Out-Null
    Capture 'tablet-editor'
    Invoke-Adb shell wm size 2560x1600 | Out-Null
    Find-Ui -Description 'Drawing canvas' | Out-Null
    Tap-Ui (Find-Ui -Description 'Fit to content')
    Get-Ui | Out-Null
    Capture 'tablet-landscape-editor'
    Tap-Ui (Find-Ui -Description 'Back to boards')
    Find-Ui -Text 'Whiteboard' | Out-Null
    Capture 'tablet-landscape-library'
    Invoke-Adb shell wm density $taskPreviousDensity | Out-Null
    Invoke-Adb shell wm size 2400x1080 | Out-Null
    Find-Ui -Description 'New board' | Out-Null
    Capture 'phone-landscape-library'
    Tap-Ui (Find-Ui -Text $taskName)
    $taskLandscape = Get-Rect (Find-Ui -Description 'Drawing canvas')
    if ($taskLandscape[3] - $taskLandscape[1] -lt 300) { throw 'Compact editor leaves too little drawing area' }
    Tap-Ui (Find-Ui -Description 'Fit to content')
    Get-Ui | Out-Null
    Capture 'phone-landscape-editor'
    Write-Output 'PASS: new-board keyboard Confirm, color keyboard/landscape restoration, recovery, Save and close, reopen, phone/tablet portrait and landscape layouts.'
} catch {
    $taskOriginalError = $_
    try { Capture 'release-smoke-failure' } catch { }
    throw $taskOriginalError
} finally {
    Invoke-Adb shell wm size $taskPreviousSize | Out-Null
    Invoke-Adb shell wm density $taskPreviousDensity | Out-Null
}
