param(
    [ValidatePattern('^[a-z][a-z0-9-]{1,20}$')][string]$Environment = 'acceptance',
    [string]$OutputDirectory,
    [switch]$LeaveStopped
)
$ErrorActionPreference = 'Stop'
# 管道传给容器 stdin 时不带 BOM，否则 sh 会把 BOM 当成首条命令的一部分。
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEnvFile = Join-Path $taskRoot ".local\deploy\$Environment\.env"
$taskComposeFile = Join-Path $PSScriptRoot 'compose.yml'
if (-not (Test-Path -LiteralPath $taskEnvFile)) { throw '该部署环境尚未初始化。' }

function Get-EnvMap([string]$Path) {
    $taskMap = @{}
    foreach ($taskLine in Get-Content -LiteralPath $Path) {
        if ($taskLine -match '^([A-Z0-9_]+)=(.*)$') { $taskMap[$Matches[1]] = $Matches[2] }
    }
    return $taskMap
}
$taskEnv = Get-EnvMap $taskEnvFile
$taskProject = $taskEnv['COMPOSE_PROJECT_NAME']
$taskDatabase = $taskEnv['MYSQL_DATABASE']
$taskComposeArgs = @('compose', '--env-file', $taskEnvFile, '-f', $taskComposeFile)
function Invoke-Compose([string[]]$CommandArgs) {
    & docker @taskComposeArgs @CommandArgs
}
# PowerShell 5.1 会重写原生命令参数中的引号；查询与 Redis 命令改为逐参数传递，不经 sh -c。
$taskMysqlPassword = $taskEnv['MYSQL_ROOT_PASSWORD']
$taskRedisPassword = $taskEnv['REDIS_PASSWORD']
function Invoke-MysqlQuery([string]$Sql) {
    Invoke-Compose @('exec', '-T', '-e', "MYSQL_PWD=$taskMysqlPassword", 'mysql', 'mysql', '-uroot', '-N', '-B', $taskDatabase, '-e', $Sql)
}
function Invoke-RedisCli([string[]]$RedisArgs) {
    Invoke-Compose (@('exec', '-T', '-e', "REDISCLI_AUTH=$taskRedisPassword", 'redis', 'redis-cli') + $RedisArgs)
}

$taskStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $taskRoot ".local\backups\${Environment}-$taskStamp"
}
$taskBackupDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $taskBackupDirectory -Force | Out-Null
$taskStartedAt = (Get-Date).ToUniversalTime().ToString('o')

$taskMysqlId = (& docker @taskComposeArgs @('ps', '-q', 'mysql') | Select-Object -First 1)
$taskRedisId = (& docker @taskComposeArgs @('ps', '-q', 'redis') | Select-Object -First 1)
if (-not $taskMysqlId -or -not $taskRedisId) { throw 'MySQL 或 Redis 未运行，无法执行一致性备份。' }
$taskMediaVolume = (& docker volume ls --filter "label=com.docker.compose.project=$taskProject" --filter 'label=com.docker.compose.volume=media-data' --format '{{.Name}}' | Select-Object -First 1)
if (-not $taskMediaVolume) { throw '未找到该环境的媒体卷。' }

# 停止应用写入后再取数据库、Redis 与媒体快照，保证跨存储边界一致。
Invoke-Compose @('stop', 'app', 'web') | Out-Null
for ($taskAttempt = 0; $taskAttempt -lt 30; $taskAttempt++) {
    $taskRunning = (& docker @taskComposeArgs @('ps', '-q', 'app') | Select-Object -First 1)
    if (-not $taskRunning) { break }
    Start-Sleep -Seconds 1
}
if ($taskRunning) { throw '应用未能停止，已放弃备份以避免不一致快照。' }

