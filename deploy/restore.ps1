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
. (Join-Path $PSScriptRoot 'redis-snapshot.ps1')
$taskBackupDirectory = [System.IO.Path]::GetFullPath($BackupDirectory)
if (-not (Test-Path -LiteralPath $taskEnvFile)) { throw '目标隔离环境尚未初始化。' }
# 恢复前预检：清单、必需文件、大小与摘要全部通过后才允许修改目标环境或启动容器。
foreach ($taskRequired in @('manifest.json', 'database.sql', 'redis.rdb', 'media.tar.gz', 'counts.sql', 'flyway-history.txt')) {
    $taskRequiredPath = Join-Path $taskBackupDirectory $taskRequired
    if (-not (Test-Path -LiteralPath $taskRequiredPath -PathType Leaf)) { throw "备份缺少文件：$taskRequired" }
    if ((Get-Item -LiteralPath $taskRequiredPath).Length -le 0) { throw "备份文件为空：$taskRequired" }
}
try {
    $taskManifest = Get-Content -LiteralPath (Join-Path $taskBackupDirectory 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
    throw '备份清单无法解析。'
}
if ($null -eq $taskManifest) { throw '备份清单为空。' }
$taskFiles = $taskManifest.files
if ($null -eq $taskFiles) { throw '备份清单缺少文件摘要。' }
$taskLogical = $taskManifest.logical
if ($null -eq $taskLogical) { throw '备份清单缺少逻辑配置。' }
foreach ($taskName in @('namespace', 'redisPrefix', 'mqTopic', 'mqGroupPrefix', 'analyticsTopic', 'analyticsGroup')) {
    if ([string]$taskLogical.$taskName -notmatch '^[A-Za-z0-9._:-]{1,160}$') { throw "备份清单的逻辑配置不合法：$taskName" }
}
if ([string]$taskManifest.redis -notmatch '^\d+$') { throw '备份清单的 Redis 计数不合法。' }
if ([string]$taskManifest.media -notmatch '^files=\d+\s+bytes=\d+$') { throw '备份清单的媒体统计不合法。' }
if (([string]$taskManifest.counts).Trim().Length -eq 0) { throw '备份清单缺少业务计数。' }
function Test-ManifestFile([string]$Name, [bool]$Required) {
    $taskEntry = $taskFiles.PSObject.Properties[$Name]
    if ($null -eq $taskEntry) {
        if ($Required) { throw "备份清单缺少文件摘要：$Name" }
        return
    }
    $taskExpected = $taskEntry.Value
    if ($null -eq $taskExpected -or [string]$taskExpected.bytes -notmatch '^\d+$' -or [string]$taskExpected.sha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw "备份清单的文件摘要不完整：$Name"
    }
    $taskPath = Join-Path $taskBackupDirectory $Name
    if ((Get-Item -LiteralPath $taskPath).Length -ne [long]$taskExpected.bytes) { throw "备份文件大小不一致：$Name" }
    $taskHash = (Get-FileHash -LiteralPath $taskPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($taskHash -ne ([string]$taskExpected.sha256).ToLowerInvariant()) { throw "备份文件摘要不一致：$Name" }
}
foreach ($taskName in @('database.sql', 'redis.rdb', 'media.tar.gz')) { Test-ManifestFile $taskName $true }
foreach ($taskName in @('counts.sql', 'flyway-history.txt', 'redis-persistent.txt')) { Test-ManifestFile $taskName $false }
# 摘要预检仍最先执行；RDB 结构校验也在目标环境变更之前完成。
$taskRdb = Get-RdbSnapshotMetadata $taskBackupDirectory
$taskRdbRead = $taskRdb.keys
$taskRdbExpires = $taskRdb.expires
$taskExpectedRedis = [long]([string]$taskManifest.redis).Trim()
if ($null -ne $taskManifest.redisSnapshot) {
    $taskSnapshotMeta = $taskManifest.redisSnapshot
    if ([string]$taskSnapshotMeta.version -ne '1' -or
        [string]$taskSnapshotMeta.keys -notmatch '^\d+$' -or [string]$taskSnapshotMeta.expires -notmatch '^\d+$' -or
        [long]$taskSnapshotMeta.keys -ne $taskRdbRead -or [long]$taskSnapshotMeta.expires -ne $taskRdbExpires -or $taskExpectedRedis -ne $taskRdbRead) {
        throw 'Redis 固定快照元数据与 RDB 不一致。'
    }
    Test-ManifestFile 'redis-persistent.txt' $true
} elseif ($taskExpectedRedis -lt $taskRdbRead - $taskRdbExpires -or $taskExpectedRedis -gt $taskRdbRead) {
    # 旧清单使用稍后在线计数，只兼容 TTL 可解释的差额；永久键下界和 RDB 完整性不可放宽。
    throw "Redis 旧清单计数超出快照边界：expected=$taskExpectedRedis read=$taskRdbRead expires=$taskRdbExpires"
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
function Get-FirstValue([string]$Sql) {
    # 空快照查询不到行时返回空串，避免对 $null 调用 Trim 导致恢复失败。
    $taskValue = Invoke-MysqlQuery $Sql | Select-Object -First 1
    if ($null -eq $taskValue) { return '' }
    return ([string]$taskValue).Trim()
}
function Invoke-RedisCli([string[]]$RedisArgs) {
    Invoke-Compose (@('exec', '-T', '-e', "REDISCLI_AUTH=$taskRedisPassword", 'redis', 'redis-cli') + $RedisArgs)
}
function New-ProbeSessionToken {
    $taskBytes = New-Object byte[] 32
    $taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
    return -join ($taskBytes | ForEach-Object { $_.ToString('x2') })
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
    skipped = @()
    failures = @()
}
$taskFailures = New-Object System.Collections.Generic.List[string]
$taskSkipped = New-Object System.Collections.Generic.List[object]
function Add-Skip([string]$Name, [string]$Reason) {
    $taskSkipped.Add(@{ name = $Name; reason = $Reason })
    Write-Output "恢复检查跳过：$Name（$Reason）"
}
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
    $taskRedisVolumes = @(& docker volume ls --filter "label=com.docker.compose.project=$taskProject" --filter 'label=com.docker.compose.volume=redis-data' --format '{{.Name}}' | Where-Object { $_ })
    if ($taskRedisVolumes.Count -ne 1) { throw '目标 Redis 卷缺失或存在多个候选。' }
    $taskRedisVolume = $taskRedisVolumes[0]
    if ($taskRedisVolume -ne "${taskProject}_redis-data") { throw '目标 Redis 卷名称与当前项目不一致。' }
    $taskRedisMeta = @(& docker volume inspect $taskRedisVolume | ConvertFrom-Json)[0]
    if ([string]$taskRedisMeta.Name -ne $taskRedisVolume -or
        [string]$taskRedisMeta.Labels.'com.docker.compose.project' -ne $taskProject -or
        [string]$taskRedisMeta.Labels.'com.docker.compose.volume' -ne 'redis-data') { throw '目标 Redis 卷归属校验失败。' }
    $taskRedisUid = (& docker run --rm redis:7-alpine@sha256:858f009f9709ce576febc734aa78b8f6d624b82571f9ddb6bda4377c833b3499 id -u redis).Trim()
    $taskHostBackup = $taskBackupDirectory.Replace('\', '/')
    & docker run --rm -v "${taskRedisVolume}:/data" -v "${taskHostBackup}:/backup:ro" alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a sh -c "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true; cp /backup/redis.rdb /data/dump.rdb; chown -R ${taskRedisUid}:${taskRedisUid} /data"
    if ($LASTEXITCODE -ne 0) { throw 'Redis 快照恢复失败。' }
    # Redis 以 AOF 模式运行，先载入 RDB，再生成含数据的 AOF。
    $taskSeedName = 'ai-creation-redis-seed-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    & docker run -d --rm --network none --name $taskSeedName -v "${taskRedisVolume}:/data" $taskRedisImage redis-server --appendonly no --dir /data --dbfilename dump.rdb --requirepass $taskRedisPassword | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Redis 快照装载容器启动失败。' }
    try {
        Wait-RedisSnapshot $taskSeedName $taskRedisPassword
        $taskRedisLoaded = Test-RedisSnapshotLoad $taskSeedName $taskRedisPassword $taskRdb
        $taskSnapshotPersistent = @(Get-SnapshotPersistentKeys $taskSeedName $taskRedisPassword)
        if ($taskSnapshotPersistent.Count -ne $taskRdbRead - $taskRdbExpires) { throw 'Redis 永久键载入不完整。' }
        & docker exec $taskSeedName redis-cli --no-auth-warning -a $taskRedisPassword CONFIG SET appendonly yes | Out-Null
        $taskAofReady = $false
        for ($taskAttempt = 0; $taskAttempt -lt 60; $taskAttempt++) {
            $taskPersistence = (& docker exec $taskSeedName redis-cli --no-auth-warning -a $taskRedisPassword INFO persistence) -join "`n"
            if ($taskPersistence -match 'aof_enabled:1' -and $taskPersistence -match 'aof_rewrite_in_progress:0' -and $taskPersistence -match 'aof_last_bgrewrite_status:ok') { $taskAofReady = $true; break }
            Start-Sleep -Seconds 1
        }
    } finally {
        & docker stop $taskSeedName | Out-Null
    }
    if (-not $taskAofReady) { throw 'Redis 快照未能写入 AOF。' }
    $taskAofFiles = (& docker run --rm -v "${taskRedisVolume}:/data:ro" alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a sh -c 'find /data/appendonlydir -type f 2>/dev/null | wc -l' | Select-Object -First 1).Trim()
    if (-not ($taskAofFiles -match '^\d+$' -and [long]$taskAofFiles -ge 1)) { throw 'Redis 快照未生成 AOF 文件。' }
    Invoke-Compose @('up', '-d', 'redis') | Out-Null
    $taskRedisReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 60; $taskAttempt++) {
        $taskPong = (Invoke-RedisCli @('PING') | Select-Object -First 1)
        if (($taskPong -join '').Trim() -eq 'PONG') { $taskRedisReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $taskRedisReady) { throw '恢复后的 Redis 未就绪。' }
    $taskRedisSize = (Invoke-RedisCli @('DBSIZE') | Select-Object -First 1).Trim()
    $taskRedisPersistence = (Invoke-RedisCli @('INFO', 'persistence')) -join "`n"
    $taskFinalRedis = if ($taskRedisSize -match '^\d+$') { [long]$taskRedisSize } else { -1 }
    # 带 TTL 的键可能在恢复期间按原到期时间消失；永久键必须完整存在（由 persistent 清单单独核对）。
    $taskRedisSizeOk = ($taskFinalRedis -ge $taskRdbRead - $taskRdbExpires -and $taskFinalRedis -le $taskRedisLoaded)
    $taskRedisOk = ($taskFinalRedis -ge 0 -and $taskRedisSizeOk -and $taskRedisPersistence -match 'aof_enabled:1')
    Add-Check 'redis_snapshot' $taskRedisOk "expected=$taskExpectedRedis read=$taskRdbRead expires=$taskRdbExpires expiredAtCheck=$($taskRdb.expiredAtCheck) loaded=$taskRedisLoaded dbsize=$taskRedisSize aofEnabled=$($taskRedisPersistence -match 'aof_enabled:1')"

    # 即使旧备份没有永久键清单，也从真实 RDB 载入结果核对全部永久协调键。
    $taskSnapshotMissing = @()
    foreach ($taskKey in $taskSnapshotPersistent) {
        $taskKeyTtl = (Invoke-RedisCli @('PTTL', $taskKey) | Select-Object -First 1).Trim()
        if ($taskKeyTtl -ne '-1') { $taskSnapshotMissing += $taskKey }
    }
    Add-Check 'redis_coordination_keys' ($taskSnapshotMissing.Count -eq 0) ("persistent=" + $taskSnapshotPersistent.Count + " missing=" + $taskSnapshotMissing.Count)

    # 新格式备份记录全部永久键；恢复后逐键核对，证明快照中的长期状态没有丢失。
    $taskPersistentFile = Join-Path $taskBackupDirectory 'redis-persistent.txt'
    if (Test-Path -LiteralPath $taskPersistentFile) {
        $taskPersistentLines = @(Get-Content -LiteralPath $taskPersistentFile -Encoding UTF8 | Where-Object { $_ -ne '' })
        $taskPersistentOk = ($taskPersistentLines.Count -ge 1 -and $taskPersistentLines[0] -match '^count=(\d+)$')
        $taskPersistentCount = if ($taskPersistentOk) { [int]$Matches[1] } else { -1 }
        $taskPersistentExpectedKeys = if ($taskPersistentOk) { @($taskPersistentLines | Select-Object -Skip 1) } else { @() }
        $taskPersistentMissing = @()
        if ($taskPersistentOk -and $taskPersistentExpectedKeys.Count -ne $taskPersistentCount) { $taskPersistentOk = $false }
        if ($taskPersistentOk) {
            foreach ($taskKey in $taskPersistentExpectedKeys) {
                $taskKeyExists = (Invoke-RedisCli @('EXISTS', $taskKey) | Select-Object -First 1).Trim()
                if ($taskKeyExists -ne '1') { $taskPersistentMissing += $taskKey }
            }
            if ($taskPersistentMissing.Count -gt 0) { $taskPersistentOk = $false }
        }
        Add-Check 'redis_persistent_keys' $taskPersistentOk ("expected=" + $taskPersistentCount + " missing=" + $taskPersistentMissing.Count)
    } else {
        Add-Skip 'redis_persistent_keys' '旧格式备份未包含永久键清单。'
    }

    if ([string]$taskManifest.media -match 'files=(\d+)\s+bytes=(\d+)') {
        $taskExpectedMediaFiles = [long]$Matches[1]
        $taskExpectedMediaBytes = [long]$Matches[2]
    } else {
        throw '备份清单的媒体统计无法解析。'
    }
    $taskMediaVolume = "${taskProject}_media-data"
    # 用列表查询判断卷是否存在：docker volume inspect 在卷缺失时会写入 stderr，
    # 在 Stop 级别的脚本里会被当成失败，导致全新环境无法创建媒体卷。
    $taskMediaMatches = @(& docker volume ls --filter "name=^${taskMediaVolume}$" --format '{{.Name}}' | Where-Object { $_ })
    if ($taskMediaMatches.Count -gt 1) { throw '媒体卷候选不唯一，已拒绝恢复。' }
    if ($taskMediaMatches.Count -eq 0) {
        & docker volume create --label "com.docker.compose.project=$taskProject" --label 'com.docker.compose.volume=media-data' $taskMediaVolume | Out-Null
        if ($LASTEXITCODE -ne 0) { throw '媒体卷创建失败。' }
    } elseif ($taskMediaMatches[0] -ne $taskMediaVolume) {
        throw '媒体卷名称与当前项目不一致，已拒绝恢复。'
    }
    $taskMediaMeta = @(& docker volume inspect $taskMediaVolume | ConvertFrom-Json)[0]
    if ([string]$taskMediaMeta.Name -ne $taskMediaVolume -or
        [string]$taskMediaMeta.Labels.'com.docker.compose.project' -ne $taskProject -or
        [string]$taskMediaMeta.Labels.'com.docker.compose.volume' -ne 'media-data') { throw '媒体卷归属校验失败。' }
    Add-Check 'media_volume_identity' $true "volume=$taskMediaVolume project=$taskProject"
    & docker run --rm -v "${taskMediaVolume}:/data" -v "${taskHostBackup}:/backup:ro" alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a sh -c 'rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true; tar -xzf /backup/media.tar.gz -C /data; chown -R 10001:10001 /data'
    if ($LASTEXITCODE -ne 0) { throw '媒体文件恢复失败。' }
    $taskMediaRaw = @(& docker run --rm -v "${taskMediaVolume}:/data:ro" alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a sh -c 'find /data -type f | wc -l; du -sb /data | cut -f1')
    $taskActualMediaFiles = [long]$taskMediaRaw[0].Trim()
    $taskActualMediaBytes = if ($taskActualMediaFiles -eq 0) { 0 } else { [long]$taskMediaRaw[1].Trim() }
    if ($taskExpectedMediaFiles -eq 0) {
        $taskMediaMatch = ($taskActualMediaFiles -eq 0 -and $taskExpectedMediaBytes -eq 0)
    } else {
        $taskMediaMatch = ($taskActualMediaFiles -eq $taskExpectedMediaFiles -and $taskActualMediaBytes -eq $taskExpectedMediaBytes)
    }
    Add-Check 'media_files' $taskMediaMatch "expected=files=$taskExpectedMediaFiles bytes=$taskExpectedMediaBytes actual=files=$taskActualMediaFiles bytes=$taskActualMediaBytes"

    # 已登记为 ACTIVE 的媒体必须真实存在且大小与登记一致；零媒体快照不产生该行。
    $taskActiveMedia = @(Invoke-MysqlQuery "SELECT path, size_bytes FROM media_asset WHERE state = 'ACTIVE' ORDER BY id;" | Where-Object { $_ -ne '' })
    if ($taskActiveMedia.Count -gt 0) {
        # 通过脚本文件执行逐行核对，避免 PowerShell 5.1 重写原生命令参数中的嵌套引号。
        $taskMediaCheckDir = Join-Path $taskRoot ('.local\tmp\media-check-' + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $taskMediaCheckDir -Force | Out-Null
        $taskMediaCheckScript = Join-Path $taskMediaCheckDir 'check.sh'
        $taskMediaCheckBody = @(
            'missing=0'
            'bad=0'
            'sep=$(printf ''\t'')'
            'while IFS="$sep" read -r p s; do'
            '  [ -z "$p" ] && continue'
            '  s=$(printf ''%s'' "$s" | tr -d ''\r\n'')'
            '  if [ ! -f "/data/$p" ]; then'
            '    missing=$((missing+1))'
            '  elif [ "$(wc -c < "/data/$p" | tr -d '' '')" != "$s" ]; then'
            '    bad=$((bad+1))'
            '  fi'
            'done'
            'echo "$missing $bad"'
        ) -join "`n"
        [System.IO.File]::WriteAllText($taskMediaCheckScript, $taskMediaCheckBody + "`n", (New-Object System.Text.UTF8Encoding $false))
        $taskMediaCheckMount = $taskMediaCheckDir.Replace('\', '/')
        $taskMediaCheckOut = (($taskActiveMedia -join "`n") | & docker run --rm -i -v "${taskMediaVolume}:/data:ro" -v "${taskMediaCheckMount}:/check:ro" alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a sh /check/check.sh) -join ' '
        Remove-Item -LiteralPath $taskMediaCheckDir -Recurse -Force
        $taskMediaCheckParts = @($taskMediaCheckOut -split '\s+' | Where-Object { $_ -ne '' })
        $taskMediaRefsOk = ($taskMediaCheckParts.Count -ge 2 -and $taskMediaCheckParts[0] -eq '0' -and $taskMediaCheckParts[1] -eq '0')
        Add-Check 'media_references' $taskMediaRefsOk ("active=" + $taskActiveMedia.Count + " missing=" + $taskMediaCheckParts[0] + " sizeMismatch=" + $taskMediaCheckParts[1])
    } else {
        Add-Check 'media_references' $true 'active=0'
    }

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

        $taskProfile = [string]$taskEnv['APP_PROFILE']
        if ($taskProfile -eq 'release') {
            $taskSecurityDefaults = ([string]$taskEnv['APP_DEMO_CODE_ENABLED'] -eq 'false' -and
                [string]$taskEnv['APP_TRADE_MOCK_PAYMENT_ENABLED'] -eq 'false' -and
                [string]$taskEnv['APP_EXPERIENCE_ENABLED'] -eq 'false')
            Add-Check 'release_security_defaults' $taskSecurityDefaults "demo=$($taskEnv['APP_DEMO_CODE_ENABLED']) mockPayment=$($taskEnv['APP_TRADE_MOCK_PAYMENT_ENABLED']) experience=$($taskEnv['APP_EXPERIENCE_ENABLED'])"
        }

        $taskPhone = Get-FirstValue 'SELECT u.phone FROM creator_user u LEFT JOIN purchase_order o ON o.user_id = u.id GROUP BY u.id, u.phone ORDER BY COUNT(o.id) DESC, u.id LIMIT 1;'
        if ($taskProfile -eq 'release' -and $taskPhone -match '^1[3-9][0-9]{9}$') {
            try {
                Invoke-RestMethod -Method Post -Uri "$taskBase/user/code?phone=$taskPhone" -TimeoutSec 10 | Out-Null
                Add-Check 'demo_login_closed' $false 'release 环境仍返回演示验证码'
            } catch {
                $taskCodeStatus = 0
                if ($_.Exception.Response) { $taskCodeStatus = [int]$_.Exception.Response.StatusCode }
                Add-Check 'demo_login_closed' ($taskCodeStatus -eq 503 -or $taskCodeStatus -eq 404) "status=$taskCodeStatus"
            }
        } else {
            Add-Skip 'demo_login_closed' '仅在默认 release 环境检查演示验证码关闭。'
        }

        # 不开启演示登录或模拟支付：用临时 Redis 会话完成认证与写操作探针，结束后立即撤销。
        $taskUserId = Get-FirstValue "SELECT u.id FROM creator_user u JOIN creator_user_role ur ON ur.user_id = u.id AND ur.role_code = 'USER' ORDER BY u.id LIMIT 1;"
        if ($taskUserId -match '^\d+$') {
            $taskProbeToken = New-ProbeSessionToken
            $taskSessionKey = "$($taskLogical.redisPrefix)login:token:$taskProbeToken"
            try {
                Invoke-RedisCli @('HSET', $taskSessionKey, 'id', $taskUserId) | Out-Null
                if ($LASTEXITCODE -ne 0) { throw '恢复探针会话写入失败。' }
                Invoke-RedisCli @('EXPIRE', $taskSessionKey, '600') | Out-Null
                if ($LASTEXITCODE -ne 0) { throw '恢复探针会话有效期设置失败。' }
                $taskSessionExists = (Invoke-RedisCli @('EXISTS', $taskSessionKey) | Select-Object -First 1).Trim()
                Add-Check 'probe_session_created' ($taskSessionExists -eq '1') "userId=$taskUserId"

                $taskMe = $null
                $taskMeStatus = 200
                try {
                    $taskMe = Invoke-RestMethod -Uri "$taskBase/user/me" -Headers @{ authorization = $taskProbeToken } -TimeoutSec 10
                } catch {
                    if ($_.Exception.Response) { $taskMeStatus = [int]$_.Exception.Response.StatusCode } else { $taskMeStatus = -1 }
                }
                $taskMeOk = ($null -ne $taskMe -and [bool]$taskMe.success -and ([string]$taskMe.data.id -eq $taskUserId))
                Add-Check 'restored_login' $taskMeOk "userId=$taskUserId status=$taskMeStatus session=temporary-redis-session"

                if ($taskMeOk) {
                    $taskOrders = Invoke-RestMethod -Uri "$taskBase/trade/orders?page=1&size=5" -Headers @{ authorization = $taskProbeToken } -TimeoutSec 10
                    Add-Check 'restored_orders' ([bool]$taskOrders.success) ("orders=" + @($taskOrders.data).Count)

                    $taskProductId = Get-FirstValue 'SELECT id FROM usage_product WHERE sale_mode = 0 AND status = 1 ORDER BY id LIMIT 1;'
                    if ($taskProductId -match '^\d+$') {
                        $taskRequestKey = 'restore-check-' + (Get-Date -Format 'yyyyMMddHHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
                        $taskOrderBody = @{ productId = [long]$taskProductId; requestKey = $taskRequestKey } | ConvertTo-Json -Compress
                        $taskCreated = Invoke-RestMethod -Method Post -Uri "$taskBase/trade/orders" -Headers @{ authorization = $taskProbeToken } -ContentType 'application/json' -Body $taskOrderBody -TimeoutSec 15
                        $taskOrderId = [string]$taskCreated.data.id
                        $taskCreatedOk = [bool]$taskCreated.success -and $taskOrderId -match '^\d+$'
                        Add-Check 'post_restore_order_create' $taskCreatedOk "orderId=$taskOrderId status=$($taskCreated.data.status)"
                        if ($taskCreatedOk) {
                            $taskCancelled = Invoke-RestMethod -Method Post -Uri "$taskBase/trade/orders/$taskOrderId/cancel" -Headers @{ authorization = $taskProbeToken } -TimeoutSec 15
                            Add-Check 'post_restore_order_cancel' ([bool]$taskCancelled.success -and $taskCancelled.data.status -eq 'CANCELLED') "status=$($taskCancelled.data.status)"
                        }
                    } else {
                        Add-Skip 'post_restore_order_create' '恢复快照没有可售商品。'
                        Add-Skip 'post_restore_order_cancel' '恢复快照没有可售商品。'
                    }
                } else {
                    Add-Skip 'restored_orders' '会话认证未通过，未执行订单查询。'
                    Add-Skip 'post_restore_order_create' '会话认证未通过，未执行写操作。'
                    Add-Skip 'post_restore_order_cancel' '会话认证未通过，未执行写操作。'
                }
            } finally {
                # 探针会话只用于恢复验证，结束后立即撤销并确认令牌失效。
                Invoke-RedisCli @('DEL', $taskSessionKey) | Out-Null
                $taskRevoked = $false
                try {
                    Invoke-RestMethod -Uri "$taskBase/user/me" -Headers @{ authorization = $taskProbeToken } -TimeoutSec 10 | Out-Null
                } catch {
                    if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) { $taskRevoked = $true }
                }
                Add-Check 'probe_session_revoked' $taskRevoked 'temporary redis session deleted'
            }
        } else {
            Add-Skip 'restored_login' '快照没有带 USER 角色的用户，跳过认证探针。'
            Add-Skip 'restored_orders' '快照没有带 USER 角色的用户，跳过订单查询。'
            Add-Skip 'post_restore_order_create' '快照没有带 USER 角色的用户，跳过写操作。'
            Add-Skip 'post_restore_order_cancel' '快照没有带 USER 角色的用户，跳过写操作。'
        }
    }
} catch {
    $taskError = $_
    $taskFailures.Add("$($taskError.Exception.Message)（line $($taskError.InvocationInfo.ScriptLineNumber)）")
} finally {
    $taskReport.finishedAt = (Get-Date).ToUniversalTime().ToString('o')
    $taskReport.skipped = $taskSkipped.ToArray()
    $taskReport.failures = @($taskFailures)
    $taskReport.passed = ($taskFailures.Count -eq 0)
    $taskReportPath = Join-Path $taskBackupDirectory "restore-$Environment.json"
    $taskReport | ConvertTo-Json -Depth 6 | Out-File -FilePath $taskReportPath -Encoding utf8
    Write-Output "恢复验证报告：$taskReportPath"
}
if ($taskFailures.Count -gt 0) { throw ('恢复验证失败：' + ($taskFailures -join ', ')) }
