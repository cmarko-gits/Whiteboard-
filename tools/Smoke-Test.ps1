param(
    [Parameter(Mandatory = $true)] [string] $AdbPath,
    [string] $DeviceSerial = 'emulator-5554',
    [string] $Package = 'dev.whiteboard.app.debug',
    [string] $ApkPath,
    [switch] $CheckRotation,
    [switch] $CheckLayers,
    [switch] $CheckAlignment,
    [switch] $CheckObjectMenu,
    [switch] $CheckConnections,
    [switch] $CheckMindMaps,
    [switch] $SkipAdaptiveResize,
    [string] $ArtifactsDirectory = '',
    [switch] $LoadHelpersOnly
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskArtifacts = if ($ArtifactsDirectory) { [IO.Path]::GetFullPath($ArtifactsDirectory) } else { Join-Path $taskRoot 'artifacts' }
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
    param([string] $Text, [string] $Description, [string] $Class, [int] $TimeoutSeconds = 15, [switch] $ExactDescription, [string] $TextPrefix)
    $taskUntil = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $taskNodes = @(Get-Ui | Where-Object {
            (-not $Text -or $_.text -eq $Text) -and
            (-not $TextPrefix -or ([string]$_.text).StartsWith($TextPrefix, [StringComparison]::Ordinal)) -and
            (-not $Description -or $(if ($ExactDescription) { $_.'content-desc' -ceq $Description } else { $_.'content-desc' -like "$Description*" })) -and
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
function Scroll-ToolStripLeft {
    param([int[]] $Canvas, [int] $Y)
    if ($Canvas.Count -ne 4 -or $Canvas[2] -le $Canvas[0]) { throw 'Invalid tool-strip viewport.' }
    $taskStripWidth = $Canvas[2] - $Canvas[0]
    # Android gesture navigation owns the display edges. Scroll inside the actual
    # strip viewport so the test cannot invoke system Back instead of its toolbar.
    Invoke-Adb shell input swipe ([int]($Canvas[0] + $taskStripWidth * 0.8)) $Y `
        ([int]($Canvas[0] + $taskStripWidth * 0.2)) $Y 350 | Out-Null
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

function Assert-LayerMenuState([bool] $ForwardEnabled) {
    $taskNodes = @(Get-Ui)
    foreach ($taskAction in @(
        @{ Label = 'Bring forward one layer'; Enabled = $ForwardEnabled },
        @{ Label = 'Send backward one layer'; Enabled = -not $ForwardEnabled }
    )) {
        $taskNode = $taskNodes | Where-Object text -eq $taskAction.Label | Select-Object -Last 1
        if (-not $taskNode) { throw "Missing layer menu item: $($taskAction.Label)" }
        $taskEnabled = $taskNode.SelectNodes('ancestor-or-self::node[@enabled="false"]').Count -eq 0
        if ($taskEnabled -ne $taskAction.Enabled) { throw "Incorrect layer boundary state: $($taskAction.Label)" }
    }
}

function Get-ConnectionFrames {
    return ((Get-Rect (Find-Ui -Description 'Ellipse')) -join ',') + ':' +
        ((Get-Rect (Find-Ui -Description 'Connection arrow')) -join ',')
}

function Move-ConnectionTarget {
    # Exercise the installed native touch path, using fresh bounds after every change.
    $taskBefore = Get-ConnectionFrames
    $taskEllipse = Get-Rect (Find-Ui -Description 'Ellipse')
    $taskCanvas = Get-Rect (Find-Ui -Description 'Drawing canvas')
    $taskStartX = [int](($taskEllipse[0] + $taskEllipse[2]) / 2)
    $taskStartY = [int](($taskEllipse[1] + $taskEllipse[3]) / 2)
    $taskDistance = [Math]::Max(32, [int](($taskCanvas[2] - $taskCanvas[0]) / 10))
    Invoke-Adb shell input swipe $taskStartX $taskStartY ($taskStartX - $taskDistance) $taskStartY 700 | Out-Null
    $taskUntil = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $taskAfter = Get-ConnectionFrames
        $taskBeforeParts = $taskBefore -split ':'
        $taskAfterParts = $taskAfter -split ':'
        if ($taskBeforeParts[0] -ne $taskAfterParts[0] -and $taskBeforeParts[1] -ne $taskAfterParts[1]) {
            return @{ Before = $taskBefore; After = $taskAfter }
        }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $taskUntil)
    throw 'Moving the ellipse did not update both the object frame and its bound connection.'
}

function Assert-ConnectionFrames([string] $Expected) {
    $taskUntil = [DateTime]::UtcNow.AddSeconds(15)
    do {
        if ((Get-ConnectionFrames) -eq $Expected) { return }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $taskUntil)
    throw "Connection and target frames were not restored exactly: $Expected"
}

if ($LoadHelpersOnly) { return }
if ($CheckLayers -and -not $CheckRotation) { throw 'Layer smoke requires the rotation rectangle fixture.' }
if ($CheckAlignment -and -not $CheckRotation) { throw 'Alignment smoke requires two rotated objects.' }
if ($CheckObjectMenu -and -not $CheckRotation) { throw 'Object menu smoke requires the selected-object fixture.' }
if ($CheckConnections -and -not $CheckRotation) { throw 'Connection smoke requires the rotation rectangle fixture.' }
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
        Scroll-ToolStripLeft -Canvas $taskCanvas -Y $taskToolY
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
    if ($CheckLayers) {
        Write-Output 'Checking single-layer ordering and Undo/Redo in the optimized package.'
        Tap-Ui (Find-Ui -Description 'More actions')
        Tap-Ui (Find-Ui -Text 'Objects')
        Tap-Ui (Find-Ui -Class 'android.widget.EditText')
        Wait-Keyboard
        Invoke-Adb shell input text Rectangle | Out-Null
        Tap-Ui (Find-Ui -Text 'Rectangle')
        Wait-Keyboard $false
        Tap-Ui (Find-Ui -Description 'Selection actions')
        Assert-LayerMenuState $false
        Tap-Ui (Find-Ui -Text 'Send backward one layer')
        Tap-Ui (Find-Ui -Description 'Selection actions')
        Assert-LayerMenuState $true
        Invoke-Adb shell input keyevent 4 | Out-Null
        Tap-Ui (Find-Ui -Description 'Undo')
        Tap-Ui (Find-Ui -Description 'Selection actions')
        Assert-LayerMenuState $false
        Invoke-Adb shell input keyevent 4 | Out-Null
        Tap-Ui (Find-Ui -Description 'Redo')
        Tap-Ui (Find-Ui -Description 'Selection actions')
        Assert-LayerMenuState $true
        Invoke-Adb shell input keyevent 4 | Out-Null
        Capture 'release-layer-order'
        Write-Output 'PASS: optimized single-layer ordering and Undo/Redo boundary states.'
    }
    if ($CheckAlignment) {
        Write-Output 'Checking alignment and exact Undo/Redo in the optimized package.'
        Tap-Ui (Find-Ui -Description 'More actions')
        Tap-Ui (Find-Ui -Text 'Select all objects')
        $taskBeforeRectangle = Get-Rect (Find-Ui -Description 'Rectangle')
        $taskBeforeStroke = Get-Rect (Find-Ui -Description 'Drawing stroke')
        Tap-Ui (Find-Ui -Description 'Selection actions')
        Tap-Ui (Find-Ui -Text 'Alignment')
        Tap-Ui (Find-Ui -Text 'Align left edges')
        $taskAlignedRectangle = Get-Rect (Find-Ui -Description 'Rectangle')
        $taskAlignedStroke = Get-Rect (Find-Ui -Description 'Drawing stroke')
        $taskLeft = [Math]::Min($taskBeforeRectangle[0], $taskBeforeStroke[0])
        if ([Math]::Abs($taskAlignedRectangle[0] - $taskLeft) -gt 2 -or
            [Math]::Abs($taskAlignedStroke[0] - $taskLeft) -gt 2) {
            throw 'The optimized alignment did not align both world frames to the left edge.'
        }
        $taskBeforeFrames = ($taskBeforeRectangle -join ',') + ':' + ($taskBeforeStroke -join ',')
        $taskAlignedFrames = ($taskAlignedRectangle -join ',') + ':' + ($taskAlignedStroke -join ',')
        if ($taskBeforeFrames -eq $taskAlignedFrames) { throw 'Alignment fixture was already aligned; no operation was proved.' }
        Capture 'release-aligned-objects'
        Tap-Ui (Find-Ui -Description 'Undo')
        $taskUndoFrames = ((Get-Rect (Find-Ui -Description 'Rectangle')) -join ',') + ':' +
            ((Get-Rect (Find-Ui -Description 'Drawing stroke')) -join ',')
        if ($taskUndoFrames -ne $taskBeforeFrames) { throw 'Alignment Undo did not restore both object frames.' }
        Tap-Ui (Find-Ui -Description 'Redo')
        $taskRedoFrames = ((Get-Rect (Find-Ui -Description 'Rectangle')) -join ',') + ':' +
            ((Get-Rect (Find-Ui -Description 'Drawing stroke')) -join ',')
        if ($taskRedoFrames -ne $taskAlignedFrames) { throw 'Alignment Redo did not restore both object frames.' }
        Write-Output 'PASS: optimized alignment, left-edge geometry and exact Undo/Redo.'
    }
    if ($CheckObjectMenu) {
        # Focus the native canvas; tapping an already selected object preserves the two-object selection.
        Tap-Ui (Find-Ui -Description 'Drawing stroke')
        Find-Ui -Text '2 selected' | Out-Null
        $taskBeforeMenuRectangle = (Get-Rect (Find-Ui -Description 'Rectangle')) -join ','
        $taskBeforeMenuStroke = (Get-Rect (Find-Ui -Description 'Drawing stroke')) -join ','
        Invoke-Adb shell input keyevent 82 | Out-Null
        Find-Ui -Text 'Rotate 90 degrees' | Out-Null
        Capture 'release-object-menu'
        Tap-Ui (Find-Ui -Text 'Copy within Whiteboard')
        Find-Ui -Description 'More actions' | Out-Null
        Find-Ui -Text '2 selected' | Out-Null
        if (((Get-Rect (Find-Ui -Description 'Rectangle')) -join ',') -ne $taskBeforeMenuRectangle -or
            ((Get-Rect (Find-Ui -Description 'Drawing stroke')) -join ',') -ne $taskBeforeMenuStroke) {
            throw 'Opening the object menu or Copy changed object geometry.'
        }
        Write-Output 'PASS: optimized keyboard object menu and private Copy without geometry changes.'
    }
    if ($CheckConnections) {
        Write-Output 'Checking smart connections and exact Undo/Redo in the optimized package.'
        $taskCanvas = Get-Rect (Find-Ui -Description 'Drawing canvas')
        $taskWidth = $taskCanvas[2] - $taskCanvas[0]
        $taskHeight = $taskCanvas[3] - $taskCanvas[1]
        # The existing stroke cannot be a connection target. Add a distinct second shape.
        Tap-Ui (Find-Ui -Description 'Shapes')
        Tap-Ui (Find-Ui -Text 'Ellipse')
        $taskCanvas = Get-Rect (Find-Ui -Description 'Drawing canvas')
        $taskWidth = $taskCanvas[2] - $taskCanvas[0]
        $taskHeight = $taskCanvas[3] - $taskCanvas[1]
        Invoke-Adb shell input swipe ([int]($taskCanvas[0] + $taskWidth * 0.76)) ([int]($taskCanvas[1] + $taskHeight * 0.12)) ([int]($taskCanvas[0] + $taskWidth * 0.94)) ([int]($taskCanvas[1] + $taskHeight * 0.28)) 500 | Out-Null
        Find-Ui -Description 'Ellipse' | Out-Null
        Tap-Ui (Find-Ui -Description 'More actions')
        Tap-Ui (Find-Ui -Text 'Connect objects')
        Tap-Ui (Find-Ui -Text 'Apply')
        Find-Ui -Description 'Connection arrow' | Out-Null
        # Dialog creation returns to Select; the first drag selects and moves the ellipse.
        $taskMovement = Move-ConnectionTarget
        Tap-Ui (Find-Ui -Description 'Undo')
        Assert-ConnectionFrames $taskMovement.Before
        Tap-Ui (Find-Ui -Description 'Redo')
        Assert-ConnectionFrames $taskMovement.After
        Capture 'release-connected-objects'
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
    if ($CheckConnections) {
        # A fresh move after reopening proves that bindings survived persistence, rather
        # than accepting a visually identical, detached line. Viewport may be refitted.
        # Reopening restores the last drawing tool; choose Select explicitly. Match the
        # exact label so the separate Selection actions button cannot satisfy this lookup.
        Tap-Ui (Find-Ui -Description 'Select' -ExactDescription)
        $taskReopenedMovement = Move-ConnectionTarget
        Tap-Ui (Find-Ui -Description 'Undo')
        Assert-ConnectionFrames $taskReopenedMovement.Before
        Capture 'release-reopened-connections'
        Write-Output 'PASS: optimized connections follow moved targets with exact Undo/Redo and saved reopen.'
    }
    if ($CheckMindMaps) {
        . (Join-Path $PSScriptRoot 'MindMap-Smoke.ps1')
        Invoke-MindMapSmoke -BoardName $taskName -PreviousSize $taskPreviousSize
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
