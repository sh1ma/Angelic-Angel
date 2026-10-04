param([string]$JavaPath = 'C:\Program Files\Java\jdk-17')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = $JavaPath
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
$env:ANDROID_HOME = Join-Path $projectRoot '.tools\android-sdk'
if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'JDK 17 is required. Pass -JavaPath.' }
if (-not (Test-Path (Join-Path $env:ANDROID_HOME 'platforms\android-37.0'))) { throw 'Install Android SDK Platform 37.0 first. See android/README.md.' }
Push-Location (Join-Path $projectRoot 'android')
try {
    & .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Android checks failed: $LASTEXITCODE" }
} finally { Pop-Location }
