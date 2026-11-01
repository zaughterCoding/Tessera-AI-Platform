param(
    [ValidatePattern('^[a-z][a-z0-9-]{1,20}$')][string]$Environment = 'release'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEnvFile = Join-Path $taskRoot ".local\deploy\$Environment\.env"
if (-not (Test-Path -LiteralPath $taskEnvFile)) { throw '请先运行 deploy/init-env.ps1 -Demo 并启动环境。' }
$taskSettings = @{}
Get-Content -LiteralPath $taskEnvFile | Where-Object { $_ -match '^[A-Z0-9_]+=' } | ForEach-Object {
    $taskPair = $_ -split '=', 2
    $taskSettings[$taskPair[0]] = $taskPair[1]
}
if ($taskSettings['APP_GENERATION_SUBMIT_ENABLED'] -ne 'true') { throw '当前环境不是演示模式，请用 deploy/init-env.ps1 -Demo 创建。' }
$taskProject = $taskSettings['COMPOSE_PROJECT_NAME']
$taskDemoDirectory = Join-Path $PSScriptRoot 'demo'
$taskNodeImage = 'node:24.22.2-alpine@sha256:8a32c8cf649a49915a66e81e7a609223c708d0adabf288ee3f4077d765542f85'

function Invoke-Seed([string]$Command) {
    # 在容器里运行，本机不需要安装 Node。
    & docker run --rm --network "${taskProject}_default" -v "${taskDemoDirectory}:/seed:ro" $taskNodeImage node /seed/seed.mjs $Command
    if ($LASTEXITCODE -ne 0) { throw "演示数据步骤失败：$Command" }
}

$taskAdminId = (Invoke-Seed 'admin-login' | Select-Object -Last 1).Trim()
if ($taskAdminId -notmatch '^\d+$') { throw '未能取得演示管理员编号。' }
# 后端没有默认管理员，只在演示环境给固定账号授予角色。
$taskGrant = "insert ignore into creator_user_role (user_id, role_code) values ($taskAdminId, 'ADMIN')"
$taskGrant | & docker exec -i "$taskProject-mysql-1" sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot $MYSQL_DATABASE'
if ($LASTEXITCODE -ne 0) { throw '授予演示管理员角色失败。' }
Invoke-Seed 'seed'
Write-Output '演示账号：管理员 13800000000，创作者 13800000001~3，验证码直接显示在登录页。'
