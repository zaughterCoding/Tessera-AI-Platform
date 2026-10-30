# 固定快照的结构统计不随 TTL 到期变化，所有计数校验均以此为边界。
$taskRedisImage = 'redis:7-alpine@sha256:858f009f9709ce576febc734aa78b8f6d624b82571f9ddb6bda4377c833b3499'
function Get-RdbSnapshotMetadata([string]$Directory) {
    $taskMount = ([System.IO.Path]::GetFullPath($Directory)).Replace('\', '/')
    $taskOutput = @(& docker run --rm --network none -v "${taskMount}:/snapshot:ro" $taskRedisImage redis-check-rdb /snapshot/redis.rdb)
    if ($LASTEXITCODE -ne 0) { throw 'Redis 快照校验失败。' }
    $taskText = $taskOutput -join "`n"
    if ($taskText -notmatch 'Checksum OK' -or $taskText -notmatch 'RDB looks OK') { throw 'Redis 快照未通过完整性校验。' }
    $taskStats = @{}
    foreach ($taskField in @('keys read', 'expires', 'already expired')) {
        if ($taskText -notmatch ('(?m)^\[info\] (\d+) ' + $taskField + '\r?$')) { throw "Redis 快照统计缺失：$taskField" }
        $taskStats[$taskField] = [long]$Matches[1]
    }
    if ($taskStats['expires'] -gt $taskStats['keys read'] -or $taskStats['already expired'] -gt $taskStats['expires']) { throw 'Redis 快照统计不合法。' }
    return @{ keys = $taskStats['keys read']; expires = $taskStats['expires']; expiredAtCheck = $taskStats['already expired'] }
}
function Invoke-SnapshotRedisCli([string]$Container, [string]$Password, [string[]]$RedisArgs) {
    $taskAuth = if ($Password) { @('-e', "REDISCLI_AUTH=$Password") } else { @() }
    $taskResult = @(& docker exec @taskAuth $Container redis-cli --no-auth-warning @RedisArgs)
    if ($LASTEXITCODE -ne 0) { throw 'Redis 快照实例命令失败。' }
    return $taskResult
}
function Wait-RedisSnapshot([string]$Container, [string]$Password) {
    for ($taskWait = 0; $taskWait -lt 60; $taskWait++) {
        try {
            $taskPong = Invoke-SnapshotRedisCli $Container $Password @('PING')
            if (($taskPong -join '').Trim() -eq 'PONG') { return }
        } catch { }
        Start-Sleep -Seconds 1
    }
    throw 'Redis 快照装载实例未就绪。'
}
function Test-RedisSnapshotLoad([string]$Container, [string]$Password, $Metadata) {
    $taskInfo = (Invoke-SnapshotRedisCli $Container $Password @('INFO', 'persistence')) -join "`n"
    # 本次载入的统计是固定值；不能拿检查器先前的过期数与稍后的 DBSIZE 严格比较。
    $taskLoad = @{}
    foreach ($taskField in @('rdb_last_load_keys_loaded', 'rdb_last_load_keys_expired')) {
        if ($taskInfo -notmatch ('(?m)^' + $taskField + ':(\d+)\r?$')) { throw "Redis 载入统计缺失：$taskField" }
        $taskLoad[$taskField] = [long]$Matches[1]
    }
    $taskLoaded = $taskLoad['rdb_last_load_keys_loaded']
    $taskExpired = $taskLoad['rdb_last_load_keys_expired']
    if ($taskLoaded + $taskExpired -ne $Metadata.keys -or $taskExpired -gt $Metadata.expires) { throw 'Redis 快照载入统计与固定 RDB 不一致。' }
    return $taskLoaded
}
function Get-SnapshotPersistentKeys([string]$Container, [string]$Password) {
    # 临时实例没有业务写入；永久键从固定 RDB 载入，SCAN 去重且不将 TTL 键当成永久协调键。
    $taskLua = "local seen = {}; local result = {}; local cursor = '0'; repeat local page = redis.call('SCAN', cursor); cursor = page[1]; for _, key in ipairs(page[2]) do if not seen[key] and redis.call('PTTL', key) == -1 then seen[key] = true; table.insert(result, key); end; end; until cursor == '0'; return result"
    $taskJson = (Invoke-SnapshotRedisCli $Container $Password @('--json', 'EVAL', $taskLua, '0')) -join "`n"
    return ($taskJson | ConvertFrom-Json)
}
