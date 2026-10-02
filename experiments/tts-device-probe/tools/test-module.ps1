param([switch] $RerunTasks)
$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$env:JAVA_HOME = 'D:\CodexData\android-toolchain\jdk-17'
$env:ANDROID_HOME = 'D:\CodexData\android-toolchain\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$hostsPath = (Join-Path $repoRoot '_manager/tools/gradle-hosts.txt').Replace('\', '/')
$env:GRADLE_OPTS = "-Xmx3g -Dfile.encoding=UTF-8 -Djdk.net.hosts.file=$hostsPath"
$gradleArgs = @(
    '-p', (Join-Path $repoRoot '_manager/probes/tts-device'),
    '--no-daemon', '--no-configuration-cache',
    ':runtime:testDebugUnitTest', ':runtime:compileDebugAndroidTestKotlin', ':runtime:assembleRelease'
)
if ($RerunTasks) { $gradleArgs += '--rerun-tasks' }
& 'D:\CodexData\android-toolchain\gradle\gradle-9.5.0\bin\gradle.bat' @gradleArgs
if ($LASTEXITCODE -ne 0) { throw "Probe module build failed with exit code $LASTEXITCODE" }
