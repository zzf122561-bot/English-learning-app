param(
    [string[]] $Tasks = @(
        ':feature-01-word-memory:testDebugUnitTest',
        ':feature-01-word-memory:compileDebugAndroidTestKotlin',
        ':feature-02-dictionary:testDebugUnitTest',
        ':feature-02-dictionary:compileDebugAndroidTestKotlin',
        ':app:assembleRelease'
    ),
    [switch] $AllowDirty
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$toolchainRoot = [System.IO.Path]::GetFullPath('D:\CodexData\android-toolchain')
$jdkHome = Join-Path $toolchainRoot 'jdk-17'
$sdkRoot = Join-Path $toolchainRoot 'android-sdk'
$gradle = Join-Path $toolchainRoot 'gradle\gradle-9.5.0\bin\gradle.bat'
$hostsFile = Join-Path $PSScriptRoot 'gradle-hosts.txt'
$boundaryVerifier = Join-Path $PSScriptRoot 'verify-boundaries.ps1'
$signingProperties = Join-Path $projectRoot '_manager\local\signing.properties'

foreach ($requiredFile in @(
    (Join-Path $jdkHome 'bin\java.exe'),
    (Join-Path $sdkRoot 'platforms\android-36\android.jar'),
    $gradle,
    $hostsFile,
    $boundaryVerifier,
    $signingProperties
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

if (-not $AllowDirty) {
    $gitStatus = @(& git -C $projectRoot status --porcelain)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect Git status"
    }
    if ($gitStatus.Count -ne 0) {
        throw "Manager APK builds require a clean Git commit."
    }
}

& pwsh.exe -NoLogo -NoProfile -NonInteractive -File $boundaryVerifier
if ($LASTEXITCODE -ne 0) {
    throw "Architecture boundary verification failed with exit code $LASTEXITCODE"
}

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
