param(
    [string[]] $Tasks = @(
        ':feature-01-word-memory:testDebugUnitTest',
        ':feature-01-word-memory:compileDebugAndroidTestKotlin',
        ':feature-01-word-memory:assembleDebug'
    )
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$featureRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $featureRoot '..'))
$toolchainRoot = [System.IO.Path]::GetFullPath('D:\CodexData\android-toolchain')
$jdkHome = Join-Path $toolchainRoot 'jdk-17'
$sdkRoot = Join-Path $toolchainRoot 'android-sdk'
$gradle = Join-Path $toolchainRoot 'gradle\gradle-9.5.0\bin\gradle.bat'
$hostsFile = Join-Path $projectRoot '_manager\tools\gradle-hosts.txt'

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

Push-Location -LiteralPath $projectRoot
try {
    & $gradle @('--no-daemon', '--no-configuration-cache') @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Feature module tests failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

$unexpectedApks = @(Get-ChildItem -LiteralPath $featureRoot -Recurse -File -Filter '*.apk' -ErrorAction SilentlyContinue)
if ($unexpectedApks.Count -ne 0) {
    throw 'Feature module produced an APK, which violates the manager boundary.'
}

Write-Output 'FEATURE_MODULE_CHECK_PASSED artifact=AAR apk=0'
