$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskMavenHome = Join-Path $taskRoot '.local\maven'
if (-not $env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME = $taskMavenHome }
Push-Location $taskRoot
try {
    & (Join-Path $taskRoot 'mvnw.cmd') -B "-Dmaven.repo.local=$taskMavenHome\repository" -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw '工程构建失败。' }
} finally {
    Pop-Location
}
