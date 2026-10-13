param(
    [string[]] $Tasks = @(
        ':app:testDebugUnitTest',
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
$dictionaryAssetPreparer = Join-Path $PSScriptRoot 'prepare-dictionary-assets.ps1'
$speechAssetPreparer = Join-Path $PSScriptRoot 'prepare-speech-assets.ps1'
$signingProperties = Join-Path $projectRoot '_manager\local\signing.properties'

foreach ($requiredFile in @(
    (Join-Path $jdkHome 'bin\java.exe'),
    (Join-Path $sdkRoot 'platforms\android-36\android.jar'),
    $gradle,
    $hostsFile,
    $boundaryVerifier,
    $dictionaryAssetPreparer,
    $speechAssetPreparer,
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
$sourceCommit = (& git -C $projectRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot identify build source' }

& pwsh.exe -NoLogo -NoProfile -NonInteractive -File $dictionaryAssetPreparer
if ($LASTEXITCODE -ne 0) {
    throw "Dictionary asset preparation failed with exit code $LASTEXITCODE"
}

& pwsh.exe -NoLogo -NoProfile -NonInteractive -File $boundaryVerifier
if ($LASTEXITCODE -ne 0) {
    throw "Architecture boundary verification failed with exit code $LASTEXITCODE"
}

& pwsh.exe -NoLogo -NoProfile -NonInteractive -File $speechAssetPreparer
if ($LASTEXITCODE -ne 0) { throw 'Speech asset preparation failed' }

$gradleArguments = @('--no-daemon', '--no-configuration-cache', '--max-workers=2') + $Tasks
$previousJavaToolOptions = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'Process')
Push-Location -LiteralPath $projectRoot
try {
    # Gradle may fork a fresh JVM before forwarding ordinary system properties. Initialize
    # the existing project-local resolver at JVM startup, without changing global settings.
    $env:JAVA_TOOL_OPTIONS = "-Djdk.net.hosts.file=$javaHostsPath"
    & $gradle @gradleArguments
    $gradleExitCode = $LASTEXITCODE
    if ($gradleExitCode -ne 0) {
        throw "Android build failed with exit code $gradleExitCode"
    }
} finally {
    if ($null -eq $previousJavaToolOptions) {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
    }
    Pop-Location
}

if ($Tasks -contains ':app:assembleRelease' -and -not $AllowDirty) {
    $afterStatus = @(& git -C $projectRoot status --porcelain)
    if ($LASTEXITCODE -ne 0 -or $afterStatus.Count -ne 0 -or (& git -C $projectRoot rev-parse HEAD).Trim() -ne $sourceCommit) {
        throw 'Checkout changed during release build; do not archive.'
    }
    $apk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
    $receipt = [ordered]@{
        sourceCommit = $sourceCommit
        apkPath = $apk
        apkSha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
        completedUtc = [DateTime]::UtcNow.ToString('o')
        tasks = $Tasks
        clean = $true
    }
    $receipt | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $projectRoot '_manager/local/build-app-receipt.json') -Encoding utf8NoBOM
    'MANAGER_BUILD_RECEIPT_READY: signed APK still requires asset/signature/archive checks'
}
