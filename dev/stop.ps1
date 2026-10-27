param([ValidateSet('dev', 'test', 'load')][string]$Environment = 'dev')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEnvFile = Join-Path $taskRoot ".local\$Environment\.env"
if (-not (Test-Path -LiteralPath $taskEnvFile)) { throw '该环境尚未初始化。' }
& docker compose --env-file $taskEnvFile -f (Join-Path $PSScriptRoot 'compose.yml') stop
if ($LASTEXITCODE -ne 0) { throw '环境停止失败。' }
