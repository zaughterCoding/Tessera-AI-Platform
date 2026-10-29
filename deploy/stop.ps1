param(
    [ValidatePattern('^[a-z][a-z0-9-]{1,20}$')][string]$Environment = 'release'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEnvFile = Join-Path $taskRoot ".local\deploy\$Environment\.env"
if (-not (Test-Path -LiteralPath $taskEnvFile)) { throw '该部署环境尚未初始化。' }
& docker compose --env-file $taskEnvFile -f (Join-Path $PSScriptRoot 'compose.yml') stop
if ($LASTEXITCODE -ne 0) { throw '环境停止失败。' }
Write-Output '容器已停止；命名卷、媒体文件与告警证据保留。'
