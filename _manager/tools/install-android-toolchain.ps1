$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$toolchainRoot = [System.IO.Path]::GetFullPath('D:\CodexData\android-toolchain')
$expectedRoot = [System.IO.Path]::GetFullPath('D:\CodexData')
if (-not $toolchainRoot.StartsWith($expectedRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unexpected toolchain target: $toolchainRoot"
}

$downloadRoot = Join-Path $toolchainRoot 'downloads'
$sdkRoot = Join-Path $toolchainRoot 'android-sdk'
$jdkHome = Join-Path $toolchainRoot 'jdk-17'
$gradleRoot = Join-Path $toolchainRoot 'gradle'
$gradleHome = Join-Path $gradleRoot 'gradle-9.5.0'

foreach ($directory in @($toolchainRoot, $downloadRoot, $sdkRoot, $gradleRoot)) {
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }
}

$curl = (Get-Command 'curl.exe' -ErrorAction Stop).Source

function Get-OfficialFile {
    param(
        [Parameter(Mandatory)] [string] $Uri,
        [Parameter(Mandatory)] [string] $Destination
    )

    if (Test-Path -LiteralPath $Destination) {
        return
    }

    $partialDestination = $Destination + '.partial-' + [Guid]::NewGuid().ToString('N')
    $curlArgs = @(
        '-L'
        '--fail'
        '--retry'
        '3'
        '--retry-delay'
        '2'
        '--show-error'
        '--output'
        $partialDestination
        $Uri
    )
    & $curl @curlArgs
    $curlExitCode = $LASTEXITCODE
    if ($curlExitCode -ne 0) {
        throw "Download failed with exit code ${curlExitCode}: $Uri"
    }
    Move-Item -LiteralPath $partialDestination -Destination $Destination
}

function Assert-FileHash {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Algorithm,
        [Parameter(Mandatory)] [string] $Expected
    )

    $actual = (Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash
    if ($actual -ne $Expected.Trim().ToUpperInvariant()) {
        throw "Hash mismatch for $Path. Expected $Expected, got $actual."
    }
}

function Invoke-CheckedNative {
    param(
        [Parameter(Mandatory)] [string] $FilePath,
        [Parameter(Mandatory)] [string[]] $ArgumentList
    )

    & $FilePath @ArgumentList
    $nativeExitCode = $LASTEXITCODE
    if ($nativeExitCode -ne 0) {
        throw "$FilePath failed with exit code $nativeExitCode"
    }
}

function Get-AndroidPackageArchive {
    param(
        [Parameter(Mandatory)] [System.Xml.XmlDocument] $Repository,
        [Parameter(Mandatory)] [string] $PackagePath
    )

    $packageNodes = $Repository.SelectNodes("//*[local-name()='remotePackage' and @path='$PackagePath']")
    if ($packageNodes.Count -eq 0) {
        throw "Android repository metadata did not contain $PackagePath."
    }
    $compatibleArchives = @(
        foreach ($packageNode in $packageNodes) {
            $packageNode.SelectNodes(".//*[local-name()='archive']") |
                Where-Object {
                    $hostOsNode = $_.SelectSingleNode("./*[local-name()='host-os']")
                    $null -eq $hostOsNode -or $hostOsNode.InnerText -eq 'windows'
                }
        }
    )
    $archiveNode = if ($PackagePath.StartsWith('platforms;', [System.StringComparison]::Ordinal)) {
        $compatibleArchives |
            Where-Object {
                $urlNode = $_.SelectSingleNode("./*[local-name()='complete']/*[local-name()='url']")
                $null -ne $urlNode -and $urlNode.InnerText -notmatch '-ext\d+_'
            } |
            Select-Object -First 1
    } else {
        $compatibleArchives | Select-Object -First 1
    }
    if ($null -eq $archiveNode) {
        throw "Android repository metadata did not contain a compatible archive for $PackagePath."
    }
    $completeNode = $archiveNode.SelectSingleNode("./*[local-name()='complete']")
    [PSCustomObject]@{
        Uri = 'https://dl.google.com/android/repository/' + $completeNode.SelectSingleNode("./*[local-name()='url']").InnerText
        Checksum = $completeNode.SelectSingleNode("./*[local-name()='checksum']").InnerText.Trim()
    }
}

