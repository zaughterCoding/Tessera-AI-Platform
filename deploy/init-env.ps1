param(
    [ValidatePattern('^[a-z][a-z0-9-]{1,20}$')][string]$Environment = 'release',
    [int]$WebPort = 18480,
    [int]$AppPort = 18490,
    [int]$PrometheusPort = 19090,
    [int]$AlertmanagerPort = 19093,
    [int]$ReceiverPort = 19099,
    [int]$GrafanaPort = 13000,
    [switch]$DemoCodeEnabled,
    [switch]$MockPaymentEnabled,
    # 一步配置本地演示：验证码、模拟支付、模拟渠道任务提交与技术体验全部开启。
    [switch]$Demo
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskDirectory = Join-Path $taskRoot ".local\deploy\$Environment"
$taskEnvFile = Join-Path $taskDirectory '.env'
$taskTokenFile = Join-Path $taskDirectory 'monitor-token'
if (Test-Path -LiteralPath $taskEnvFile) { throw '环境文件已存在；请复用原凭据或换一个环境名。' }
New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null

function New-LocalSecret([int]$ByteCount = 32) {
    $taskBytes = New-Object byte[] $ByteCount
    $taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
    return -join ($taskBytes | ForEach-Object { $_.ToString('x2') })
}

function New-KafkaClusterId {
    $taskBytes = New-Object byte[] 16
    $taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
    return [Convert]::ToBase64String($taskBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
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

$taskDatabase = 'ai_creation_' + $Environment.Replace('-', '_')
$taskDemo = if ($DemoCodeEnabled -or $Demo) { 'true' } else { 'false' }
$taskMockPayment = if ($MockPaymentEnabled -or $Demo) { 'true' } else { 'false' }
$taskDemoFlag = if ($Demo) { 'true' } else { 'false' }
$taskSimulatorResultBase = if ($Demo) { 'http://app:8080' } else { '' }
# 演示验证码与模拟支付只允许显式 local/test；默认部署保持 release 且全部关闭。
$taskProfile = if ($DemoCodeEnabled -or $MockPaymentEnabled -or $Demo) { 'test' } else { 'release' }
$taskSlaveId = (Get-Random -Minimum 20001 -Maximum 50000).ToString()
$taskCanalClientPassword = New-LocalSecret
$taskMonitorToken = New-LocalSecret
$taskGenerationCallbackSecret = New-LocalSecret
$taskValues = @(
    "COMPOSE_PROJECT_NAME=ai-creation-$Environment"
    "DEPLOY_ENV=$Environment"
    "MYSQL_DATABASE=$taskDatabase"
    "APP_NAMESPACE=ai-platform:$Environment"
    "APP_REDIS_PREFIX=ai-platform:${Environment}:"
    "APP_PROFILE=$taskProfile"
    "WEB_BIND=127.0.0.1"
    "WEB_PORT=$WebPort"
    "APP_HTTP_PORT=$AppPort"
    "MONITOR_BIND=127.0.0.1"
    "PROMETHEUS_PORT=$PrometheusPort"
    "ALERTMANAGER_PORT=$AlertmanagerPort"
    "ALERT_RECEIVER_PORT=$ReceiverPort"
    "GRAFANA_PORT=$GrafanaPort"
    "APP_DEMO_CODE_ENABLED=$taskDemo"
    "APP_TRADE_MOCK_PAYMENT_ENABLED=$taskMockPayment"
    "APP_EXPERIENCE_ENABLED=$taskDemoFlag"
    "APP_MONITOR_TOKEN=$taskMonitorToken"
    "APP_CACHE_ENABLED=true"
    "APP_MEMCACHE_ENABLED=true"
    "APP_CDC_ENABLED=true"
    "APP_CDC_DESTINATION=$taskDatabase"
    "APP_CDC_SCHEMA=$taskDatabase"
    "APP_CDC_LEASE_SECONDS=30"
    "CANAL_SLAVE_ID=$taskSlaveId"
    "APP_MESSAGING_TRANSPORT=local"
    "APP_MQ_TOPIC=ai-platform-$Environment-business"
    "APP_MQ_GROUP_PREFIX=ai-platform-$Environment"
    "APP_MESSAGING_REDRIVE_SENT_SECONDS=30"
    "APP_MESSAGING_RETRY_BASE_SECONDS=1"
    "APP_MESSAGING_MAX_CONSUME_ATTEMPTS=5"
    "APP_TRADE_ORDER_TIMEOUT_MINUTES=15"
    "APP_TRADE_EXPIRY_SCAN_ENABLED=true"
    "APP_TRADE_EXPIRY_SCAN_MS=5000"
    "APP_TRADE_EXPIRY_SCAN_BATCH=50"
    "APP_ENTITLEMENT_EXPIRY_SCAN_ENABLED=true"
    "APP_ENTITLEMENT_EXPIRY_SCAN_MS=60000"
    "APP_ENTITLEMENT_EXPIRY_SCAN_BATCH=100"
    # 正式发布默认不接受新任务提交；已受理任务仍调度、轮询与对账。
    "APP_GENERATION_SUBMIT_ENABLED=$taskDemoFlag"
    "APP_GENERATION_SCHEDULING_ENABLED=true"
    "APP_GENERATION_POLLING_ENABLED=true"
    "APP_GENERATION_SIMULATOR_ENABLED=true"
    # 演示故障控制与模拟回调只允许在本地/测试显式开启，正式默认关闭。
    "APP_GENERATION_SIMULATOR_FAULT_CONTROL_ENABLED=$taskDemoFlag"
    "APP_GENERATION_SIMULATOR_CALLBACK_ENABLED=false"
    "APP_GENERATION_SIMULATOR_CALLBACK_URL="
    "APP_GENERATION_CHANNEL_CODE=simulated-primary"
    "APP_GENERATION_CHANNEL_MAX_IN_FLIGHT=4"
    "APP_GENERATION_CHANNEL_QUEUE_CAPACITY=200"
    "APP_GENERATION_QUEUE_TIMEOUT_SECONDS=1800"
    "APP_GENERATION_USER_ACTIVE_LIMIT=5"
    "APP_GENERATION_DEFAULT_SCHEDULE_WEIGHT=1"
    "APP_GENERATION_FAIR_SCHEDULING_ENABLED=true"
    "APP_GENERATION_ADAPTIVE_CONCURRENCY_ENABLED=true"
    "APP_GENERATION_ADAPTIVE_INITIAL_IN_FLIGHT=4"
    "APP_GENERATION_ADAPTIVE_WINDOW_SECONDS=5"
    "APP_GENERATION_ADAPTIVE_PAUSE_SECONDS=5"
    "APP_GENERATION_DISPATCH_BATCH=20"
    "APP_GENERATION_POLL_BATCH=20"
    "APP_GENERATION_DISPATCH_INTERVAL_MS=2000"
    "APP_GENERATION_POLL_SCAN_INTERVAL_MS=2000"
    "APP_GENERATION_LEASE_SECONDS=30"
    "APP_GENERATION_SUBMIT_MAX_ATTEMPTS=5"
    "APP_GENERATION_QUERY_MAX_ATTEMPTS=8"
    "APP_GENERATION_RETRY_BASE_SECONDS=2"
    "APP_GENERATION_RETRY_MAX_SECONDS=60"
    "APP_GENERATION_POLL_INTERVAL_SECONDS=2"
    "APP_GENERATION_REVIEW_POLL_SECONDS=300"
    "APP_GENERATION_SIMULATOR_DEFAULT_DURATION_MS=3000"
    "APP_GENERATION_SIMULATOR_QUERY_FAILURES=3"
    "APP_GENERATION_SIMULATOR_MAX_CONCURRENT=0"
    "APP_GENERATION_SIMULATOR_CALLBACK_TIMEOUT_MS=2000"
    "APP_GENERATION_CALLBACK_MAX_SKEW_SECONDS=300"
    "APP_GENERATION_CALLBACK_MAX_BYTES=8192"
    "APP_GENERATION_CALLBACK_SECRET=$taskGenerationCallbackSecret"
    "APP_GENERATION_HTTP_ENABLED=false"
    "APP_GENERATION_HTTP_BASE_URL="
    "APP_GENERATION_HTTP_SUBMIT_PATH=/v1/images/jobs"
    "APP_GENERATION_HTTP_TASK_PATH=/v1/images/jobs/{taskId}"
    "APP_GENERATION_HTTP_CANCEL_PATH="
    "APP_GENERATION_HTTP_MODEL="
    "APP_GENERATION_HTTP_API_KEY="
    "APP_GENERATION_HTTP_AUTH_SCHEME=Bearer"
    "APP_GENERATION_HTTP_ALLOW_INSECURE_HTTP=false"
    "APP_GENERATION_HTTP_REAL_CALL_LIMIT=0"
    "APP_GENERATION_HTTP_CONNECT_TIMEOUT_MS=3000"
    "APP_GENERATION_HTTP_REQUEST_TIMEOUT_MS=25000"
    "APP_GENERATION_HTTP_QUERY_TIMEOUT_MS=10000"
    "APP_GENERATION_HTTP_MAX_RESPONSE_BYTES=262144"
    "APP_GENERATION_HTTP_IDEMPOTENT_SUBMIT=false"
    "APP_GENERATION_HTTP_QUERY_BY_ATTEMPT=false"
    "APP_GENERATION_RESULT_DOWNLOAD_SECRET=$(New-LocalSecret)"
    "APP_GENERATION_RESULT_SIMULATOR_RESULT_BASE_URL=$taskSimulatorResultBase"
    "APP_GENERATION_RESULT_ALLOWED_HOSTS="
    "APP_GENERATION_RESULT_ALLOW_PRIVATE_HOSTS=false"
    "APP_ANALYTICS_ENABLED=true"
    "APP_ANALYTICS_TOPIC=ai-platform-$Environment-analytics"
    "APP_ANALYTICS_GROUP=ai-platform-$Environment-analytics"
    "APP_ANALYTICS_RETENTION_DAYS=30"
    "APP_ANALYTICS_BACKFILL_DAYS=7"
    "APP_KAFKA_CLUSTER_ID=$(New-KafkaClusterId)"
    "APP_RETENTION_CLEANUP_ENABLED=true"
    "APP_RETENTION_OUTBOX_DONE_DAYS=30"
    "APP_RETENTION_INBOX_DAYS=30"
    "APP_RETENTION_MESSAGE_FAILURE_DAYS=30"
    "APP_RETENTION_ANALYTICS_FAILURE_DAYS=30"
    "APP_RETENTION_AUDIT_DAYS=365"
    "APP_RETENTION_BATCH_SIZE=500"
    "MYSQL_ROOT_PASSWORD=$(New-LocalSecret)"
    "APP_DB_PASSWORD=$(New-LocalSecret)"
    "SCHEMA_DB_PASSWORD=$(New-LocalSecret)"
    "REDIS_PASSWORD=$(New-LocalSecret)"
    # MySQL 复制注册的 report-password 上限为 32 字节。
    "CANAL_DB_PASSWORD=$(New-LocalSecret 16)"
    "CANAL_CLIENT_PASSWORD=$taskCanalClientPassword"
    "CANAL_SERVER_PASSWORD=$(Get-CanalServerPassword $taskCanalClientPassword)"
    "GRAFANA_ADMIN_PASSWORD=$(New-LocalSecret)"
)
[System.IO.File]::WriteAllLines($taskEnvFile, $taskValues, (New-Object System.Text.UTF8Encoding $false))
# Prometheus 的凭据文件只保存令牌本身，不写入日志或环境清单。
[System.IO.File]::WriteAllText($taskTokenFile, $taskMonitorToken, (New-Object System.Text.UTF8Encoding $false))
Write-Output "已创建独立部署环境：$Environment"
Write-Output "环境文件：$taskEnvFile"
Write-Output "演示验证码：$taskDemo；模拟支付：$taskMockPayment；技术体验：$taskDemoFlag"
Write-Output "启动命令：powershell -File deploy/start.ps1 -Environment $Environment"
