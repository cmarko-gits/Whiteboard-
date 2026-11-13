$ErrorActionPreference = 'Stop'
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Test-ThemeAppearance.ps1'), [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Appearance script syntax failed.' }
$definition = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Get-SelectedThemeFromNodes' }, $true)
if (-not $definition) { throw 'Missing production theme state reader.' }
$read = $definition.Body.GetScriptBlock()
function Read-Fixture([string]$Xml) {
    [xml]$document = $Xml
    return & $read -Nodes @($document.SelectNodes('//node'))
}
$parentState = '<hierarchy><node package="dev.whiteboard.app" checkable="true" checked="true" selected="false"><node package="dev.whiteboard.app" text="System" selected="false"/></node><node package="dev.whiteboard.app" checkable="true" checked="false"><node package="dev.whiteboard.app" text="Dark"/></node></hierarchy>'
if ((Read-Fixture $parentState) -cne 'System') { throw 'Unmerged label did not inherit its checked owner.' }
$merged = '<hierarchy><node package="dev.whiteboard.app" text="Dark" checkable="true" checked="true"/></hierarchy>'
if ((Read-Fixture $merged) -cne 'Dark') { throw 'Merged checked carrier was not read.' }
$selected = '<hierarchy><node package="dev.whiteboard.app" selected="true"><node package="dev.whiteboard.app" text="Light"/></node></hierarchy>'
if ((Read-Fixture $selected) -cne 'Light') { throw 'Selected carrier was not read.' }
foreach ($invalid in @(
    '<hierarchy><node package="dev.whiteboard.app" text="System" checkable="true" checked="false"/></hierarchy>',
    '<hierarchy><node package="dev.whiteboard.app" text="System" checkable="true" checked="true"/><node package="dev.whiteboard.app" text="Dark" selected="true"/></hierarchy>',
    '<hierarchy><node package="other.app" text="System" checkable="true" checked="true"/></hierarchy>',
    '<hierarchy><node package="dev.whiteboard.app" text="System"/></hierarchy>'
)) {
    $rejected = $false
    try { Read-Fixture $invalid | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Missing, ambiguous or wrong-package theme state was accepted.' }
}
Write-Output 'PASS: actual owner/merged/selected carriers; rejects absent, ambiguous, wrong-package and label-only state. No device calls.'

# Execute the actual capture command against the connected canvas description shape.
# Its accessibility instructions are part of the app's public description.
$canvasCommand = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.CommandAst] -and
        $node.GetCommandName() -eq 'Find-Ui' -and
        $node.Extent.Text -match "-Description 'Drawing canvas'"
}, $true)
if (-not $canvasCommand) { throw 'Missing actual appearance canvas selector.' }
. (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -AdbPath unused-host-probe -LoadHelpersOnly
function Invoke-Adb { throw 'A host theme selector check attempted a device call.' }
function Get-Ui { return $script:canvasFixture.SelectNodes('//node') }
foreach ($description in @('Drawing canvas', 'Drawing canvas. Use Objects to browse all objects. Actions allow text, notes, shapes, zoom and pan without drawing gestures.')) {
    [xml]$script:canvasFixture = '<hierarchy><node package="dev.whiteboard.app" enabled="true" content-desc="' + $description + '"/></hierarchy>'
    $node = & ([scriptblock]::Create($canvasCommand.Extent.Text + ' -TimeoutSeconds 0'))
    if ($node.'content-desc' -cne $description) { throw 'Actual canvas selector did not retain its accessibility description.' }
}
Write-Output 'PASS: actual capture selector accepts the canvas with and without accessibility instructions; no device calls.'
