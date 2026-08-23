$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$appBuild = Join-Path $projectRoot 'app\build.gradle.kts'
$appManifest = Join-Path $projectRoot 'app\src\main\AndroidManifest.xml'
$featureDirs = @(Get-ChildItem -LiteralPath $projectRoot -Directory -Filter 'feature-*')

if (-not (Test-Path -LiteralPath $appBuild) -or -not (Test-Path -LiteralPath $appManifest)) {
    throw 'The manager-owned App shell is incomplete.'
}

$applicationModules = 0
$rootBuild = Get-Content -LiteralPath (Join-Path $projectRoot 'build.gradle.kts') -Raw
$appBuildText = Get-Content -LiteralPath $appBuild -Raw
if ($appBuildText -match 'android\.application') { $applicationModules++ }

foreach ($featureDir in $featureDirs) {
    $buildFile = Join-Path $featureDir.FullName 'build.gradle.kts'
    $manifestFile = Join-Path $featureDir.FullName 'src\main\AndroidManifest.xml'
    if (-not (Test-Path -LiteralPath $buildFile)) { throw "Missing feature build file: $buildFile" }
    $buildText = Get-Content -LiteralPath $buildFile -Raw
    if ($buildText -match 'android\.application') { $applicationModules++ }
    if ($buildText -notmatch 'android\.library') { throw "Feature is not an Android Library: $($featureDir.Name)" }
    foreach ($forbidden in @('applicationId', 'versionCode', 'versionName', 'signingConfig')) {
        if ($buildText -match $forbidden) { throw "Forbidden feature build token '$forbidden': $($featureDir.Name)" }
    }
    if ($buildText -match 'project\(\s*"?:feature-') {
        throw "Feature-to-feature dependency is forbidden: $($featureDir.Name)"
    }
    if (Test-Path -LiteralPath $manifestFile) {
        $manifestText = Get-Content -LiteralPath $manifestFile -Raw
        if ($manifestText -match 'android\.intent\.action\.MAIN' -or $manifestText -match 'android\.intent\.category\.LAUNCHER') {
            throw "Feature manifest declares a launcher: $($featureDir.Name)"
        }
    }
    $featureApks = @(Get-ChildItem -LiteralPath $featureDir.FullName -Recurse -File -Filter '*.apk' -ErrorAction SilentlyContinue)
    if ($featureApks.Count -ne 0) { throw "APK found inside feature directory: $($featureDir.Name)" }
}

if ($applicationModules -ne 1) {
    throw "Expected exactly one Android Application module, found $applicationModules."
}
if ($rootBuild -notmatch 'android\.application' -or $rootBuild -notmatch 'android\.library') {
    throw 'Root plugin declarations are incomplete.'
}

Write-Output "BOUNDARY_CHECK_PASSED applicationModules=1 featureModules=$($featureDirs.Count)"
