param(
    [ValidatePattern('^[a-z][a-z0-9-]{1,20}$')][string]$Environment = 'release',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEnvFile = Join-Path $taskRoot ".local\deploy\$Environment\.env"
$taskTokenFile = Join-Path $taskRoot ".local\deploy\$Environment\monitor-token"
$taskComposeFile = Join-Path $PSScriptRoot 'compose.yml'
if (-not (Test-Path -LiteralPath $taskEnvFile)) { throw '请先运行 deploy/init-env.ps1。' }
if (-not (Test-Path -LiteralPath $taskTokenFile)) { throw '监控令牌文件缺失，请重新初始化独立环境。' }
if (-not $SkipBuild) { & (Join-Path $PSScriptRoot 'build.ps1') }
& docker compose --env-file $taskEnvFile -f $taskComposeFile up --build -d --wait
if ($LASTEXITCODE -ne 0) { throw '环境启动失败，请查看容器日志。' }
$taskWebSetting = Get-Content -LiteralPath $taskEnvFile | Where-Object { $_ -like 'WEB_PORT=*' }
$taskWebPort = ($taskWebSetting -split '=', 2)[1]
$taskReady = $false
for ($taskAttempt = 0; $taskAttempt -lt 60; $taskAttempt++) {
    try {
        $taskHealth = Invoke-WebRequest "http://127.0.0.1:$taskWebPort/healthz" -UseBasicParsing -TimeoutSec 2
        if ($taskHealth.StatusCode -eq 200 -and $taskHealth.Content -match 'ok') { $taskReady = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
}
if (-not $taskReady) { throw '反向代理未通过健康检查，请查看容器日志。' }
Write-Output "入口已就绪：http://127.0.0.1:$taskWebPort/"
Write-Output "监控入口只绑定本机回环地址；请从环境文件读取监控端口。"
