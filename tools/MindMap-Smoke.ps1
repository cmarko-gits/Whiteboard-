# Loaded by Smoke-Test.ps1 after its installed-package persistence/connection checks.
function Tap-MindMapAction {
    param([Parameter(Mandatory = $true)][string] $Text)
    $taskUntil = [DateTime]::UtcNow.AddSeconds(15)
    $taskPrevious = $null
    do {
        $taskLabels = @(Get-Ui | Where-Object {
            $_.text -ceq $Text -and $_.SelectNodes('ancestor-or-self::node[@enabled="false"]').Count -eq 0
        })
        if ($taskLabels.Count -gt 1) { throw "Ambiguous mind-map action: $Text" }
        if ($taskLabels.Count -eq 1) {
            $taskLabel = $taskLabels[0]
            $taskOwner = $taskLabel.SelectSingleNode('ancestor-or-self::node[@clickable="true"][1]')
            if ($null -ne $taskOwner) {
                $taskRect = Get-Rect $taskOwner
                if ($taskRect[2] -gt $taskRect[0] -and $taskRect[3] -gt $taskRect[1]) {
                    $taskPosition = $taskLabel.bounds + ':' + $taskOwner.bounds
                    if ($taskPosition -ceq $taskPrevious) {
                        # Exactly one click, using the connected owner's latest settled bounds.
                        Tap-Ui $taskOwner
                        return
                    }
                    $taskPrevious = $taskPosition
                } else { $taskPrevious = $null }
            } else { $taskPrevious = $null }
        } else { $taskPrevious = $null }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $taskUntil)
    throw "Mind-map action did not settle: $Text"
}

