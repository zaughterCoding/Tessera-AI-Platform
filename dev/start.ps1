param([ValidateSet('dev', 'test', 'load')][string]$Environment = 'dev')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEnvFile = Join-Path $taskRoot ".local\$Environment\.env"
if (-not (Test-Path -LiteralPath $taskEnvFile)) { throw '请先运行 dev/init-env.ps1。' }
& (Join-Path $PSScriptRoot 'build.ps1')
& docker compose --env-file $taskEnvFile -f (Join-Path $PSScriptRoot 'compose.yml') up --build -d --wait
if ($LASTEXITCODE -ne 0) { throw '环境启动失败。' }
$taskHttpSetting = Get-Content -LiteralPath $taskEnvFile | Where-Object { $_ -like 'APP_HTTP_PORT=*' }
$taskHttpPort = ($taskHttpSetting -split '=', 2)[1]
$taskReady = $false
for ($taskAttempt = 0; $taskAttempt -lt 60; $taskAttempt++) {
    try {
        $taskHealth = Invoke-RestMethod "http://127.0.0.1:$taskHttpPort/actuator/health/readiness" -TimeoutSec 2
        if ($taskHealth.status -eq 'UP') { $taskReady = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
}
if (-not $taskReady) { throw '应用未通过依赖就绪检查，请查看该环境的容器日志。' }
Write-Output "应用已就绪：http://127.0.0.1:$taskHttpPort/actuator/health"