function Install-VerifiedAndroidArchive {
    param(
        [Parameter(Mandatory)] [string] $Uri,
        [Parameter(Mandatory)] [string] $Checksum,
        [Parameter(Mandatory)] [string] $DownloadPath,
        [Parameter(Mandatory)] [string] $TargetPath,
        [Parameter(Mandatory)] [string] $SentinelFile
    )

    $targetSentinel = Join-Path $TargetPath $SentinelFile
    if (Test-Path -LiteralPath $targetSentinel) {
        return
    }
    if (Test-Path -LiteralPath $TargetPath) {
        throw "Android SDK target exists but is incomplete: $TargetPath"
    }

    Get-OfficialFile -Uri $Uri -Destination $DownloadPath
    $algorithm = switch ($Checksum.Length) {
        40 { 'SHA1' }
        64 { 'SHA256' }
        default { throw "Unsupported Android package checksum length: $($Checksum.Length)" }
    }
    Assert-FileHash -Path $DownloadPath -Algorithm $algorithm -Expected $Checksum

    $archiveStage = Join-Path $toolchainRoot ("stage-android-package-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $archiveStage | Out-Null
    Expand-Archive -LiteralPath $DownloadPath -DestinationPath $archiveStage
    $sentinel = Get-ChildItem -LiteralPath $archiveStage -Filter $SentinelFile -File -Recurse | Select-Object -First 1
    if ($null -eq $sentinel) {
        throw "Verified Android archive did not contain $SentinelFile."
    }
    $extractedHome = $sentinel.Directory.FullName
    $resolvedArchiveStage = [System.IO.Path]::GetFullPath($archiveStage)
    $resolvedExtractedHome = [System.IO.Path]::GetFullPath($extractedHome)
    if (-not $resolvedExtractedHome.StartsWith($resolvedArchiveStage + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Extracted Android package escaped staging directory: $resolvedExtractedHome"
    }
    $targetParent = Split-Path -Parent $TargetPath
    if (-not (Test-Path -LiteralPath $targetParent)) {
        New-Item -ItemType Directory -Path $targetParent | Out-Null
    }
    Move-Item -LiteralPath $resolvedExtractedHome -Destination $TargetPath
    if (-not (Test-Path -LiteralPath $targetSentinel)) {
        throw "Android package installation did not produce $targetSentinel."
    }
}

if (-not (Test-Path -LiteralPath (Join-Path $jdkHome 'bin\java.exe'))) {
    if (Test-Path -LiteralPath $jdkHome) {
        throw "JDK target exists but is incomplete: $jdkHome"
    }

    $jdkMetadataPath = Join-Path $downloadRoot 'temurin-17-metadata.json'
    $jdkMetadataUri = 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&vendor=eclipse'
    Get-OfficialFile -Uri $jdkMetadataUri -Destination $jdkMetadataPath
    $jdkMetadata = Get-Content -LiteralPath $jdkMetadataPath -Raw | ConvertFrom-Json
    $jdkPackage = $jdkMetadata[0].binary.package
    if ($null -eq $jdkPackage.link -or $null -eq $jdkPackage.checksum) {
        throw 'Adoptium metadata did not contain a package link and checksum.'
    }

    $jdkZip = Join-Path $downloadRoot 'temurin-17-jdk.zip'
    Get-OfficialFile -Uri $jdkPackage.link -Destination $jdkZip
    Assert-FileHash -Path $jdkZip -Algorithm 'SHA256' -Expected $jdkPackage.checksum

    $jdkStage = Join-Path $toolchainRoot ("stage-jdk-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $jdkStage | Out-Null
    Expand-Archive -LiteralPath $jdkZip -DestinationPath $jdkStage
    $javaExecutable = Get-ChildItem -LiteralPath $jdkStage -Filter 'java.exe' -File -Recurse | Select-Object -First 1
    if ($null -eq $javaExecutable -or $javaExecutable.Directory.Name -ne 'bin') {
        throw 'Could not locate bin\java.exe in the verified JDK archive.'
    }
    $extractedJdkHome = $javaExecutable.Directory.Parent.FullName
    $resolvedStage = [System.IO.Path]::GetFullPath($jdkStage)
    $resolvedExtractedJdk = [System.IO.Path]::GetFullPath($extractedJdkHome)
    if (-not $resolvedExtractedJdk.StartsWith($resolvedStage + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Extracted JDK escaped staging directory: $resolvedExtractedJdk"
    }
    Move-Item -LiteralPath $resolvedExtractedJdk -Destination $jdkHome
}

$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome 'bin') + [System.IO.Path]::PathSeparator + $env:Path

$repositoryXmlPath = Join-Path $downloadRoot 'android-repository2-1.xml'
Get-OfficialFile -Uri 'https://dl.google.com/android/repository/repository2-1.xml' -Destination $repositoryXmlPath
[xml] $repositoryXml = Get-Content -LiteralPath $repositoryXmlPath -Raw

$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
    $commandLinePackage = $repositoryXml.SelectSingleNode("//*[local-name()='remotePackage' and @path='cmdline-tools;latest']")
    if ($null -eq $commandLinePackage) {
        throw 'Android repository metadata did not contain cmdline-tools;latest.'
    }
    $windowsArchive = $commandLinePackage.SelectNodes(".//*[local-name()='archive']") |
        Where-Object { $_.SelectSingleNode("./*[local-name()='host-os']").InnerText -eq 'windows' } |
        Select-Object -First 1
    if ($null -eq $windowsArchive) {
        throw 'Android repository metadata did not contain a Windows command-line tools archive.'
    }
    $completeNode = $windowsArchive.SelectSingleNode("./*[local-name()='complete']")
    $archiveUrl = $completeNode.SelectSingleNode("./*[local-name()='url']").InnerText
    $checksumNode = $completeNode.SelectSingleNode("./*[local-name()='checksum']")
    $checksumType = $checksumNode.GetAttribute('type')
    $checksumAlgorithm = if ([string]::IsNullOrWhiteSpace($checksumType)) {
        switch ($checksumNode.InnerText.Trim().Length) {
            40 { 'SHA1' }
            64 { 'SHA256' }
            default { throw 'Android repository checksum has no type and an unsupported length.' }
        }
    } else {
        $checksumType.ToUpperInvariant()
    }
    if ($checksumAlgorithm -eq 'SHA-1') { $checksumAlgorithm = 'SHA1' }

    $commandLineZip = Join-Path $downloadRoot 'android-command-line-tools.zip'
    Get-OfficialFile -Uri ("https://dl.google.com/android/repository/" + $archiveUrl) -Destination $commandLineZip
    Assert-FileHash -Path $commandLineZip -Algorithm $checksumAlgorithm -Expected $checksumNode.InnerText

    $sdkStage = Join-Path $toolchainRoot ("stage-sdk-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $sdkStage | Out-Null
    Expand-Archive -LiteralPath $commandLineZip -DestinationPath $sdkStage
    $stagedSdkManager = Get-ChildItem -LiteralPath $sdkStage -Filter 'sdkmanager.bat' -File -Recurse | Select-Object -First 1
    if ($null -eq $stagedSdkManager) {
        throw 'Could not locate sdkmanager.bat in the verified Android command-line tools archive.'
    }
    $extractedCommandLineHome = $stagedSdkManager.Directory.Parent.FullName
    $resolvedSdkStage = [System.IO.Path]::GetFullPath($sdkStage)
    $resolvedCommandLineHome = [System.IO.Path]::GetFullPath($extractedCommandLineHome)
    if (-not $resolvedCommandLineHome.StartsWith($resolvedSdkStage + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Extracted Android tools escaped staging directory: $resolvedCommandLineHome"
    }
    $commandLineToolsRoot = Join-Path $sdkRoot 'cmdline-tools'
    $latestCommandLineHome = Join-Path $commandLineToolsRoot 'latest'
    if (-not (Test-Path -LiteralPath $commandLineToolsRoot)) {
        New-Item -ItemType Directory -Path $commandLineToolsRoot | Out-Null
    }
    if (Test-Path -LiteralPath $latestCommandLineHome) {
        throw "Android command-line target exists but is incomplete: $latestCommandLineHome"
    }
    Move-Item -LiteralPath $resolvedCommandLineHome -Destination $latestCommandLineHome
}

$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_HOME = $sdkRoot
$androidCli = Join-Path $sdkRoot 'cmdline-tools\latest\bin\android.exe'

$platformToolsArchive = Get-AndroidPackageArchive -Repository $repositoryXml -PackagePath 'platform-tools'
$platformToolsInstall = @{
    Uri          = $platformToolsArchive.Uri
    Checksum     = $platformToolsArchive.Checksum
    DownloadPath = Join-Path $downloadRoot 'android-platform-tools.zip'
    TargetPath   = Join-Path $sdkRoot 'platform-tools'
    SentinelFile = 'adb.exe'
}
Install-VerifiedAndroidArchive @platformToolsInstall

$platformArchive = Get-AndroidPackageArchive -Repository $repositoryXml -PackagePath 'platforms;android-36'
$platformInstall = @{
    Uri          = $platformArchive.Uri
    Checksum     = $platformArchive.Checksum
    DownloadPath = Join-Path $downloadRoot 'android-platform-36.zip'
    TargetPath   = Join-Path $sdkRoot 'platforms\android-36'
    SentinelFile = 'android.jar'
}
Install-VerifiedAndroidArchive @platformInstall

$buildToolsArchive = Get-AndroidPackageArchive -Repository $repositoryXml -PackagePath 'build-tools;36.0.0'
$buildToolsInstall = @{
    Uri          = $buildToolsArchive.Uri
    Checksum     = $buildToolsArchive.Checksum
    DownloadPath = Join-Path $downloadRoot 'android-build-tools-36.0.0.zip'
    TargetPath   = Join-Path $sdkRoot 'build-tools\36.0.0'
    SentinelFile = 'aapt2.exe'
}
Install-VerifiedAndroidArchive @buildToolsInstall

if (-not (Test-Path -LiteralPath (Join-Path $gradleHome 'bin\gradle.bat'))) {
    $gradleZip = Join-Path $downloadRoot 'gradle-9.5.0-bin.zip'
    $gradleChecksum = Join-Path $downloadRoot 'gradle-9.5.0-bin.zip.sha256'
    Get-OfficialFile -Uri 'https://downloads.gradle.org/distributions/gradle-9.5.0-bin.zip' -Destination $gradleZip
    Get-OfficialFile -Uri 'https://downloads.gradle.org/distributions/gradle-9.5.0-bin.zip.sha256' -Destination $gradleChecksum
    Assert-FileHash -Path $gradleZip -Algorithm 'SHA256' -Expected (Get-Content -LiteralPath $gradleChecksum -Raw)
    Expand-Archive -LiteralPath $gradleZip -DestinationPath $gradleRoot
}

$java = Join-Path $jdkHome 'bin\java.exe'
$gradle = Join-Path $gradleHome 'bin\gradle.bat'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$androidJar = Join-Path $sdkRoot 'platforms\android-36\android.jar'
$aapt2 = Join-Path $sdkRoot 'build-tools\36.0.0\aapt2.exe'

foreach ($requiredFile in @($java, $androidCli, $gradle, $adb, $androidJar, $aapt2)) {
    if (-not (Test-Path -LiteralPath $requiredFile)) {
        throw "Toolchain verification failed; missing $requiredFile"
    }
}

Write-Host "TOOLCHAIN_ROOT=$toolchainRoot"
Write-Host "JAVA_HOME=$jdkHome"
Write-Host "ANDROID_SDK_ROOT=$sdkRoot"
Write-Host "GRADLE_HOME=$gradleHome"
Write-Host "ADB=$adb"
