param(
    [string[]] $Tasks = @('testDebugUnitTest', 'assembleDebug')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$toolchainRoot = [System.IO.Path]::GetFullPath('D:\CodexData\android-toolchain')
$jdkHome = Join-Path $toolchainRoot 'jdk-17'
$sdkRoot = Join-Path $toolchainRoot 'android-sdk'
$gradle = Join-Path $toolchainRoot 'gradle\gradle-9.5.0\bin\gradle.bat'
$hostsFile = Join-Path $PSScriptRoot 'gradle-hosts.txt'

foreach ($requiredFile in @(
    (Join-Path $jdkHome 'bin\java.exe'),
    (Join-Path $sdkRoot 'platforms\android-36\android.jar'),
    $gradle,
    $hostsFile
)) {
    if (-not (Test-Path -LiteralPath $requiredFile)) {
        throw "Missing required build tool: $requiredFile"
    }
}

$env:JAVA_HOME = $jdkHome
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_HOME = $sdkRoot
$env:Path = (Join-Path $jdkHome 'bin') + [System.IO.Path]::PathSeparator + $env:Path
$javaHostsPath = $hostsFile.Replace('\', '/')
$env:GRADLE_OPTS = "-Xmx3g -Dfile.encoding=UTF-8 -Djdk.net.hosts.file=$javaHostsPath -Dorg.gradle.internal.http.connectionTimeout=60000 -Dorg.gradle.internal.http.socketTimeout=60000"

$gradleArguments = @('--no-daemon', '--no-configuration-cache') + $Tasks
Push-Location -LiteralPath $projectRoot
try {
    & $gradle @gradleArguments
    $gradleExitCode = $LASTEXITCODE
    if ($gradleExitCode -ne 0) {
        throw "Android build failed with exit code $gradleExitCode"
    }
} finally {
    Pop-Location
}
