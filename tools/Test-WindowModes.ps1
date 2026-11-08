param(
    [Parameter(Mandatory=$true)] [string] $AdbPath,
    [string] $DeviceSerial='emulator-5554',
    [string] $Package='dev.whiteboard.app',
    [string] $ArtifactsDirectory=''
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath $AdbPath -DeviceSerial $DeviceSerial -Package $Package -LoadHelpersOnly
if($ArtifactsDirectory){
    $taskArtifacts=[IO.Path]::GetFullPath($ArtifactsDirectory)
    New-Item -ItemType Directory -Path $taskArtifacts -Force|Out-Null
    $taskUiPath=Join-Path $taskArtifacts 'window-modes-ui.xml'
}
$taskObservedApi=[int](((Invoke-Adb shell getprop ro.build.version.sdk) -join '').Trim())
$taskSize=(Invoke-Adb shell wm size) -join "`n"
$taskRestoreSize=if($taskSize -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
$taskPhysical=if($taskSize -match '(?:Override|Physical) size: (\d+)x(\d+)') { @([int]$Matches[1],[int]$Matches[2]) } else { throw 'Cannot read display size.' }
$taskAppTask=$null
$taskResults=[Collections.Generic.List[object]]::new()
function Window-Configuration([string] $Mode, [int[]] $PreviousBounds=@(), [int[]] $DisplaySize=@()) {
    $taskUntil=[DateTime]::UtcNow.AddSeconds(15)
    do {
        $taskDump=(Invoke-Adb shell dumpsys activity "$Package/dev.whiteboard.app.MainActivity") -join "`n"
        $taskCurrent=($taskDump -split "`n" | Where-Object { $_ -match 'mCurrentConfig=' } | Select-Object -First 1)
        if($taskCurrent -match ('mWindowingMode='+[regex]::Escape($Mode)+'\b')) {
            if($DisplaySize.Count -eq 2 -and $taskCurrent -notmatch ('mMaxBounds=Rect\(0, 0 - '+$DisplaySize[0]+', '+$DisplaySize[1]+'\)')) {
                Start-Sleep -Milliseconds 200
                continue
            }
            # A position-only split switch may leave the client's last Configuration unchanged.
            # Read current server task bounds for physical placement, not stale client offsets.
            $taskServer=(Invoke-Adb shell am stack list) -join "`n"
            if($taskServer -notmatch ('taskId='+$taskAppTask+':[^\r\n]*bounds=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\][^\r\n]*visible=true')) { throw 'Missing current visible server task bounds.' }
            $taskRectangle=@([int]$Matches[1],[int]$Matches[2],[int]$Matches[3],[int]$Matches[4])
            if($PreviousBounds.Count -eq 4 -and ($taskRectangle -join ',') -eq ($PreviousBounds -join ',')) {
                Start-Sleep -Milliseconds 200
                continue
            }
            return $taskRectangle
        }
        Start-Sleep -Milliseconds 200
    } while([DateTime]::UtcNow -lt $taskUntil)
    $taskDump | Set-Content (Join-Path $taskArtifacts 'qualification-window-failure-config.txt')
    throw "Activity did not enter $Mode; no multi-window pass is recorded."
}
function Check-Editor([string] $Name, [int[]] $Bounds) {
    # A noninteractive title tap does not reliably change accessibility's active split window.
    Invoke-Adb shell am task focus $taskAppTask | Out-Null
    Find-Ui -Description 'Drawing canvas' | Out-Null
    Find-Ui -Text $Name | Out-Null
    Find-Ui -Text 'Saved on this device' | Out-Null
    Tap-Ui (Find-Ui -Description 'Fit to content')
    Find-Ui -Description 'Drawing stroke' | Out-Null
    Find-Ui -Text 'Saved on this device' | Out-Null
    $taskCanvas=Get-Rect (Find-Ui -Description 'Drawing canvas')
    if($taskCanvas[2]-$taskCanvas[0] -lt 80 -or $taskCanvas[3]-$taskCanvas[1] -lt 80) { throw 'Split-screen canvas has insufficient visible space.' }
    return $taskCanvas
}
try {
    $taskNames=@(Get-Ui | Where-Object text -like 'Recovery_check_*')
    if(-not $taskNames.Count) { throw 'Run Smoke-Test first and keep its saved editor open.' }
    $taskName=$taskNames[0].text
    Find-Ui -Description 'Drawing canvas' | Out-Null
    Invoke-Adb -Arguments @('shell','am','start','-W','-a','android.settings.SETTINGS') | Out-Null
    $taskStacks=(Invoke-Adb shell am stack list) -join "`n"
    $taskMatch=[regex]::Match($taskStacks,'taskId=(\d+):\s+'+[regex]::Escape($Package)+'/')
    if(-not $taskMatch.Success) { throw 'Cannot identify the Whiteboard task.' }
    $taskAppTask=[int]$taskMatch.Groups[1].Value
    Invoke-Adb -Arguments @('shell','dumpsys','activity','service','com.android.systemui/.SystemUIService','WMShell','splitscreen','moveToSideStage',"$taskAppTask",'1') | Out-Null
    $taskBounds=Window-Configuration 'multi-window'
    $taskCanvas=Check-Editor $taskName $taskBounds
    Capture 'qualification-split-portrait'
    $taskResults.Add(@{ mode='split-portrait'; appBounds=$taskBounds; canvas=$taskCanvas; status='passed' })
    Invoke-Adb shell dumpsys activity "$Package/dev.whiteboard.app.MainActivity" | Set-Content (Join-Path $taskArtifacts 'qualification-split-portrait-config.txt')
    Invoke-Adb shell am stack list | Add-Content (Join-Path $taskArtifacts 'qualification-split-portrait-config.txt')
    Invoke-Adb -Arguments @('shell','dumpsys','activity','service','com.android.systemui/.SystemUIService','WMShell','splitscreen','switchSplitPosition') | Out-Null
    $taskBounds=Window-Configuration 'multi-window' -PreviousBounds $taskBounds
    $taskCanvas=Check-Editor $taskName $taskBounds
    Capture 'qualification-split-switched'
    $taskResults.Add(@{ mode='split-switched'; appBounds=$taskBounds; canvas=$taskCanvas; status='passed' })
    Invoke-Adb shell wm size "$($taskPhysical[1])x$($taskPhysical[0])" | Out-Null
    $taskBounds=Window-Configuration 'multi-window' -DisplaySize @($taskPhysical[1],$taskPhysical[0])
    $taskCanvas=Check-Editor $taskName $taskBounds
    Capture 'qualification-split-landscape'
    $taskResults.Add(@{ mode='split-landscape'; appBounds=$taskBounds; canvas=$taskCanvas; status='passed' })
    Invoke-Adb shell dumpsys activity "$Package/dev.whiteboard.app.MainActivity" | Set-Content (Join-Path $taskArtifacts 'qualification-split-landscape-config.txt')
    Invoke-Adb shell am stack list | Add-Content (Join-Path $taskArtifacts 'qualification-split-landscape-config.txt')
} catch {
    try { Capture 'qualification-split-failure' } catch { }
    throw
} finally {
    if($null -ne $taskAppTask) {
        Invoke-Adb -Arguments @('shell','dumpsys','activity','service','com.android.systemui/.SystemUIService','WMShell','splitscreen','exitSplitScreen',"$taskAppTask") | Out-Null
    }
    Invoke-Adb shell wm size $taskRestoreSize | Out-Null
    [ordered]@{ schema=1; source=$env:GITHUB_SHA; api=$taskObservedApi; checkedAtUtc=[DateTime]::UtcNow.ToString('o'); package=$Package; device=$DeviceSerial; cases=@($taskResults.ToArray()); scope='Real WindowManager split-screen, position switch, orientation change, visible saved stroke after Fit; not a physical foldable/desktop certification.' } | ConvertTo-Json -Depth 7 | Set-Content (Join-Path $taskArtifacts 'qualification-window-results.json') -Encoding utf8
}
Window-Configuration 'fullscreen' | Out-Null
Find-Ui -Description 'Drawing canvas' | Out-Null
Find-Ui -Text 'Saved on this device' | Out-Null
Write-Output 'PASS: actual split-screen portrait/landscape, side switch, document preservation and return to fullscreen.'
