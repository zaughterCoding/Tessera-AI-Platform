param(
    [ValidatePattern('^[a-z][a-z0-9-]{1,20}$')][string]$Environment = 'acceptance',
    [Parameter(Mandatory)][string]$Image
)
$ErrorActionPreference = 'Stop'
# 管道传给容器 stdin 时不带 BOM，否则 sh 会把 BOM 当成首条命令的一部分。
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEnvFile = Join-Path $taskRoot ".local\deploy\$Environment\.env"
$taskComposeFile = Join-Path $PSScriptRoot 'compose.yml'
if (-not (Test-Path -LiteralPath $taskEnvFile)) { throw '该部署环境尚未初始化。' }
& docker image inspect $Image --format '{{.Id}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '目标应用镜像不存在。' }

function Get-EnvMap([string]$Path) {
    $taskMap = @{}
    foreach ($taskLine in Get-Content -LiteralPath $Path) {
        if ($taskLine -match '^([A-Z0-9_]+)=(.*)$') { $taskMap[$Matches[1]] = $Matches[2] }
    }
    return $taskMap
}
$taskEnv = Get-EnvMap $taskEnvFile
$taskProject = $taskEnv['COMPOSE_PROJECT_NAME']
$taskComposeArgs = @('compose', '--env-file', $taskEnvFile, '-f', $taskComposeFile)
function Invoke-Compose([string[]]$CommandArgs) {
    & docker @taskComposeArgs @CommandArgs
}
function Get-FlywayHistory {
    # 逐参数传递查询，避免 PowerShell 5.1 丢弃原生参数里的引号。
    Invoke-Compose @('exec', '-T', '-e', "MYSQL_PWD=$($taskEnv['MYSQL_ROOT_PASSWORD'])", 'mysql', 'mysql', '-uroot', '-N', '-B', $taskEnv['MYSQL_DATABASE'], '-e', 'SELECT installed_rank, version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;')
}

$taskStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$taskEvidenceDirectory = Join-Path $taskRoot '.local\evidence'
New-Item -ItemType Directory -Path $taskEvidenceDirectory -Force | Out-Null
$taskReportPath = Join-Path $taskEvidenceDirectory "rollback-$Environment-$taskStamp.json"
$taskCurrentImage = "${taskProject}-app:local"
$taskPreviousTag = "${taskProject}-app:before-$taskStamp"
$taskBefore = Get-FlywayHistory
$taskReport = @{
    startedAt = (Get-Date).ToUniversalTime().ToString('o')
    environment = $Environment
    fromImage = $taskCurrentImage
    toImage = $Image
    flywayBefore = ($taskBefore -join "`n")
    steps = @()
    passed = $false
}
try {
    & docker tag $taskCurrentImage $taskPreviousTag
    if ($LASTEXITCODE -ne 0) { throw '无法保存当前应用镜像标签。' }
    & docker tag $Image $taskCurrentImage
    if ($LASTEXITCODE -ne 0) { throw '无法应用目标镜像标签。' }
    $taskReport.steps += "tagged $Image as $taskCurrentImage; previous kept as $taskPreviousTag"

    Invoke-Compose @('up', '-d', '--no-deps', '--force-recreate', 'app') | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '应用容器重建失败。' }
    $taskHealthy = $false
    for ($taskAttempt = 0; $taskAttempt -lt 90; $taskAttempt++) {
        $taskAppId = (& docker @taskComposeArgs @('ps', '-q', 'app') | Select-Object -First 1)
        if ($taskAppId) {
            $taskStatus = (& docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $taskAppId)
            if ($taskStatus -eq 'healthy') { $taskHealthy = $true; break }
            if ($taskStatus -eq 'unhealthy' -or $taskStatus -eq 'exited') { break }
        }
        Start-Sleep -Seconds 1
    }
    $taskReport.steps += "app health: $taskHealthy"
    if (-not $taskHealthy) {
        $taskLogs = Invoke-Compose @('logs', '--tail', '120', 'app')
        $taskReport.steps += ($taskLogs -join "`n")
        throw '目标应用镜像未能通过就绪检查。'
    }

    $taskWebPort = $taskEnv['WEB_PORT']
    $taskCatalog = Invoke-RestMethod "http://127.0.0.1:$taskWebPort/api/catalog/categories" -TimeoutSec 10
    if (-not $taskCatalog.success) { throw '回滚后公开目录不可用。' }
    $taskReport.steps += 'public catalog available after image switch'

    $taskAfter = Get-FlywayHistory
    $taskReport.flywayAfter = ($taskAfter -join "`n")
    if ((($taskBefore -join "`n").Trim()) -ne (($taskAfter -join "`n").Trim())) {
        throw '迁移历史在回滚过程中发生变化；应用回滚不应修改已应用迁移。'
    }
    $taskReport.steps += 'flyway history unchanged'
    $taskReport.passed = $true
} catch {
    $taskReport.error = $_.Exception.Message
} finally {
    $taskReport.finishedAt = (Get-Date).ToUniversalTime().ToString('o')
    $taskReport | ConvertTo-Json -Depth 5 | Out-File -FilePath $taskReportPath -Encoding utf8
    Write-Output "回滚验证报告：$taskReportPath"
    Write-Output "原应用镜像保留为：$taskPreviousTag"
}
if (-not $taskReport.passed) { throw '回滚验证失败，请查看报告与容器日志。' }