function Invoke-MindMapSmoke {
    param([Parameter(Mandatory = $true)][string] $BoardName, [Parameter(Mandatory = $true)][string] $PreviousSize)
    $taskStamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $taskRootCaption = "R8_root_$taskStamp"
    $taskChildCaption = "R8_child_$taskStamp"
    $taskPeerCaption = "R8_peer_$taskStamp"
    $taskChildDescription = "Note: $taskChildCaption"
    function Open-Actions {
        Tap-Ui (Find-Ui -Description 'More actions')
        Tap-Ui (Find-Ui -Text 'Mind maps')
    }
    function Enter-Title([string] $Caption, [bool] $CheckConfiguration = $false) {
        $taskField = Find-Ui -Class 'android.widget.EditText'
        if ($taskField.'resource-id' -cne 'mind_map_node_title' -or $taskField.text -cne '') {
            throw 'Expected an empty create/child/sibling title field; refusing to append to an existing node.'
        }
        Tap-Ui $taskField
        Wait-Keyboard
        $taskField = Find-Ui -Class 'android.widget.EditText'
        if ($taskField.'resource-id' -cne 'mind_map_node_title' -or $taskField.focused -cne 'true' -or $taskField.text -cne '') {
            throw 'The empty mind-map title field did not retain input focus.'
        }
        Invoke-Adb shell input text $Caption | Out-Null
        Find-Ui -Text $Caption -Class 'android.widget.EditText' | Out-Null
        if ($CheckConfiguration) {
            Invoke-Adb shell wm size 1280x720 | Out-Null
            Tap-Ui (Find-Ui -Text $Caption -Class 'android.widget.EditText')
            Wait-Keyboard
            Find-Ui -Text $Caption -Class 'android.widget.EditText' | Out-Null
            Capture 'release-mind-map-child-input'
        }
        Tap-Ui (Find-Ui -Text 'Confirm')
        Wait-Keyboard $false
        if ($CheckConfiguration) { Invoke-Adb shell wm size $PreviousSize | Out-Null }
        Find-Ui -Description "Note: $Caption" -ExactDescription | Out-Null
    }
    function Save-Map {
        Find-Ui -Text 'Unsaved changes' | Out-Null
        Tap-Ui (Find-Ui -Description 'Save')
        Find-Ui -Text 'Saved on this device' | Out-Null
    }
    function Assert-ExpandedNotes {
        foreach ($taskCaption in @($taskRootCaption, $taskChildCaption, $taskPeerCaption)) {
            Find-Ui -Description "Note: $taskCaption" -ExactDescription | Out-Null
        }
    }
    function Get-MapFrames {
        $taskNodes = @(Get-Ui)
        $taskFrames = foreach ($taskCaption in @($taskRootCaption, $taskPeerCaption)) {
            $taskMatches = @($taskNodes | Where-Object { $_.'content-desc' -ceq "Note: $taskCaption" })
            if ($taskMatches.Count -ne 1) { throw "Expected one visible node: $taskCaption" }
            (Get-Rect $taskMatches[0]) -join ','
        }
        return $taskFrames -join ':'
    }
    Write-Output 'Checking editable mind maps in the optimized installed package.'
    Open-Actions
    Tap-MindMapAction 'New mind map'
    Enter-Title $taskRootCaption
    Save-Map
    Open-Actions
    Tap-MindMapAction 'Add child'
    Capture 'release-mind-map-child-panel'
    Enter-Title $taskChildCaption $true
    Save-Map
    Open-Actions
    Tap-MindMapAction 'Add sibling'
    Enter-Title $taskPeerCaption
    Save-Map
    # Select the original child and explicitly make its sibling the new parent.
    Open-Actions
    Tap-Ui (Find-Ui -Text 'Mind maps')
    Tap-Ui (Find-Ui -TextPrefix $taskChildCaption)
    Tap-MindMapAction 'Change parent'
    Tap-Ui (Find-Ui -TextPrefix $taskPeerCaption)
    Save-Map
    Open-Actions
    Tap-MindMapAction 'Select map'
    Assert-ExpandedNotes
    Capture 'release-mind-map-expanded'
    # Multiple selected objects open the browser; choose the parent to collapse.
    Open-Actions
    Tap-Ui (Find-Ui -TextPrefix $taskPeerCaption)
    Tap-MindMapAction 'Collapse branch'
    Find-Ui -Text 'Unsaved changes' | Out-Null
    Tap-Ui (Find-Ui -Description 'Undo')
    Find-Ui -Text 'Saved on this device' | Out-Null
    Open-Actions
    Tap-MindMapAction 'Select map'
    Assert-ExpandedNotes
    $taskExpandedFrames = Get-MapFrames
    # Keep exactly this expanded viewport for Redo, so absence cannot be caused by refitting.
    Tap-Ui (Find-Ui -Description 'Redo')
    Find-Ui -Text 'Unsaved changes' | Out-Null
    if ((Get-MapFrames) -cne $taskExpandedFrames) { throw 'Collapse Redo unexpectedly changed the root/parent screen geometry.' }
    if (@(Get-Ui | Where-Object { $_.'content-desc' -ceq $taskChildDescription }).Count -ne 0) {
        throw 'Collapsed descendant remains in the optimized canvas accessibility projection.'
    }
    Open-Actions
    Tap-Ui (Find-Ui -TextPrefix $taskPeerCaption)
    Find-Ui -Text 'Expand branch' | Out-Null
    Tap-Ui (Find-Ui -Text 'Done')
    Save-Map
    Invoke-Adb shell am force-stop $Package | Out-Null
    Invoke-Adb shell am start -f 0x10008000 -n "$Package/dev.whiteboard.app.MainActivity" | Out-Null
    Find-Ui -Description 'New board' | Out-Null
    Tap-Ui (Find-Ui -Text $BoardName)
    Find-Ui -Text 'Saved on this device' | Out-Null
    Open-Actions
    $taskHidden = Find-Ui -TextPrefix $taskChildCaption
    $taskRow = $taskHidden.SelectSingleNode('ancestor-or-self::node[@clickable="true"][1]')
    if ($null -eq $taskRow) { throw 'Reopened node has no actionable hierarchy row.' }
    $taskLabels = @($taskRow.SelectNodes('descendant-or-self::node') | ForEach-Object { $_.text }) -join ' '
    if ($taskLabels -notmatch '\bLevel 3\b' -or $taskLabels -notmatch '\bHidden\b') {
        throw "Reopened parent/child topology or collapsed state was lost: $taskLabels"
    }
    Tap-Ui (Find-Ui -TextPrefix $taskPeerCaption)
    Tap-MindMapAction 'Expand branch'
    Find-Ui -Text 'Unsaved changes' | Out-Null
    Open-Actions
    Tap-MindMapAction 'Select map'
    Assert-ExpandedNotes
    Save-Map
    Capture 'release-mind-map-reopened'
    Write-Output 'PASS: optimized mind-map create, child/sibling, configuration change, reparent, collapse, Undo/Redo and saved reopen.'
}
