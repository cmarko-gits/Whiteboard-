param(
    [ValidateSet('37.1','36','36.1')] [string] $Image = '37.1',
    [int] $Port = 5554,
    [ValidateRange(2048,4096)] [int] $MemoryMB = 3072
)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskSdk=Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$taskAdb=Join-Path $taskSdk 'platform-tools/adb.exe'
$taskDevices=(& $taskAdb devices) -join "`n"
if($LASTEXITCODE -ne 0) { throw 'Cannot inspect ADB devices before emulator startup.' }
if($taskDevices -match '(?m)^emulator-\d+\s') { throw 'Stop the existing emulator before starting another QA image.' }
$taskVmProcesses=@(Get-CimInstance Win32_Process | Where-Object { $_.Name -like 'qemu-system-*' -or $_.Name -eq 'emulator.exe' })
if($taskVmProcesses.Count) { throw 'An emulator VM is still running or shutting down; ADB disappearance does not establish process exit.' }
$taskOs=Get-CimInstance Win32_OperatingSystem
if($taskOs.FreePhysicalMemory -lt ($MemoryMB+1024)*1024) { throw 'Insufficient free host RAM; avoid automatic host paging during QA startup.' }
$taskBudgetPath=Join-Path $taskRoot 'artifacts/qualification-storage-baseline.json'
if(Test-Path -LiteralPath $taskBudgetPath) {
    $taskBudget=Get-Content -LiteralPath $taskBudgetPath -Raw | ConvertFrom-Json
    $taskQaHome=Join-Path $taskRoot 'artifacts/qualification-avd'
    $taskHasQaDisk=(Test-Path -LiteralPath (Join-Path $taskQaHome 'qualification-owned.txt')) -and
        (Test-Path -LiteralPath (Join-Path $taskQaHome 'Whiteboard_QA.avd/userdata-qemu.img.qcow2'))
    # Existing 2GB AVD boot has no fresh userdata or RAM backing allocation. Its bounded
    # follow-up tests use a smaller reserve together with the mandatory runtime watcher.
    $taskReserve=if($Image -eq '37.1' -and $MemoryMB -eq 2048){512000000}elseif($Image -eq '37.1' -or $taskHasQaDisk){1000000000}else{2000000000}
    if((Get-PSDrive C).Free -lt $taskBudget.freeBytesAtStart-$taskBudget.maxAdditionalBytes+$taskReserve) {
        throw 'Insufficient storage headroom; do not start another emulator under the current qualification budget.'
    }
}
# Disabling snapshot load/save alone can still allocate a RAM-sized Quickboot backing file.
# QA uses anonymous guest RAM and no snapshots to keep disk growth within its explicit budget.
$taskArguments=@('-port',"$Port",'-no-window','-no-audio','-no-boot-anim','-no-snapshot','-feature','-QuickbootFileBacked','-cores','2','-memory',"$MemoryMB",'-gpu','swiftshader','-camera-back','none','-camera-front','none')
$taskPreviousAvdHome=$env:ANDROID_AVD_HOME
try {
    if ($Image -eq '37.1') { $taskArguments=@('-avd','Small_Phone')+$taskArguments }
    else {
        # Fresh isolated QA userdata; never boots an older OS against the user's existing AVD data.
        # Google Play images can enforce a larger virtual partition than requested; monitor actual host allocation.
        $taskAvdHome=[IO.Path]::GetFullPath((Join-Path $taskRoot 'artifacts/qualification-avd'))
        $taskAvdPath=Join-Path $taskAvdHome 'Whiteboard_QA.avd'
        if((Test-Path -LiteralPath $taskAvdHome) -and -not (Test-Path -LiteralPath (Join-Path $taskAvdHome 'qualification-owned.txt'))) {
            throw 'Refusing to change an existing directory without the qualification ownership marker.'
        }
        $taskSystem=Join-Path $taskSdk "system-images/android-$Image/google_apis_playstore/x86_64"
        if (-not (Test-Path -LiteralPath (Join-Path $taskSystem 'system.img'))) { throw 'Requested system image is not installed; no image download is permitted by this helper.' }
        New-Item -ItemType Directory -Path $taskAvdPath -Force | Out-Null
        $taskBase=Join-Path $env:USERPROFILE '.android/avd/Small_Phone.avd/config.ini'
        $taskConfig=[ordered]@{}
        foreach($taskLine in Get-Content -LiteralPath $taskBase) { if($taskLine -match '^([^=]+)=(.*)$') { $taskConfig[$Matches[1]]=$Matches[2] } }
        $taskConfig['AvdId']='Whiteboard_QA'
        $taskConfig['avd.ini.displayname']='Whiteboard isolated QA'
        $taskConfig['image.sysdir.1']="system-images\android-$Image\google_apis_playstore\x86_64\"
        $taskConfig['image.androidVersion.api']=$Image
        $taskConfig['disk.dataPartition.size']='1024M'
        $taskConfig['sdcard.size']='16M'
        $taskConfig['tag.id']='google_apis_playstore'
        $taskConfig['tag.ids']='google_apis_playstore'
        $taskConfig['tag.display']='Google Play'
        $taskConfig['tag.displaynames']='Google Play'
        $taskConfig['fastboot.forceColdBoot']='yes'
        $taskConfig['fastboot.forceFastBoot']='no'
        $taskConfig['showDeviceFrame']='no'
        $taskConfig.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" } | Set-Content -LiteralPath (Join-Path $taskAvdPath 'config.ini') -Encoding utf8
        @('avd.ini.encoding=UTF-8',"path=$taskAvdPath","target=android-$Image") | Set-Content -LiteralPath (Join-Path $taskAvdHome 'Whiteboard_QA.ini') -Encoding utf8
        'Disposable Whiteboard qualification AVD; contains only generated QA fixtures.' | Set-Content -LiteralPath (Join-Path $taskAvdHome 'qualification-owned.txt') -Encoding utf8
        $env:ANDROID_AVD_HOME=$taskAvdHome
        $taskArguments=@('-avd','Whiteboard_QA','-no-cache')+$taskArguments
    }
    $taskProcess=Start-Process (Join-Path $taskSdk 'emulator/emulator.exe') -ArgumentList $taskArguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $taskRoot "artifacts/qualification-emulator-$Image-out.log") `
        -RedirectStandardError (Join-Path $taskRoot "artifacts/qualification-emulator-$Image-err.log")
    if(Test-Path -LiteralPath $taskBudgetPath) {
        Start-Process -FilePath (Get-Process -Id $PID).Path `
            -ArgumentList @('-NoProfile','-File',('"{0}"' -f (Join-Path $taskRoot 'tools/Watch-QaStorage.ps1')),'-EmulatorProcessId',"$($taskProcess.Id)",'-DeviceSerial',"emulator-$Port") `
            -WindowStyle Hidden -WorkingDirectory $taskRoot `
            -RedirectStandardOutput (Join-Path $taskRoot 'artifacts/task-09-storage-watch-out.log') `
            -RedirectStandardError (Join-Path $taskRoot 'artifacts/task-09-storage-watch-err.log') | Out-Null
    }
    Write-Output "Started $Image image as emulator-$Port; process $($taskProcess.Id). Poll sys.boot_completed before installation."
} finally { $env:ANDROID_AVD_HOME=$taskPreviousAvdHome }
