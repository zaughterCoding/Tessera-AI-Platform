param([ValidateSet('dev', 'test', 'load')][string]$Environment = 'dev')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskDirectory = Join-Path $taskRoot ".local\$Environment"
$taskEnvFile = Join-Path $taskDirectory '.env'
if (Test-Path -LiteralPath $taskEnvFile) { throw '环境文件已存在，请保留原凭据或另选环境。' }
New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null
function New-LocalSecret {
    $taskBytes = New-Object byte[] 32
    $taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
    return -join ($taskBytes | ForEach-Object { $_.ToString('x2') })
}
$taskHttpPort = @{ dev = 18080; test = 18081; load = 18082 }[$Environment]
$taskProfile = @{ dev = 'local'; test = 'test'; load = 'load' }[$Environment]
$taskDemoEnabled = @{ dev = 'true'; test = 'true'; load = 'false' }[$Environment]
$taskValues = @(
    "COMPOSE_PROJECT_NAME=ai-creation-$Environment"
    "MYSQL_DATABASE=ai_creation_$Environment"
    "APP_NAMESPACE=ai-platform:$Environment"
    "APP_REDIS_PREFIX=ai-platform:${Environment}:"
    "APP_HTTP_PORT=$taskHttpPort"
    "APP_PROFILE=$taskProfile"
    "APP_DEMO_CODE_ENABLED=$taskDemoEnabled"
    "APP_MONITOR_TOKEN=$(New-LocalSecret)"
    "MYSQL_ROOT_PASSWORD=$(New-LocalSecret)"
    "APP_DB_PASSWORD=$(New-LocalSecret)"
    "SCHEMA_DB_PASSWORD=$(New-LocalSecret)"
    "REDIS_PASSWORD=$(New-LocalSecret)"
)
[System.IO.File]::WriteAllLines($taskEnvFile, $taskValues, (New-Object System.Text.UTF8Encoding $false))
Write-Output "已创建独立环境：$Environment；凭据仅保存在 $taskEnvFile。"