try {
    $taskCountsSql = @'
SELECT 'creator_user', COUNT(*) FROM creator_user
UNION ALL SELECT 'creator_role', COUNT(*) FROM creator_role
UNION ALL SELECT 'creator_user_role', COUNT(*) FROM creator_user_role
UNION ALL SELECT 'application_category', COUNT(*) FROM application_category
UNION ALL SELECT 'ai_application', COUNT(*) FROM ai_application
UNION ALL SELECT 'usage_product', COUNT(*) FROM usage_product
UNION ALL SELECT 'purchase_request', COUNT(*) FROM purchase_request
UNION ALL SELECT 'purchase_order', COUNT(*) FROM purchase_order
UNION ALL SELECT 'purchase_payment', COUNT(*) FROM purchase_payment
UNION ALL SELECT 'purchase_order_event', COUNT(*) FROM purchase_order_event
UNION ALL SELECT 'limited_campaign', COUNT(*) FROM limited_campaign
UNION ALL SELECT 'campaign_generation', COUNT(*) FROM campaign_generation
UNION ALL SELECT 'campaign_reservation', COUNT(*) FROM campaign_reservation
UNION ALL SELECT 'campaign_dead_letter', COUNT(*) FROM campaign_dead_letter
UNION ALL SELECT 'media_quota', COUNT(*) FROM media_quota
UNION ALL SELECT 'media_asset', COUNT(*) FROM media_asset
UNION ALL SELECT 'creation', COUNT(*) FROM creation
UNION ALL SELECT 'creation_media', COUNT(*) FROM creation_media
UNION ALL SELECT 'creation_like', COUNT(*) FROM creation_like
UNION ALL SELECT 'follow_edge', COUNT(*) FROM follow_edge
UNION ALL SELECT 'business_outbox', COUNT(*) FROM business_outbox
UNION ALL SELECT 'message_inbox', COUNT(*) FROM message_inbox
UNION ALL SELECT 'message_failure', COUNT(*) FROM message_failure
UNION ALL SELECT 'analytics_outbox', COUNT(*) FROM analytics_outbox
UNION ALL SELECT 'analytics_event', COUNT(*) FROM analytics_event
UNION ALL SELECT 'analytics_application_bucket', COUNT(*) FROM analytics_application_bucket
UNION ALL SELECT 'analytics_visit_user', COUNT(*) FROM analytics_visit_user
UNION ALL SELECT 'analytics_failure', COUNT(*) FROM analytics_failure
UNION ALL SELECT 'analytics_backfill_state', COUNT(*) FROM analytics_backfill_state
UNION ALL SELECT 'operation_audit', COUNT(*) FROM operation_audit
UNION ALL SELECT 'flyway_schema_history', COUNT(*) FROM flyway_schema_history;
'@
    $taskCountsFile = Join-Path $taskBackupDirectory 'counts.sql'
    [System.IO.File]::WriteAllText($taskCountsFile, $taskCountsSql, (New-Object System.Text.UTF8Encoding $false))

    Invoke-Compose @('exec', '-T', 'mysql', 'sh', '-c', 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqldump -uroot --single-transaction --routines --triggers --events --hex-blob --no-tablespaces --set-gtid-purged=OFF $MYSQL_DATABASE > /tmp/ai-creation-backup.sql') | Out-Null
    & docker cp "${taskMysqlId}:/tmp/ai-creation-backup.sql" (Join-Path $taskBackupDirectory 'database.sql')
    if ($LASTEXITCODE -ne 0) { throw '数据库备份文件复制失败。' }
    if ((Get-Item -LiteralPath (Join-Path $taskBackupDirectory 'database.sql')).Length -lt 1024) { throw '数据库备份文件异常偏小，已放弃备份。' }
    Invoke-Compose @('exec', '-T', 'mysql', 'rm', '-f', '/tmp/ai-creation-backup.sql') | Out-Null

    Invoke-Compose @('exec', '-T', 'redis', 'sh', '-c', 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli SAVE') | Out-Null
    Invoke-Compose @('exec', '-T', 'redis', 'sh', '-c', 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli --rdb /data/ai-creation-backup.rdb') | Out-Null
    & docker cp "${taskRedisId}:/data/ai-creation-backup.rdb" (Join-Path $taskBackupDirectory 'redis.rdb')
    if ($LASTEXITCODE -ne 0) { throw 'Redis 快照复制失败。' }
    if ((Get-Item -LiteralPath (Join-Path $taskBackupDirectory 'redis.rdb')).Length -lt 64) { throw 'Redis 快照文件异常偏小，已放弃备份。' }
    Invoke-Compose @('exec', '-T', 'redis', 'rm', '-f', '/data/ai-creation-backup.rdb') | Out-Null

    $taskHostBackup = $taskBackupDirectory.Replace('\', '/')
    & docker run --rm -v "${taskMediaVolume}:/data:ro" -v "${taskHostBackup}:/backup" alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a tar -czf /backup/media.tar.gz -C /data .
    if ($LASTEXITCODE -ne 0) { throw '媒体文件打包失败。' }

    $taskFlyway = Invoke-MysqlQuery 'SELECT installed_rank, version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;'
    $taskFlyway | Out-File -FilePath (Join-Path $taskBackupDirectory 'flyway-history.txt') -Encoding utf8
    if (($taskFlyway -join '').Trim().Length -eq 0) { throw '迁移历史读取为空，已放弃备份。' }

    $taskCounts = Invoke-MysqlQuery ($taskCountsSql.Replace("`r`n", ' ').Replace("`n", ' '))
    $taskCounts | Out-File -FilePath (Join-Path $taskBackupDirectory 'counts.txt') -Encoding utf8
    if (($taskCounts -join '').Trim().Length -eq 0) { throw '业务计数读取为空，已放弃备份。' }

    $taskRedisStats = Invoke-RedisCli @('DBSIZE')
    $taskRedisKeyList = @(Invoke-RedisCli @('--scan', '--pattern', '*'))
    $taskRedisKeys = $taskRedisKeyList.Count
    $taskRedisSamples = @($taskRedisKeyList | Where-Object { $_ -like '*campaign*' } | Select-Object -First 20)
    @("dbsize=$taskRedisStats", "keys=$taskRedisKeys") + $taskRedisSamples | Out-File -FilePath (Join-Path $taskBackupDirectory 'redis-keys.txt') -Encoding utf8

    $taskMediaRaw = @(& docker run --rm -v "${taskMediaVolume}:/data:ro" alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a sh -c 'find /data -type f | wc -l; du -sb /data | cut -f1')
    $taskMediaStats = @("files=$($taskMediaRaw[0].Trim())", "bytes=$($taskMediaRaw[1].Trim())")
    $taskMediaStats | Out-File -FilePath (Join-Path $taskBackupDirectory 'media-stats.txt') -Encoding utf8
    if ((($taskMediaStats -join '')) -notmatch 'files=[1-9][0-9]*') { throw '媒体文件计数异常，已放弃备份。' }

    $taskOffsets = Invoke-Compose @('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-consumer-groups.sh', '--bootstrap-server', '127.0.0.1:9092', '--describe', '--group', $taskEnv['APP_ANALYTICS_GROUP'])
    $taskOffsets | Out-File -FilePath (Join-Path $taskBackupDirectory 'kafka-offsets.txt') -Encoding utf8

    $taskLogical = @{
        namespace = $taskEnv['APP_NAMESPACE']
        redisPrefix = $taskEnv['APP_REDIS_PREFIX']
        mqTopic = $taskEnv['APP_MQ_TOPIC']
        mqGroupPrefix = $taskEnv['APP_MQ_GROUP_PREFIX']
        analyticsTopic = $taskEnv['APP_ANALYTICS_TOPIC']
        analyticsGroup = $taskEnv['APP_ANALYTICS_GROUP']
    }
    $taskFileHashes = @{}
    foreach ($taskName in @('database.sql', 'redis.rdb', 'media.tar.gz')) {
        $taskPath = Join-Path $taskBackupDirectory $taskName
        $taskFileHashes[$taskName] = @{
            bytes = (Get-Item -LiteralPath $taskPath).Length
            sha256 = (Get-FileHash -LiteralPath $taskPath -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    $taskManifest = @{
        createdAt = $taskStartedAt
        sourceEnvironment = $Environment
        sourceProject = $taskProject
        database = $taskDatabase
        gitCommit = (& git -C $taskRoot rev-parse HEAD)
        gitStatus = (& git -C $taskRoot status --porcelain)
        consistency = '应用停止后采集 MySQL 单事务快照、Redis RDB 与媒体归档；恢复顺序为数据库、Redis、媒体、应用。'
        logical = $taskLogical
        files = $taskFileHashes
        counts = ($taskCounts -join "`n")
        redis = ($taskRedisStats -join "`n")
        media = ($taskMediaStats -join "`n")
    }
    $taskManifest | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $taskBackupDirectory 'manifest.json') -Encoding utf8
    Write-Output "备份完成：$taskBackupDirectory"
    Write-Output '备份包含数据库、Redis 协调状态、媒体文件与只读清单；不包含任何明文凭据。'
} finally {
    if (-not $LeaveStopped) {
        Invoke-Compose @('up', '-d', '--wait', 'app', 'web') | Out-Null
    }
}
