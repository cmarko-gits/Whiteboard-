# Loaded by Smoke-Test.ps1 after its installed-package persistence/connection checks.
function Tap-MindMapAction {
    [CmdletBinding(DefaultParameterSetName = 'Text')]
    param(
        [Parameter(Mandatory = $true, Position = 0, ParameterSetName = 'Text')][string] $Text,
        [Parameter(Mandatory = $true, ParameterSetName = 'Description')][string] $Description
    )
    if ($Package -notin @('dev.whiteboard.app', 'dev.whiteboard.app.debug')) { throw 'Unexpected UI client target package.' }
    $taskMode = if ($PSCmdlet.ParameterSetName -ceq 'Description') { 'description' } else { 'text' }
    $taskLabel = if ($taskMode -ceq 'description') { $Description } else { $Text }
    $taskBytes = [Text.Encoding]::UTF8.GetBytes($taskLabel)
    if ($taskBytes.Length -eq 0 -or $taskBytes.Length -gt 512) { throw 'Invalid bounded UI control label.' }
    $taskEncoded = [Convert]::ToBase64String($taskBytes)
    # Encode one argument instead of putting labels into an adb shell command string.
    $taskOutput = (Invoke-Adb -Arguments @(
        'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'wb_package', $Package, '-e', 'wb_mode', $taskMode,
        '-e', 'wb_label64', $taskEncoded,
        'dev.whiteboard.app.debug.test/dev.whiteboard.app.ReleaseUiBridgeRunner'
    )) -join "`n"
    if ($taskOutput -notmatch '(?m)^INSTRUMENTATION_CODE: -1\s*$' -or
        $taskOutput -notmatch ('(?m)^INSTRUMENTATION_RESULT: whiteboard_clicked64=' + [regex]::Escape($taskEncoded) + '\s*$') -or
        $taskOutput -notmatch ('(?m)^INSTRUMENTATION_RESULT: whiteboard_package=' + [regex]::Escape($Package) + '\s*$')) {
        throw "Connected UI action failed: $taskMode/$taskLabel`n$($taskOutput.Substring(0, [Math]::Min(8192, $taskOutput.Length)))"
    }
}

function Invoke-MindMapSmoke {
    param([Parameter(Mandatory = $true)][string] $BoardName, [Parameter(Mandatory = $true)][string] $PreviousSize)
    $taskStamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $taskRootCaption = "R8_root_$taskStamp"
    $taskChildCaption = "R8_child_$taskStamp"
    $taskPeerCaption = "R8_peer_$taskStamp"
    $taskChildDescription = "Note: $taskChildCaption"
    function Open-Actions {
        Tap-MindMapAction -Description 'More actions'
        Tap-MindMapAction 'Mind maps'
    }
    function Enter-Title([string] $Caption, [bool] $CheckConfiguration = $false) {
        $taskField = Find-Ui -Class 'android.widget.EditText'
        if ($taskField.'resource-id' -cne 'mind_map_node_title' -or $taskField.text -cne '') {
            throw 'Expected an empty create/child/sibling title field; refusing to append to an existing node.'
        }
        Tap-MindMapAction 'Node title'
        Wait-Keyboard
        $taskField = Find-Ui -Class 'android.widget.EditText'
        if ($taskField.'resource-id' -cne 'mind_map_node_title' -or $taskField.focused -cne 'true' -or $taskField.text -cne '') {
            throw 'The empty mind-map title field did not retain input focus.'
        }
        Invoke-Adb shell input text $Caption | Out-Null
        Find-Ui -Text $Caption -Class 'android.widget.EditText' | Out-Null
        if ($CheckConfiguration) {
            Invoke-Adb shell wm size 1280x720 | Out-Null
            Tap-MindMapAction 'Node title'
            Wait-Keyboard
            Find-Ui -Text $Caption -Class 'android.widget.EditText' | Out-Null
            Capture 'release-mind-map-child-input'
        }
        Tap-MindMapAction 'Confirm'
        Wait-Keyboard $false
        if ($CheckConfiguration) { Invoke-Adb shell wm size $PreviousSize | Out-Null }
        Find-Ui -Description "Note: $Caption" -ExactDescription | Out-Null
    }
    function Save-Map {
        Find-Ui -Text 'Unsaved changes' | Out-Null
        Tap-MindMapAction -Description 'Save'
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
    Tap-MindMapAction 'Mind maps'
    Tap-MindMapAction $taskChildCaption
    Tap-MindMapAction 'Change parent'
    Tap-MindMapAction $taskPeerCaption
    Save-Map
    Open-Actions
    Tap-MindMapAction 'Select map'
    Assert-ExpandedNotes
    Capture 'release-mind-map-expanded'
    # Multiple selected objects open the browser; choose the parent to collapse.
    Open-Actions
    Tap-MindMapAction $taskPeerCaption
    Tap-MindMapAction 'Collapse branch'
    Find-Ui -Text 'Unsaved changes' | Out-Null
    Tap-MindMapAction -Description 'Undo'
    Find-Ui -Text 'Saved on this device' | Out-Null
    Open-Actions
    Tap-MindMapAction 'Select map'
    Assert-ExpandedNotes
    $taskExpandedFrames = Get-MapFrames
    # Keep exactly this expanded viewport for Redo, so absence cannot be caused by refitting.
    Tap-MindMapAction -Description 'Redo'
    Find-Ui -Text 'Unsaved changes' | Out-Null
    if ((Get-MapFrames) -cne $taskExpandedFrames) { throw 'Collapse Redo unexpectedly changed the root/parent screen geometry.' }
    if (@(Get-Ui | Where-Object { $_.'content-desc' -ceq $taskChildDescription }).Count -ne 0) {
        throw 'Collapsed descendant remains in the optimized canvas accessibility projection.'
    }
    Open-Actions
    Tap-MindMapAction $taskPeerCaption
    Find-Ui -Text 'Expand branch' | Out-Null
    Tap-MindMapAction 'Done'
    Save-Map
    Invoke-Adb shell am force-stop $Package | Out-Null
    Invoke-Adb shell am start -f 0x10008000 -n "$Package/dev.whiteboard.app.MainActivity" | Out-Null
    Find-Ui -Description 'New board' | Out-Null
    Tap-MindMapAction $BoardName
    Find-Ui -Text 'Saved on this device' | Out-Null
    Open-Actions
    $taskHidden = Find-Ui -TextPrefix $taskChildCaption
    $taskRow = $taskHidden.SelectSingleNode('ancestor-or-self::node[@clickable="true"][1]')
    if ($null -eq $taskRow) { throw 'Reopened node has no actionable hierarchy row.' }
    $taskLabels = @($taskRow.SelectNodes('descendant-or-self::node') | ForEach-Object { $_.text }) -join ' '
    if ($taskLabels -notmatch '\bLevel 3\b' -or $taskLabels -notmatch '\bHidden\b') {
        throw "Reopened parent/child topology or collapsed state was lost: $taskLabels"
    }
    Tap-MindMapAction $taskPeerCaption
    Tap-MindMapAction 'Expand branch'
    Find-Ui -Text 'Unsaved changes' | Out-Null
    Open-Actions
    Tap-MindMapAction 'Select map'
    Assert-ExpandedNotes
    Save-Map
    Capture 'release-mind-map-reopened'
    Write-Output 'PASS: optimized mind-map create, child/sibling, configuration change, reparent, collapse, Undo/Redo and saved reopen.'
}
