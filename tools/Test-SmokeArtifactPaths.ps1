$ErrorActionPreference = 'Stop'
$fixtureBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$fixtureName = 'whiteboard-smoke-path-' + [guid]::NewGuid().ToString('N')
$fixtureRoot = Join-Path $fixtureBase $fixtureName
$fixtureTools = Join-Path $fixtureRoot 'tools'
New-Item -ItemType Directory -Path $fixtureTools -Force | Out-Null
try {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Smoke-Test.ps1') -Destination (Join-Path $fixtureTools 'Smoke-Test.ps1')
    foreach ($caller in @('Test-WindowModes.ps1', 'Test-Adaptive.ps1')) {
        $sourcePath = Join-Path $PSScriptRoot $caller
        $tokens = $null; $parseErrors = $null
        $ast = [Management.Automation.Language.Parser]::ParseFile($sourcePath, [ref]$tokens, [ref]$parseErrors)
        if ($parseErrors.Count) { throw "Cannot parse $caller" }
        $firstDeviceRead = @($ast.FindAll({
            param($node)
            $node -is [Management.Automation.Language.AssignmentStatementAst] -and $node.Left.Extent.Text -ceq '$taskObservedApi'
        }, $false))
        if ($firstDeviceRead.Count -ne 1 -or $firstDeviceRead[0].Right.Extent.Text -notmatch 'Invoke-Adb') {
            throw "Cannot identify the device-free setup of $caller"
        }
        $source = Get-Content -LiteralPath $sourcePath -Raw
        $prefix = $source.Substring(0, $firstDeviceRead[0].Extent.StartOffset)
        $probe = $prefix + "`n[pscustomobject]@{ artifacts = `$taskArtifacts; directory = `$ArtifactsDirectory; ui = `$taskUiPath }`n"
        $probePath = Join-Path $fixtureTools $caller
        [IO.File]::WriteAllText($probePath, $probe, [Text.UTF8Encoding]::new($false))
        foreach ($explicit in @($false, $true)) {
            $arguments = @{ AdbPath = 'host-probe-must-not-run' }
            if ($caller -ceq 'Test-Adaptive.ps1') { $arguments.VersionName = '0.8.1' }
            $expected = Join-Path $fixtureRoot 'artifacts'
            $expectedUi = 'smoke-ui.xml'
            if ($explicit) {
                $expected = Join-Path $fixtureRoot 'reports with spaces'
                $arguments.ArtifactsDirectory = $expected
                $expectedUi = if ($caller -ceq 'Test-Adaptive.ps1') { 'adaptive-ui.xml' } else { 'window-modes-ui.xml' }
            }
            $result = & $probePath @arguments
            if ($result.artifacts -cne [IO.Path]::GetFullPath($expected) -or
                $result.ui -cne (Join-Path $expected $expectedUi) -or
                ($explicit -and $result.directory -cne $expected)) {
                throw "$caller lost its artifact directory while loading the real smoke helpers (explicit=$explicit)."
            }
        }
    }
    Write-Output 'PASS: both real caller setups preserve default and explicit artifact paths, including spaces, without invoking ADB.'
} finally {
    $resolved = [IO.Path]::GetFullPath($fixtureRoot)
    if ($resolved -cne (Join-Path $fixtureBase $fixtureName) -or
        [IO.Path]::GetFileName($resolved) -cne $fixtureName -or
        $fixtureName -notmatch '^whiteboard-smoke-path-[a-f0-9]{32}$') {
        throw 'Unsafe host fixture cleanup path.'
    }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
