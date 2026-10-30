$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskMavenHome = Join-Path $taskRoot '.local\maven'
if (-not $env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME = $taskMavenHome }
# 编译、测试与产品镜像统一使用 Java 21，避免宿主与运行时不匹配。
$taskJavaHome = if ($env:AI_PLATFORM_JAVA_HOME) { $env:AI_PLATFORM_JAVA_HOME } else { $env:JAVA_HOME }
if (-not $taskJavaHome) { throw '请设置 JAVA_HOME 或 AI_PLATFORM_JAVA_HOME 指向 JDK 21。' }
$taskJava = Join-Path $taskJavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $taskJava)) { throw "JDK 21 不存在：$taskJava" }
$taskJavaVersion = (& cmd /c "`"$taskJava`" -version 2>&1" | Select-Object -First 1)
if ($taskJavaVersion -notmatch 'version "21\.') { throw "构建需要 JDK 21，当前为：$taskJavaVersion" }
$env:JAVA_HOME = $taskJavaHome
Push-Location $taskRoot
try {
    & (Join-Path $taskRoot 'mvnw.cmd') -B "-Dmaven.repo.local=$taskMavenHome\repository" -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw '工程构建失败。' }
} finally {
    Pop-Location
}
