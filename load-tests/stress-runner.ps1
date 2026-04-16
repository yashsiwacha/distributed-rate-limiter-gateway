$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
[System.Net.ServicePointManager]::DefaultConnectionLimit = 2000

$base = if ($env:BASE_URL) { $env:BASE_URL } else { 'http://localhost:8080' }

try {
    $healthRes = Invoke-WebRequest -Uri "$base/actuator/health" -Method Get -UseBasicParsing
    Write-Output "Health endpoint status code: $($healthRes.StatusCode)"
} catch {
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
        Write-Output "Health endpoint status code: $([int]$_.Exception.Response.StatusCode) (proceeding in degraded mode)"
    } else {
        Write-Output "Health endpoint unavailable (proceeding in degraded mode)"
    }
}

$tokenRes = Invoke-RestMethod -Method Post -Uri "$base/auth/token?userId=stress-user"
if (-not $tokenRes.token) {
    throw 'Failed to acquire JWT token from /auth/token'
}

$handler = [System.Net.Http.HttpClientHandler]::new()
if ($handler.PSObject.Properties.Name -contains 'MaxConnectionsPerServer') {
    $handler.MaxConnectionsPerServer = 2000
}
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(12)
$client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $tokenRes.token)

function Invoke-StressPhase {
    param(
        [string]$Name,
        [int]$Requests,
        [int]$Concurrency,
        [int]$DelayMs
    )

    $latency = [System.Collections.Generic.List[double]]::new()
    $codes = [System.Collections.Generic.List[int]]::new()

    $phaseSw = [System.Diagnostics.Stopwatch]::StartNew()

    $batch = 0
    $totalBatches = [int][Math]::Ceiling($Requests / [double]$Concurrency)

    for ($offset = 0; $offset -lt $Requests; $offset += $Concurrency) {
        $batch++
        $current = [Math]::Min($Concurrency, $Requests - $offset)
        $tasks = [System.Collections.Generic.List[System.Threading.Tasks.Task[object]]]::new()

        for ($j = 0; $j -lt $current; $j++) {
            $i = $offset + $j
            $route = if (($i % 10) -lt 6) {
                "$base/api/service-a/ping?delayMs=$DelayMs"
            } else {
                "$base/api/service-b/ping?delayMs=$DelayMs"
            }
            $capturedRoute = $route

            $task = [System.Threading.Tasks.Task[object]]::Run([Func[object]]{
                $sw = [System.Diagnostics.Stopwatch]::StartNew()
                $code = -1
                try {
                    $resp = $client.GetAsync($capturedRoute).GetAwaiter().GetResult()
                    $code = [int]$resp.StatusCode
                } catch {
                    $code = -1
                }
                $sw.Stop()
                return [PSCustomObject]@{ Code = $code; Lat = [double]$sw.Elapsed.TotalMilliseconds }
            })

            $tasks.Add($task)
        }

        foreach ($t in $tasks) {
            try {
                $r = $t.GetAwaiter().GetResult()
                $latency.Add($r.Lat)
                $codes.Add($r.Code)
            } catch {
                $latency.Add(12000)
                $codes.Add(-1)
            }
        }

        Write-Output "[$Name] batch $batch/$totalBatches complete"
    }

    $phaseSw.Stop()

    $allowed = ($codes | Where-Object { $_ -eq 200 }).Count
    $rejected = ($codes | Where-Object { $_ -eq 429 }).Count
    $errors = ($codes | Where-Object { $_ -ne 200 -and $_ -ne 429 }).Count

    $arr = $latency.ToArray()
    [array]::Sort($arr)

    $avg = if ($arr.Length -gt 0) {
        [math]::Round(($arr | Measure-Object -Average).Average, 2)
    } else { 0 }

    $p95 = if ($arr.Length -gt 0) {
        $idx = [Math]::Min($arr.Length - 1, [int][Math]::Floor($arr.Length * 0.95))
        [math]::Round($arr[$idx], 2)
    } else { 0 }

    [PSCustomObject]@{
        Phase        = $Name
        Requests     = $Requests
        Concurrency  = $Concurrency
        DelayMs      = $DelayMs
        Allowed      = $allowed
        Rejected429  = $rejected
        OtherErrors  = $errors
        AvgLatencyMs = $avg
        P95LatencyMs = $p95
        EffectiveRPS = [math]::Round($Requests / [math]::Max(0.001, $phaseSw.Elapsed.TotalSeconds), 2)
        DurationSec  = [math]::Round($phaseSw.Elapsed.TotalSeconds, 2)
    }
}

Write-Output 'Running stress phase: very_high_load'
$phase1 = Invoke-StressPhase -Name 'very_high_load' -Requests 1200 -Concurrency 120 -DelayMs 2
$phase1 | Format-List

Write-Output 'Running stress phase: tense_upstream_pressure'
$phase2 = Invoke-StressPhase -Name 'tense_upstream_pressure' -Requests 2000 -Concurrency 180 -DelayMs 25
$phase2 | Format-List
