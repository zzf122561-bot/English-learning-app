param([switch] $RerunTasks, [string[]] $Tests = @(),
    [string[]] $Tasks = @(':runtime:testDebugUnitTest', ':runtime:compileDebugAndroidTestKotlin', ':runtime:assembleRelease', ':runtime:lintDebug'))
$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$env:JAVA_HOME = 'D:\CodexData\android-toolchain\jdk-17'
$env:ANDROID_HOME = 'D:\CodexData\android-toolchain\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$hostsPath = (Join-Path $repoRoot '_manager/tools/gradle-hosts.txt').Replace('\', '/')
$env:GRADLE_OPTS = "-Xmx3g -Dfile.encoding=UTF-8 -Djdk.net.hosts.file=$hostsPath"
$gradleArgs = @(
    '-p', (Join-Path $repoRoot '_manager/probes/tts-device'),
    '--no-daemon', '--no-configuration-cache', '--max-workers=2'
)
foreach ($task in $Tasks) {
    if ($task -notmatch '^:runtime:[A-Za-z]+$') { throw 'Only this Library task is allowed' }
    $gradleArgs += $task
    if ($task -eq ':runtime:testDebugUnitTest') { foreach ($test in $Tests) { $gradleArgs += @('--tests', $test) } }
}
if ($RerunTasks) { $gradleArgs += '--rerun-tasks' }
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = '-Djdk.net.hosts.file=D:/Codex_Project/Codex_EnglishApp/_manager/tools/gradle-hosts.txt'
    & 'D:\CodexData\android-toolchain\gradle\gradle-9.5.0\bin\gradle.bat' @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Probe module build failed with exit code $LASTEXITCODE" }
} finally {
    if ($null -eq $previousJavaToolOptions) { Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue }
    else { $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions }
}
