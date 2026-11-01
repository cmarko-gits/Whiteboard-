param([Parameter(Mandatory = $true)] [string] $Origin)
$ErrorActionPreference = 'Stop'
$taskUri = [Uri] $Origin
if (-not $taskUri.IsAbsoluteUri -or $taskUri.UserInfo -or $taskUri.Query -or $taskUri.Fragment -or $taskUri.AbsolutePath -ne '/') {
    throw 'Supply an origin without credentials, path, query or fragment.'
}
if ($taskUri.Scheme -ne 'https' -and -not ($taskUri.Scheme -eq 'http' -and $taskUri.IsLoopback)) {
    throw 'Public endpoint checks require HTTPS. HTTP is allowed only for loopback.'
}
$taskHandler = [Net.Http.HttpClientHandler]::new()
$taskHandler.AllowAutoRedirect = $false
$taskClient = [Net.Http.HttpClient]::new($taskHandler)
$taskClient.Timeout = [TimeSpan]::FromSeconds(10)
try {
    foreach ($taskPath in @('/health', '/v1/account', '/v1/does-not-exist')) {
        $taskResponse = $taskClient.GetAsync($Origin.TrimEnd('/') + $taskPath).GetAwaiter().GetResult()
        try {
            $taskExpected = switch ($taskPath) { '/health' { 200 } '/v1/account' { 401 } default { 404 } }
            if ([int] $taskResponse.StatusCode -ne $taskExpected) { throw "Unexpected status for $taskPath." }
            if (-not $taskResponse.Headers.CacheControl.NoStore -or
                -not $taskResponse.Headers.Contains('X-Content-Type-Options') -or
                ($taskResponse.Headers.GetValues('X-Content-Type-Options') -join '') -ne 'nosniff' -or
                ($taskResponse.Headers.GetValues('X-Frame-Options') -join '') -ne 'DENY') {
                throw "Required response protection missing for $taskPath."
            }
            if ($taskPath -eq '/health') {
                $taskBody = $taskResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
                if ($taskBody.status -ne 'ok') { throw 'Health check did not return the expected process status.' }
            }
        } finally { $taskResponse.Dispose() }
    }
    Write-Output 'Health, unauthenticated denial, missing-route status and cache/security headers passed.'
} finally { $taskClient.Dispose() }
