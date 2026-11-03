param(
    [Parameter(Mandatory=$true)][string]$AdbPath,
    [string]$DeviceSerial='emulator-5554',
    [string]$Package='dev.whiteboard.app'
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath $AdbPath -DeviceSerial $DeviceSerial -Package $Package -LoadHelpersOnly
$taskArtifacts=Join-Path $taskRoot 'artifacts/device-release'
New-Item -ItemType Directory -Path $taskArtifacts -Force | Out-Null
$languageCases=[Collections.Generic.List[object]]::new()
$originalChoice=$null
$originalFailure=$null
$restoreFailure=$null

function Open-LanguageSettings {
    $nodes=@(Get-Ui)
    if(@($nodes | Where-Object { $_.text -in @('Language','Jezik') }).Count){return}
    $back=@($nodes | Where-Object { $_.'content-desc' -in @('Back to boards','Nazad na table') })
    if($back.Count){Tap-Ui $back[-1];$nodes=@(Get-Ui)}
    $settings=@($nodes | Where-Object {
        $_.'content-desc' -in @('Settings','Podešavanja') -or $_.text -in @('Settings','Podešavanja')
    })
    if($settings.Count -ne 1){throw 'Expected one library Settings control.'}
    Tap-Ui $settings[0]
    Find-Ui -Text 'Srpski (latinica)' | Out-Null
}

function Assert-LanguageSelection([string]$Label) {
    $node=Find-Ui -Text $Label
    if(!$node.SelectNodes('ancestor-or-self::node[@checked="true" or @selected="true"]').Count){
        throw "Language radio choice was not selected: $Label"
    }
}

function Restart-LanguageApp {
    Invoke-Adb shell am force-stop $Package | Out-Null
    Invoke-Adb -Arguments @('shell','am','start','-W','-n',"$Package/dev.whiteboard.app.MainActivity") | Out-Null
}

try {
    # Smoke-Test leaves one saved fixture and an English UI; no new document is edited here.
    $nodes=@(Get-Ui)
    $back=@($nodes | Where-Object { $_.'content-desc' -eq 'Back to boards' })
    if($back.Count){Tap-Ui $back[-1]}
    Find-Ui -Description 'New board' | Out-Null
    $fixture=@(Get-Ui | Where-Object { $_.text -like 'Recovery_check_*' } | Select-Object -First 1)
    if(!$fixture.Count){throw 'Run release Smoke-Test first; a saved fixture is required.'}
    $fixtureName=[string]$fixture[0].text
    Open-LanguageSettings
    $choices=@(Get-Ui | Where-Object {
        $_.text -in @('Follow system language','English','Srpski (latinica)') -and
        $_.SelectNodes('ancestor-or-self::node[@checked="true" or @selected="true"]').Count -gt 0
    })
    if($choices.Count -ne 1){throw 'Cannot preserve the initial language selection.'}
    $originalChoice=[string]$choices[0].text
    Tap-Ui (Find-Ui -Text 'Srpski (latinica)')
    Find-Ui -Text 'Jezik' | Out-Null
    Assert-LanguageSelection 'Srpski (latinica)'
    Capture 'language-sr-settings'
    Restart-LanguageApp
    Find-Ui -Description 'Nova tabla' | Out-Null
    Open-LanguageSettings
    Find-Ui -Text 'Jezik' | Out-Null
    Assert-LanguageSelection 'Srpski (latinica)'
    $languageCases.Add(@{choice='sr-Latn';status='passed';coverage='In-app choice, recreated UI and persistence after force-stop/relaunch.'})
    Tap-Ui (Find-Ui -Text 'English')
    Find-Ui -Text 'Language' | Out-Null
    Restart-LanguageApp
    Find-Ui -Description 'New board' | Out-Null
    Open-LanguageSettings
    Assert-LanguageSelection 'English'
    Tap-Ui (Find-Ui -Text 'Done')
    Tap-Ui (Find-Ui -Text $fixtureName)
    Find-Ui -Description 'More actions' | Out-Null
    Find-Ui -Text 'Saved on this device' | Out-Null
    Capture 'language-en-reopened-editor'
    $languageCases.Add(@{choice='en';status='passed';coverage='English persistence and reopening the unchanged saved fixture name.'})
} catch {
    $originalFailure=$_
    try {Capture 'language-failure'} catch {}
} finally {
    if($originalChoice){
        try {
            Open-LanguageSettings
            $restoreLabel=if($originalChoice -eq 'Follow system language'){
                $current=@(Get-Ui | Where-Object text -eq 'Prati sistemski jezik')
                if($current.Count){'Prati sistemski jezik'}else{'Follow system language'}
            }else{$originalChoice}
            Tap-Ui (Find-Ui -Text $restoreLabel)
            Find-Ui -Text 'Language' | Out-Null
            Assert-LanguageSelection $originalChoice
            Tap-Ui (Find-Ui -Text 'Done')
        } catch {$restoreFailure=$_}
    }
    [ordered]@{
        checkedAtUtc=[DateTime]::UtcNow.ToString('o');package=$Package;cases=@($languageCases.ToArray())
        originalChoice=$originalChoice;restored=($originalChoice -and !$restoreFailure)
        failure=if($originalFailure){$originalFailure.Exception.Message}else{$null}
        restorationFailure=if($restoreFailure){$restoreFailure.Exception.Message}else{$null}
    } | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $taskArtifacts 'app-language-results.json') -Encoding utf8
}
if($originalFailure){throw $originalFailure}
if($restoreFailure){throw $restoreFailure}
Write-Output 'PASS: optimized app language picker, process-restart persistence, saved reopen and restored initial choice.'
