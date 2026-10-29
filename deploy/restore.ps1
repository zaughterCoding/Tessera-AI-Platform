param(
    [Parameter(Mandatory)][ValidatePattern('^restore-[a-z0-9-]{1,20}$')][string]$Environment,
    [Parameter(Mandatory)][string]$BackupDirectory
)
$ErrorActionPreference = 'Stop'
# 管道传给容器 stdin 时不带 BOM，否则 sh 会把 BOM 当成首条命令的一部分。
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEnvFile = Join-Path $taskRoot ".local\deploy\$Environment\.env"
$taskComposeFile = Join-Path $PSScriptRoot 'compose.yml'
$taskBackupDirectory = [System.IO.Path]::GetFullPath($BackupDirectory)
if (-not (Test-Path -LiteralPath $taskEnvFile)) { throw '目标隔离环境尚未初始化。' }
foreach ($taskRequired in @('manifest.json', 'database.sql', 'redis.rdb', 'media.tar.gz', 'counts.sql', 'flyway-history.txt')) {
    if (-not (Test-Path -LiteralPath (Join-Path $taskBackupDirectory $taskRequired))) { throw "备份缺少文件：$taskRequired" }
}

function Get-EnvMap([string]$Path) {
    $taskMap = @{}
    foreach ($taskLine in Get-Content -LiteralPath $Path) {
        if ($taskLine -match '^([A-Z0-9_]+)=(.*)$') { $taskMap[$Matches[1]] = $Matches[2] }
    }
    return $taskMap
}
function Set-EnvValue([string]$Path, [string]$Key, [string]$Value) {
    $taskLines = @(Get-Content -LiteralPath $Path)
    $taskFound = $false
    for ($taskIndex = 0; $taskIndex -lt $taskLines.Count; $taskIndex++) {
        if ($taskLines[$taskIndex] -like "$Key=*") {
            $taskLines[$taskIndex] = "$Key=$Value"
            $taskFound = $true
        }
    }
    if (-not $taskFound) { $taskLines += "$Key=$Value" }
    [System.IO.File]::WriteAllLines($Path, $taskLines, (New-Object System.Text.UTF8Encoding $false))
}

$taskManifest = Get-Content -LiteralPath (Join-Path $taskBackupDirectory 'manifest.json') -Raw | ConvertFrom-Json
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

# 恢复后的新实例沿用来源的 Redis 命名空间与消息主题，保证协调状态和未完成任务可继续处理；
# 数据库名、凭据、端口与卷仍使用目标隔离环境自己的值。
$taskLogical = $taskManifest.logical
Set-EnvValue $taskEnvFile 'APP_NAMESPACE' $taskLogical.namespace
Set-EnvValue $taskEnvFile 'APP_REDIS_PREFIX' $taskLogical.redisPrefix
Set-EnvValue $taskEnvFile 'APP_MQ_TOPIC' $taskLogical.mqTopic
Set-EnvValue $taskEnvFile 'APP_MQ_GROUP_PREFIX' $taskLogical.mqGroupPrefix
Set-EnvValue $taskEnvFile 'APP_ANALYTICS_TOPIC' $taskLogical.analyticsTopic
Set-EnvValue $taskEnvFile 'APP_ANALYTICS_GROUP' $taskLogical.analyticsGroup

$taskReport = @{
    startedAt = (Get-Date).ToUniversalTime().ToString('o')
    targetEnvironment = $Environment
    sourceEnvironment = $taskManifest.sourceEnvironment
    checks = @{}
    failures = @()
}
$taskFailures = New-Object System.Collections.Generic.List[string]
function Add-Check([string]$Name, [bool]$Passed, [string]$Detail) {
    $taskReport.checks[$Name] = @{ passed = $Passed; detail = $Detail }
    if (-not $Passed) { $taskFailures.Add($Name) }
}

