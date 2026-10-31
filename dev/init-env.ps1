param([ValidateSet('dev', 'test', 'load')][string]$Environment = 'dev')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskDirectory = Join-Path $taskRoot ".local\$Environment"
$taskEnvFile = Join-Path $taskDirectory '.env'
if (Test-Path -LiteralPath $taskEnvFile) { throw '环境文件已存在，请保留原凭据或另选环境。' }
New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null
function New-LocalSecret([int]$ByteCount = 32) {
    $taskBytes = New-Object byte[] $ByteCount
    $taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
    return -join ($taskBytes | ForEach-Object { $_.ToString('x2') })
}
function Get-CanalServerPassword([string]$ClientPassword) {
    # Canal 服务端保存 SHA1(SHA1(明文))，客户端仍使用明文参与握手。
    $taskSha = [System.Security.Cryptography.SHA1]::Create()
    try {
        $taskFirst = $taskSha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($ClientPassword))
        $taskSecond = $taskSha.ComputeHash($taskFirst)
        return -join ($taskSecond | ForEach-Object { $_.ToString('x2') })
    } finally { $taskSha.Dispose() }
}
$taskHttpPort = @{ dev = 18080; test = 18081; load = 18082 }[$Environment]
$taskProfile = @{ dev = 'local'; test = 'test'; load = 'load' }[$Environment]
$taskDemoEnabled = @{ dev = 'true'; test = 'true'; load = 'false' }[$Environment]
$taskMockPayment = @{ dev = 'true'; test = 'true'; load = 'false' }[$Environment]
$taskCanalSlaveId = @{ dev = '12341'; test = '12342'; load = '12343' }[$Environment]
$taskKafkaClusterId = @{
    dev = 'AQrkGJkvTTCsTVgElHWZDg'
    test = 'wUzqfWIcS9OX_SEfGWBxmg'
    load = 'NtaTM3_1SqC1cs5Cgv6VTA'
}[$Environment]
$taskCanalClientPassword = New-LocalSecret()
$taskValues = @(
    "COMPOSE_PROJECT_NAME=ai-creation-$Environment"
    "MYSQL_DATABASE=ai_creation_$Environment"
    "APP_NAMESPACE=ai-platform:$Environment"
    "APP_REDIS_PREFIX=ai-platform:${Environment}:"
    "APP_HTTP_PORT=$taskHttpPort"
    "APP_PROFILE=$taskProfile"
    "APP_DEMO_CODE_ENABLED=$taskDemoEnabled"
    "APP_CACHE_ENABLED=true"
    "APP_MEMCACHE_ENABLED=true"
    "APP_MONITOR_TOKEN=$(New-LocalSecret)"
    "APP_CDC_ENABLED=true"
    "APP_CDC_DESTINATION=ai_creation_$Environment"
    "APP_CDC_SCHEMA=ai_creation_$Environment"
    "APP_CDC_LEASE_SECONDS=30"
    "CANAL_SLAVE_ID=$taskCanalSlaveId"
    "APP_MESSAGING_TRANSPORT=local"
    "APP_MQ_TOPIC=ai-platform-$Environment-business"
    "APP_MQ_GROUP_PREFIX=ai-platform-$Environment"
    "APP_MESSAGING_REDRIVE_SENT_SECONDS=30"
    "APP_MESSAGING_REDRIVE_FAILED_SECONDS=300"
    "APP_MESSAGING_RETRY_BASE_SECONDS=1"
    "APP_MESSAGING_MAX_CONSUME_ATTEMPTS=5"
    "APP_TRADE_ORDER_TIMEOUT_MINUTES=15"
    "APP_TRADE_MOCK_PAYMENT_ENABLED=$taskMockPayment"
    "APP_TRADE_EXPIRY_SCAN_ENABLED=true"
    "APP_TRADE_EXPIRY_SCAN_MS=5000"
    "APP_TRADE_EXPIRY_SCAN_BATCH=50"
    "APP_ENTITLEMENT_EXPIRY_SCAN_ENABLED=true"
    "APP_ENTITLEMENT_EXPIRY_SCAN_MS=60000"
    "APP_ENTITLEMENT_EXPIRY_SCAN_BATCH=100"
    "APP_ANALYTICS_ENABLED=true"
    "APP_ANALYTICS_TOPIC=ai-platform-$Environment-analytics"
    "APP_ANALYTICS_GROUP=ai-platform-$Environment-analytics"
    "APP_KAFKA_CLUSTER_ID=$taskKafkaClusterId"
    "MYSQL_ROOT_PASSWORD=$(New-LocalSecret)"
    "APP_DB_PASSWORD=$(New-LocalSecret)"
    "SCHEMA_DB_PASSWORD=$(New-LocalSecret)"
    "REDIS_PASSWORD=$(New-LocalSecret)"
    # MySQL 复制注册的 report-password 上限为 32 字节。
    "CANAL_DB_PASSWORD=$(New-LocalSecret 16)"
    "CANAL_CLIENT_PASSWORD=$taskCanalClientPassword"
    "CANAL_SERVER_PASSWORD=$(Get-CanalServerPassword $taskCanalClientPassword)"
)
[System.IO.File]::WriteAllLines($taskEnvFile, $taskValues, (New-Object System.Text.UTF8Encoding $false))
Write-Output "已创建独立环境：$Environment；凭据仅保存在 $taskEnvFile。"
