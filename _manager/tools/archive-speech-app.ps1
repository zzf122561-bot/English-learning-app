[CmdletBinding()]
param([ValidateRange(1,999)][int]$TestNumber = 1)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $repo
$receipt = Get-Content -Raw -LiteralPath (Join-Path $repo '_manager/local/build-app-receipt.json') | ConvertFrom-Json
if (!$receipt.clean -or (git status --porcelain) -or (git rev-parse HEAD).Trim() -ne $receipt.sourceCommit) { throw 'Require matching clean build receipt and source checkout' }
$apk = Join-Path $repo 'app/build/outputs/apk/release/app-release.apk'
if ($receipt.apkPath -ne $apk -or (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash -ne $receipt.apkSha256) { throw 'APK does not match build receipt' }
$version = '1.2.0'
$stem = 'EnglishApp-v{0}-test.{1:D3}-{2}' -f $version,$TestNumber,$receipt.sourceCommit.Substring(0,7)
$archive = Join-Path $repo ("_manager/artifacts/app/v$version/test.{0:D3}" -f $TestNumber)
$release = Join-Path $repo ("_manager/releases/app/v$version/test.{0:D3}" -f $TestNumber)
if ((Test-Path -LiteralPath $archive) -or (Test-Path -LiteralPath $release)) { throw 'Release sequence already exists; never overwrite' }
$sdk = 'D:/CodexData/android-toolchain/android-sdk'
$java = 'D:/CodexData/android-toolchain/jdk-17/bin/java.exe'
$toolsDir = Join-Path $sdk 'build-tools/36.0.0'
$signature = & $java -jar (Join-Path $toolsDir 'lib/apksigner.jar') verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0 -or !($signature -match 'v2 scheme.*true')) { throw 'APK v2 verification failed' }
$expectedCert = '1b8799f7712d1de409daf80034dbbf8465c37aa807567f3eb1996ef17179a788'
if (!($signature -match "certificate SHA-256 digest: $expectedCert") -or !($signature -match '^Number of signers: 1$')) { throw 'Signing continuity mismatch' }
& (Join-Path $toolsDir 'zipalign.exe') -c -P 16 4 $apk
if ($LASTEXITCODE -ne 0) { throw 'APK ZIP 16KiB alignment failure' }
$badging = & (Join-Path $toolsDir 'aapt2.exe') dump badging $apk
if ($LASTEXITCODE -ne 0 -or !($badging -match "package: name='com.xuesui.englishapp' versionCode='7' versionName='1.2.0'") -or !($badging -match "minSdkVersion:'29'|sdkVersion:'29'")) { throw 'Package/version/minSdk mismatch' }
if (!($badging -match "^application-label:'saber'$")) { throw 'App label must be lowercase saber' }
$expectedPermissions = @("uses-permission: name='android.permission.INTERNET'", "uses-permission: name='com.xuesui.englishapp.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'")
if ($badging | Where-Object { $_ -match '^uses-permission:' -and $_ -notin $expectedPermissions }) { throw 'Unexpected App permission' }
$assets = Join-Path $repo 'app/src/main/assets'
$modelManifest = Get-Content -Raw -LiteralPath (Join-Path $repo '_manager/probes/tts-device/model-manifest.json') | ConvertFrom-Json
$dictionaryManifest = Get-Content -Raw -LiteralPath (Join-Path $assets 'dictionaries/manifest.json') | ConvertFrom-Json
$expected = @{}
foreach ($file in $modelManifest.models[0].files) { $expected['assets/' + $file.assetPath] = @{hash=$file.sha256;size=$file.sizeBytes;stored=$true} }
foreach ($dictionary in $dictionaryManifest.dictionaries) {
    foreach ($file in (@($dictionary.mdx) + @($dictionary.resources))) {
        $entryName = 'assets/dictionaries/' + $file.path
        $local = Join-Path $assets ('dictionaries/' + $file.path)
        $expected[$entryName] = @{hash=$file.sha256;size=(Get-Item -LiteralPath $local).Length;stored=$false}
    }
}
foreach ($relative in @('models/manifest.json','dictionaries/manifest.json')) {
    $local = Join-Path $assets $relative
    $expected['assets/' + $relative] = @{hash=(Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash;size=(Get-Item -LiteralPath $local).Length;stored=$false}
}
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $assets 'licenses') -File) {
    $expected['assets/licenses/' + $file.Name] = @{hash=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash;size=$file.Length;stored=$false}
}
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $repo 'experiments/tts-device-probe/src/main/resources/META-INF/model-probe') -File) {
    $expected['META-INF/model-probe/' + $file.Name] = @{hash=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash;size=$file.Length;stored=$false}
}
$checks = [Collections.Generic.List[object]]::new()
$stage = Join-Path $repo ('_manager/local/app-archive-check/' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $stage | Out-Null
$zip = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    foreach ($name in $expected.Keys) {
        $entry = $zip.GetEntry($name)
        $e = $expected[$name]
        if (!$entry -or $entry.Length -ne $e.size) { throw "Missing or invalid APK entry $name" }
        $stream = $entry.Open()
        try { $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant() } finally { $stream.Dispose() }
        if ($hash -ne $e.hash -or ($e.stored -and $entry.CompressedLength -ne $entry.Length)) { throw "APK asset hash/compression mismatch: $name" }
        $checks.Add(@{entry=$name;sizeBytes=$entry.Length;sha256=$hash})
    }
    $native = @($zip.Entries | Where-Object { $_.FullName -like 'lib/*/*.so' })
    if ($native.Count -ne 3) { throw 'Unexpected native library count' }
    foreach ($entry in $native) {
        if ($entry.FullName -notmatch '^lib/arm64-v8a/(libmodel_probe_qwen|libc\+\+_shared|libandroidx\.graphics\.path)\.so$') { throw 'Unexpected native library/ABI' }
        $target = Join-Path $stage $entry.Name
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target,$false)
        $checks.Add(@{entry=$entry.FullName;sizeBytes=$entry.Length;sha256=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()})
    }
} finally { $zip.Dispose() }
$readelf = Join-Path $sdk 'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe'
$elf = @()
foreach ($file in Get-ChildItem -LiteralPath $stage -Filter '*.so') {
    $headers = & $readelf -lW $file.FullName
    if ($LASTEXITCODE -ne 0) { throw 'ELF inspection failed' }
    $loads = @($headers | Where-Object { $_ -match '^\s+LOAD\s' })
    if (!$loads.Count -or ($loads | Where-Object { $_ -notmatch '0x4000\s*$|0x10000\s*$' })) { throw 'ELF LOAD alignment below 16KiB' }
    $elf += @{library=$file.Name;headers=$headers}
}
$result = [ordered]@{sourceCommit=$receipt.sourceCommit;buildCompletedUtc=$receipt.completedUtc;archivedUtc=[DateTime]::UtcNow.ToString('o');filename="$stem.apk";sizeBytes=(Get-Item -LiteralPath $apk).Length;sha256=$receipt.apkSha256;package='com.xuesui.englishapp';applicationLabel='saber';versionName=$version;versionCode=7;minSdk=29;features=@{wordMemory='0.5.0';dictionary='0.2.1';speech='0.3.0'};signature=$signature;badging=$badging;modelManifest=$modelManifest;dictionaryManifest=$dictionaryManifest;verifiedEntries=$checks;zip16KiB='passed';elf=$elf;buildTasks=$receipt.tasks;deviceAcceptance='pending; earlier independent probe success is not acceptance of this APK'}
New-Item -ItemType Directory -Force $archive,$release | Out-Null
[IO.File]::Copy($apk,(Join-Path $archive "$stem.apk"),$false)
$result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $release 'ARTIFACT.json') -Encoding utf8NoBOM
Write-Output "ARCHIVED: $(Join-Path $archive "$stem.apk")"
Write-Output "SHA256: $($receipt.apkSha256)"
