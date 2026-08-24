param(
    [string[]] $Tasks = @(
        ':feature-02-dictionary:testDebugUnitTest',
        ':feature-02-dictionary:compileDebugAndroidTestKotlin',
        ':feature-02-dictionary:assembleDebug'
    ),
    [switch] $RerunTasks
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$featureRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $featureRoot '..'))
$toolchainRoot = [System.IO.Path]::GetFullPath('D:\CodexData\android-toolchain')
$jdkHome = Join-Path $toolchainRoot 'jdk-17'
$sdkRoot = Join-Path $toolchainRoot 'android-sdk'
$gradle = Join-Path $toolchainRoot 'gradle\gradle-9.5.0\bin\gradle.bat'
$hostsFile = 'D:\Codex_Project\Codex_EnglishApp\_manager\tools\gradle-hosts.txt'

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
    $gradleArgs = @('--no-daemon', '--no-configuration-cache', '--configure-on-demand')
    if ($RerunTasks) { $gradleArgs += '--rerun-tasks' }
    & $gradle @gradleArgs @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Feature module tests failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

$unexpectedApks = @(Get-ChildItem -LiteralPath $featureRoot -Recurse -File -Filter '*.apk' -ErrorAction SilentlyContinue)
if ($unexpectedApks.Count -ne 0) {
    throw 'Feature module produced an APK, which violates the manager boundary.'
}

$androidTestSources = @(Get-ChildItem -LiteralPath (Join-Path $featureRoot 'src\androidTest') -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue)
if ($androidTestSources.Count -eq 0) {
    throw 'Milestone 2 requires real androidTest Kotlin source.'
}

$manifest = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $featureRoot 'src\main\AndroidManifest.xml')
if ($manifest -match '<activity|android.intent.category.LAUNCHER') {
    throw 'Feature library must not declare an Activity or launcher intent.'
}

$moduleBuild = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $featureRoot 'build.gradle.kts')
if ($moduleBuild -match 'com\.android\.application|feature-01-word-memory') {
    throw 'Feature module crossed the Application or Feature 1 boundary.'
}

$aars = @(Get-ChildItem -LiteralPath (Join-Path $featureRoot 'build\outputs\aar') -File -Filter '*.aar' -ErrorAction SilentlyContinue)
if ($aars.Count -eq 0) { throw 'Feature module did not produce an AAR.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($aar in $aars) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($aar.FullName)
    try {
        $nativeEntries = @($archive.Entries | Where-Object { $_.FullName -match '(^|/)(jni|lib)/|\.(so|dll|dylib)$' })
        if ($nativeEntries.Count -ne 0) { throw "AAR contains forbidden native/JNI entries: $($aar.Name)" }
    } finally {
        $archive.Dispose()
    }
}

Write-Output "FEATURE_MODULE_CHECK_PASSED artifact=AAR apk=0 native=0 androidTestSources=$($androidTestSources.Count)"