try {
    Invoke-Compose @('up', '-d', 'mysql', 'redis') | Out-Null
    $taskMysqlId = ''
    $taskRedisId = ''
    for ($taskAttempt = 0; $taskAttempt -lt 90; $taskAttempt++) {
        $taskMysqlId = (& docker @taskComposeArgs @('ps', '-q', 'mysql') | Select-Object -First 1)
        $taskRedisId = (& docker @taskComposeArgs @('ps', '-q', 'redis') | Select-Object -First 1)
        if ($taskMysqlId -and $taskRedisId) {
            $taskMysqlState = (& docker inspect -f '{{.State.Health.Status}}' $taskMysqlId)
            $taskRedisState = (& docker inspect -f '{{.State.Health.Status}}' $taskRedisId)
            if ($taskMysqlState -eq 'healthy' -and $taskRedisState -eq 'healthy') { break }
        }
        Start-Sleep -Seconds 1
    }
    if (-not $taskMysqlId -or -not $taskRedisId) { throw '目标环境的 MySQL/Redis 未能启动。' }

    $taskTableCount = (Invoke-MysqlQuery 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();' | Select-Object -First 1).Trim()
    if ($taskTableCount -ne '0') { throw '目标环境数据库不是空库，已拒绝覆盖。' }

    & docker cp (Join-Path $taskBackupDirectory 'database.sql') "${taskMysqlId}:/tmp/ai-creation-restore.sql"
    if ($LASTEXITCODE -ne 0) { throw '数据库备份文件复制失败。' }
    Invoke-Compose @('exec', '-T', 'mysql', 'sh', '-c', 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot $MYSQL_DATABASE < /tmp/ai-creation-restore.sql') | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '数据库导入失败。' }
    Invoke-Compose @('exec', '-T', 'mysql', 'rm', '-f', '/tmp/ai-creation-restore.sql') | Out-Null

    $taskCountsQuery = (Get-Content -LiteralPath (Join-Path $taskBackupDirectory 'counts.sql') -Raw).Replace("`r`n", ' ').Replace("`n", ' ')
    $taskRestoredCounts = @(Invoke-MysqlQuery $taskCountsQuery)
    $taskCountsMatch = (($taskRestoredCounts -join "`n").Trim() -eq ([string]$taskManifest.counts).Trim())
    Add-Check 'business_counts' $taskCountsMatch (($taskRestoredCounts -join '; '))

    $taskRestoredFlyway = @(Invoke-MysqlQuery 'SELECT installed_rank, version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;')
    $taskExpectedFlyway = @(Get-Content -LiteralPath (Join-Path $taskBackupDirectory 'flyway-history.txt') | Where-Object { $_ -ne '' })
    $taskFlywayMatch = (($taskRestoredFlyway -join "`n").Trim() -eq ($taskExpectedFlyway -join "`n").Trim())
    Add-Check 'flyway_history' $taskFlywayMatch (($taskRestoredFlyway -join '; '))

    Invoke-Compose @('stop', 'redis') | Out-Null
    $taskRedisVolume = (& docker volume ls --filter "label=com.docker.compose.project=$taskProject" --filter 'label=com.docker.compose.volume=redis-data' --format '{{.Name}}' | Select-Object -First 1)
    if (-not $taskRedisVolume) { throw '未找到目标 Redis 卷。' }
    $taskRedisUid = (& docker run --rm redis:7-alpine@sha256:858f009f9709ce576febc734aa78b8f6d624b82571f9ddb6bda4377c833b3499 id -u redis).Trim()
    $taskHostBackup = $taskBackupDirectory.Replace('\', '/')
    & docker run --rm -v "${taskRedisVolume}:/data" -v "${taskHostBackup}:/backup:ro" alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a sh -c "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true; cp /backup/redis.rdb /data/dump.rdb; chown -R ${taskRedisUid}:${taskRedisUid} /data"
    if ($LASTEXITCODE -ne 0) { throw 'Redis 快照恢复失败。' }
    # Redis 以 AOF 模式运行，直接放回 dump.rdb 不会载入；先用临时实例载入 RDB，再生成含数据的 AOF。
    $taskRedisImage = 'redis:7-alpine@sha256:858f009f9709ce576febc734aa78b8f6d624b82571f9ddb6bda4377c833b3499'
    $taskSeedName = 'ai-creation-redis-seed-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    & docker run -d --rm --name $taskSeedName -v "${taskRedisVolume}:/data" $taskRedisImage redis-server --appendonly no --dir /data --dbfilename dump.rdb --requirepass $taskRedisPassword | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Redis 快照装载容器启动失败。' }
    $taskSeedReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 60; $taskAttempt++) {
        $taskPong = (& docker exec $taskSeedName redis-cli --no-auth-warning -a $taskRedisPassword PING 2>$null)
        if (($taskPong -join '').Trim() -eq 'PONG') { $taskSeedReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $taskSeedReady) { & docker rm -f $taskSeedName | Out-Null; throw 'Redis 快照装载实例未就绪。' }
    $taskSeedSize = (& docker exec $taskSeedName redis-cli --no-auth-warning -a $taskRedisPassword DBSIZE | Select-Object -First 1).Trim()
    & docker exec $taskSeedName redis-cli --no-auth-warning -a $taskRedisPassword CONFIG SET appendonly yes | Out-Null
    $taskAofReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 60; $taskAttempt++) {
        $taskPersistence = (& docker exec $taskSeedName redis-cli --no-auth-warning -a $taskRedisPassword INFO persistence) -join "`n"
        if ($taskPersistence -match 'aof_enabled:1' -and $taskPersistence -match 'aof_rewrite_in_progress:0' -and $taskPersistence -match 'aof_current_size:[1-9][0-9]*') { $taskAofReady = $true; break }
        Start-Sleep -Seconds 1
    }
    & docker stop $taskSeedName | Out-Null
    if (-not $taskAofReady) { throw 'Redis 快照未能写入 AOF。' }
    if (-not ($taskSeedSize -match '^\d+$' -and [int]$taskSeedSize -gt 0)) { throw 'Redis 快照载入后为空。' }
    Invoke-Compose @('up', '-d', 'redis') | Out-Null
    Start-Sleep -Seconds 2
    $taskRedisSize = (Invoke-RedisCli @('DBSIZE') | Select-Object -First 1).Trim()
    Add-Check 'redis_snapshot' ($taskRedisSize -match '^\d+$' -and [int]$taskRedisSize -gt 0) "dbsize=$taskRedisSize"

    $taskMediaVolume = "${taskProject}_media-data"
    & docker volume create $taskMediaVolume | Out-Null
    & docker run --rm -v "${taskMediaVolume}:/data" -v "${taskHostBackup}:/backup:ro" alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a sh -c 'rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true; tar -xzf /backup/media.tar.gz -C /data; chown -R 10001:10001 /data'
    if ($LASTEXITCODE -ne 0) { throw '媒体文件恢复失败。' }
    $taskMediaRaw = @(& docker run --rm -v "${taskMediaVolume}:/data:ro" alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a sh -c 'find /data -type f | wc -l; du -sb /data | cut -f1')
    $taskMediaStats = @("files=$($taskMediaRaw[0].Trim())", "bytes=$($taskMediaRaw[1].Trim())")
    $taskMediaMatch = (($taskMediaStats -join "`n").Trim() -eq ([string]$taskManifest.media).Trim())
    Add-Check 'media_files' $taskMediaMatch (($taskMediaStats -join '; '))

    Invoke-Compose @('up', '--build', '-d', '--wait') | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '恢复后的完整栈启动失败。' }
    $taskWebPort = $taskEnv['WEB_PORT']
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 90; $taskAttempt++) {
        try {
            $taskHealth = Invoke-WebRequest "http://127.0.0.1:$taskWebPort/healthz" -UseBasicParsing -TimeoutSec 2
            if ($taskHealth.StatusCode -eq 200) { $taskReady = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    Add-Check 'web_health' $taskReady "port=$taskWebPort"
    if ($taskReady) {
        $taskBase = "http://127.0.0.1:$taskWebPort/api"
        $taskCategories = Invoke-RestMethod "$taskBase/catalog/categories" -TimeoutSec 10
        Add-Check 'public_catalog' ([bool]$taskCategories.success -and @($taskCategories.data).Count -gt 0) ("categories=" + @($taskCategories.data).Count)

        $taskPhone = (Invoke-MysqlQuery 'SELECT u.phone FROM creator_user u LEFT JOIN purchase_order o ON o.user_id = u.id GROUP BY u.id, u.phone ORDER BY COUNT(o.id) DESC, u.id LIMIT 1;' | Select-Object -First 1).Trim()
        if ($taskPhone -match '^1[3-9][0-9]{9}$') {
            $taskCode = Invoke-RestMethod -Method Post -Uri "$taskBase/user/code?phone=$taskPhone" -TimeoutSec 10
            $taskDemoCode = $taskCode.data.demoCode
            $taskLoginBody = @{ phone = $taskPhone; code = $taskDemoCode } | ConvertTo-Json -Compress
            $taskLogin = Invoke-RestMethod -Method Post -Uri "$taskBase/user/login" -ContentType 'application/json' -Body $taskLoginBody -TimeoutSec 10
            $taskToken = [string]$taskLogin.data
            $taskOrders = Invoke-RestMethod -Uri "$taskBase/trade/orders?page=1&size=5" -Headers @{ authorization = $taskToken } -TimeoutSec 10
            Add-Check 'restored_login' ([bool]$taskLogin.success -and $taskToken.Length -gt 10) 'restored user demo login'
            Add-Check 'restored_orders' ([bool]$taskOrders.success) ("orders=" + @($taskOrders.data).Count)

            $taskProductId = (Invoke-MysqlQuery 'SELECT id FROM usage_product WHERE sale_mode = 0 AND status = 1 ORDER BY id LIMIT 1;' | Select-Object -First 1).Trim()
            if ($taskProductId -match '^\d+$') {
                $taskRequestKey = 'restore-check-' + (Get-Date -Format 'yyyyMMddHHmmss')
                $taskOrderBody = @{ productId = [long]$taskProductId; requestKey = $taskRequestKey } | ConvertTo-Json -Compress
                $taskCreated = Invoke-RestMethod -Method Post -Uri "$taskBase/trade/orders" -Headers @{ authorization = $taskToken } -ContentType 'application/json' -Body $taskOrderBody -TimeoutSec 15
                $taskOrderId = [string]$taskCreated.data.id
                $taskCreatedOk = [bool]$taskCreated.success -and $taskOrderId -match '^\d+$'
                Add-Check 'post_restore_order_create' $taskCreatedOk "orderId=$taskOrderId status=$($taskCreated.data.status)"
                if ($taskCreatedOk) {
                    $taskCancelled = Invoke-RestMethod -Method Post -Uri "$taskBase/trade/orders/$taskOrderId/cancel" -Headers @{ authorization = $taskToken } -TimeoutSec 15
                    Add-Check 'post_restore_order_cancel' ([bool]$taskCancelled.success -and $taskCancelled.data.status -eq 'CANCELLED') "status=$($taskCancelled.data.status)"
                }
            }
        } else {
            Add-Check 'restored_login' $false '未找到可登录的恢复用户；未执行恢复后写操作。'
        }
    }
} catch {
    $taskFailures.Add($_.Exception.Message)
} finally {
    $taskReport.finishedAt = (Get-Date).ToUniversalTime().ToString('o')
    $taskReport.failures = @($taskFailures)
    $taskReport.passed = ($taskFailures.Count -eq 0)
    $taskReportPath = Join-Path $taskBackupDirectory "restore-$Environment.json"
    $taskReport | ConvertTo-Json -Depth 6 | Out-File -FilePath $taskReportPath -Encoding utf8
    Write-Output "恢复验证报告：$taskReportPath"
}
if ($taskFailures.Count -gt 0) { throw ('恢复验证失败：' + ($taskFailures -join ', ')) }
