# 通用 Minecraft 服务端基准脚本骨架
#
# 用法：
#   .\benchmark.ps1 -ServerDir "C:\servers\mc" -JavaArgs "-Xms4G -Xmx4G -XX:+UseG1GC -jar server.jar nogui" -Tag baseline
#
# 说明：
#   - 只负责“固定负载 + 采集”，不做任何配置修改。
#   - spark 不是必需的：没有 spark 时会跳过 profiler，只保留启动/GC 日志。
#   - GC 日志需要由 JavaArgs 里的 -Xlog 指定，否则只会得到 stdout。

param(
    [Parameter(Mandatory = $true)][string]$ServerDir,
    [string]$JavaPath = "java",
    [Parameter(Mandatory = $true)][string]$JavaArgs,
    [string]$Tag = (Get-Date -Format "yyyyMMdd_HHmm"),
    [int]$Bots = 0,
    [int]$WarmupSeconds = 30,
    [int]$ProfileSeconds = 60,
    [switch]$SkipProfile
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ServerDir)) { throw "ServerDir not found: $ServerDir" }

$runs = Join-Path $ServerDir "bench\runs"
New-Item -ItemType Directory -Force -Path $runs | Out-Null
$log = Join-Path $ServerDir "logs\latest.log"

if (Test-Path $log) { Remove-Item $log -Force }

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $JavaPath
$psi.Arguments = $JavaArgs
$psi.WorkingDirectory = $ServerDir
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$p = [System.Diagnostics.Process]::Start($psi)
$outTask = $p.StandardOutput.ReadToEndAsync()
$errTask = $p.StandardError.ReadToEndAsync()

function Send-Cmd([string]$c) {
    Write-Host ">> $c"
    $p.StandardInput.WriteLine($c)
    $p.StandardInput.Flush()
}

$started = Get-Date
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$done = $false
while ($sw.Elapsed.TotalSeconds -lt 300) {
    if (Test-Path $log) {
        $tail = Get-Content $log -Tail 40 -ErrorAction SilentlyContinue
        if ($tail -match 'Done \(') { $done = $true; break }
        if ($tail -match 'Failed to start|Exception in thread "main"') { break }
    }
    Start-Sleep -Seconds 2
}
$startup = $sw.Elapsed.TotalSeconds
Write-Host "startup: $([math]::Round($startup,1))s, done=$done"

if (-not $done) {
    if (-not $p.HasExited) { Send-Cmd "stop" }
    $p.WaitForExit(60000) | Out-Null
    $outTask.Result | Out-File (Join-Path $runs "$Tag-stdout.log") -Encoding utf8
    $errTask.Result | Out-File (Join-Path $runs "$Tag-stderr.log") -Encoding utf8
    throw "SERVER DID NOT START"
}

Start-Sleep -Seconds $WarmupSeconds

# 可选：Carpet 假人。Bots=0 时跳过。
if ($Bots -gt 0) {
    for ($i = 1; $i -le $Bots; $i++) {
        Send-Cmd "player bench$i spawn"
        Start-Sleep -Milliseconds 800
    }
    Start-Sleep -Seconds 10
}

Send-Cmd "spark tps"
Start-Sleep -Seconds 3

if (-not $SkipProfile) {
    Send-Cmd "spark profiler start --timeout $ProfileSeconds"
    Start-Sleep -Seconds ($ProfileSeconds + 15)
    Send-Cmd "spark tps"
    Start-Sleep -Seconds 3
} else {
    Start-Sleep -Seconds 60
    Send-Cmd "spark tps"
    Start-Sleep -Seconds 3
}

Send-Cmd "save-all flush"
Start-Sleep -Seconds 10
Send-Cmd "stop"
$p.WaitForExit(240000) | Out-Null

$outTask.Result | Out-File (Join-Path $runs "$Tag-stdout.log") -Encoding utf8
$errTask.Result | Out-File (Join-Path $runs "$Tag-stderr.log") -Encoding utf8
if (Test-Path $log) { Copy-Item $log (Join-Path $runs "$Tag-latest.log") -Force }

$summary = [ordered]@{
    tag                 = $Tag
    started             = $started.ToString("s")
    startup_done_seconds = [math]::Round($startup, 1)
    bots                = $Bots
    warmup_seconds      = $WarmupSeconds
    profile_seconds     = if ($SkipProfile) { 0 } else { $ProfileSeconds }
    exited              = $p.HasExited
    total_seconds       = [math]::Round($sw.Elapsed.TotalSeconds, 1)
    java_args           = $JavaArgs
}
$summary | ConvertTo-Json | Out-File (Join-Path $runs "$Tag-summary.json") -Encoding utf8

Write-Host "DONE $Tag"
Write-Host "output: $runs\$Tag-stdout.log"
