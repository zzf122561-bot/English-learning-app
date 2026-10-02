param([ValidateRange(1,999)][int]$TestNumber = 1)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
Set-Location -LiteralPath $repo
function Invoke-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Native command failed: $Exe ($LASTEXITCODE)" }
}
function Hash-Stream($Stream) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [Convert]::ToHexString($sha.ComputeHash($Stream)).ToLowerInvariant() }
    finally { $sha.Dispose() }
}
$dirty = git status --porcelain
if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Build requires a clean committed checkout.' }
$commit = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot identify source commit.' }
$stem = 'ModelProbe-v0.1.0-test.{0:D3}-{1}' -f $TestNumber,$commit.Substring(0,7)
$archive = Join-Path $repo '_manager/artifacts/tts-probe'
$finalApk = Join-Path $archive "$stem.apk"
$receiptPath = Join-Path $archive "$stem.json"
if ((Test-Path -LiteralPath $finalApk) -or (Test-Path -LiteralPath $receiptPath)) { throw 'Archive name already exists; never overwrite.' }
$assets = Join-Path $PSScriptRoot 'app/src/main/assets'
$modelSource = Join-Path $repo '_manager/local/tts-models/qwen3-1.7b-customvoice-q4-b7ee2e8'
$manifestPath = Join-Path $PSScriptRoot 'model-manifest.json'
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
New-Item -ItemType Directory -Force (Join-Path $assets 'models') | Out-Null
foreach ($file in $manifest.models[0].files) {
    $source = Join-Path $modelSource $file.name
    $target = Join-Path $assets $file.assetPath
    if ((Get-Item -LiteralPath $source).Length -ne $file.sizeBytes) { throw 'Source size changed.' }
    if (!(Test-Path -LiteralPath $target)) { Copy-Item -LiteralPath $source -Destination $target }
    if ((Get-Item -LiteralPath $target).Length -ne $file.sizeBytes) { throw 'Asset size mismatch; preserve for investigation.' }
}
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $assets 'models/manifest.json')
if (!(Test-Path -LiteralPath (Join-Path $assets 'probe-sample.txt'))) { throw 'Authorized private sample missing.' }
$env:JAVA_HOME = 'D:\CodexData\android-toolchain\jdk-17'
$env:ANDROID_HOME = 'D:\CodexData\android-toolchain\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_OPTS = "-Xmx3g -Dfile.encoding=UTF-8 -Djdk.net.hosts.file=$($repo.Replace('\','/'))/_manager/tools/gradle-hosts.txt"
Invoke-Checked 'D:\CodexData\android-toolchain\gradle\gradle-9.5.0\bin\gradle.bat' @('-p',$PSScriptRoot,'--no-daemon','--no-configuration-cache','--max-workers=2',':app:assembleRelease')
if ((git status --porcelain) -or ((git rev-parse HEAD).Trim() -ne $commit)) { throw 'Source changed during build.' }
$unsigned = Join-Path $PSScriptRoot 'app/build/outputs/apk/release/app-release-unsigned.apk'
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0'
$stage = Join-Path $repo ('_manager/local/tts-apk/' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $stage | Out-Null
$aligned = Join-Path $stage 'aligned.apk'
$signed = Join-Path $stage 'signed.apk'
Invoke-Checked (Join-Path $buildTools 'zipalign.exe') @('-P','16','4',$unsigned,$aligned)
$signDir = Join-Path $repo '_manager/local/tts-probe-signing'
New-Item -ItemType Directory -Force $signDir | Out-Null
$key = Join-Path $signDir 'probe.p12'
$secret = Join-Path $signDir 'password.dpapi.xml'
if ((Test-Path -LiteralPath $key) -and !(Test-Path -LiteralPath $secret)) { throw 'Signing password record missing; do not replace key.' }
if (!(Test-Path -LiteralPath $secret)) {
    $random = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
    $secure = ConvertTo-SecureString ([Convert]::ToBase64String($random)) -AsPlainText -Force
    $secure | Export-Clixml -LiteralPath $secret
}
$protected = Import-Clixml -LiteralPath $secret
$credential = [pscredential]::new('probe',$protected)
$env:MODEL_PROBE_SIGN_PASS = $credential.GetNetworkCredential().Password
try {
    if (!(Test-Path -LiteralPath $key)) {
        Invoke-Checked (Join-Path $env:JAVA_HOME 'bin/keytool.exe') @('-genkeypair','-noprompt','-keystore',$key,'-storetype','PKCS12','-storepass:env','MODEL_PROBE_SIGN_PASS','-alias','probe','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Local Model Probe, O=Personal Testing, C=CN')
    }
    Invoke-Checked (Join-Path $env:JAVA_HOME 'bin/java.exe') @('-jar',(Join-Path $buildTools 'lib/apksigner.jar'),'sign','--ks',$key,'--ks-key-alias','probe','--ks-pass','env:MODEL_PROBE_SIGN_PASS','--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','false','--v4-signing-enabled','false','--out',$signed,$aligned)
} finally { Remove-Item Env:MODEL_PROBE_SIGN_PASS }
$signature = & (Join-Path $env:JAVA_HOME 'bin/java.exe') -jar (Join-Path $buildTools 'lib/apksigner.jar') verify --verbose --print-certs $signed
if ($LASTEXITCODE -ne 0 -or !($signature -match 'v2 scheme.*true')) { throw 'APK v2 signature validation failed.' }
Invoke-Checked (Join-Path $buildTools 'zipalign.exe') @('-c','-P','16','4',$signed)
$badging = & (Join-Path $buildTools 'aapt2.exe') dump badging $signed
if ($LASTEXITCODE -ne 0) { throw 'APK metadata read failed.' }
if (!($badging -match "package: name='com.xuesui.englishapp.modelprobe' versionCode='1' versionName='0.1.0-probe'") -or !($badging -match "sdkVersion:'31'")) { throw 'Wrong package/version/minSdk.' }
$unexpectedPermissions = $badging | Where-Object { $_ -match '^uses-permission:' -and $_ -ne "uses-permission: name='com.xuesui.englishapp.modelprobe.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'" }
if ($unexpectedPermissions) { throw 'Unexpected permission in independent offline probe.' }
$zip = [IO.Compression.ZipFile]::OpenRead($signed)
$checks = [Collections.Generic.List[object]]::new()
try {
    $expectations = @{}
    foreach ($file in $manifest.models[0].files) { $expectations['assets/' + $file.assetPath] = @{ hash=$file.sha256; size=$file.sizeBytes; stored=$true } }
    foreach ($item in @(@{name='assets/models/manifest.json';path=$manifestPath},@{name='assets/probe-sample.txt';path=(Join-Path $assets 'probe-sample.txt')})) {
        $expectations[$item.name] = @{hash=(Get-FileHash -LiteralPath $item.path -Algorithm SHA256).Hash.ToLowerInvariant();size=(Get-Item -LiteralPath $item.path).Length;stored=$false}
    }
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $assets 'licenses') -File) {
        $expectations['assets/licenses/' + $file.Name] = @{hash=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant();size=$file.Length;stored=$false}
    }
    $noticeRoot = Join-Path $repo 'experiments/tts-device-probe/src/main/resources/META-INF/model-probe'
    foreach ($file in Get-ChildItem -LiteralPath $noticeRoot -File) {
        $expectations['META-INF/model-probe/' + $file.Name] = @{hash=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant();size=$file.Length;stored=$false}
    }
    foreach ($name in $expectations.Keys) {
        $entry = $zip.GetEntry($name)
        $expected = $expectations[$name]
        if (!$entry -or $entry.Length -ne $expected.size) { throw "Missing or wrong-size APK entry: $name" }
        $stream = $entry.Open()
        try { $hash = Hash-Stream $stream } finally { $stream.Dispose() }
        if ($hash -ne $expected.hash) { throw "APK entry hash mismatch: $name" }
        if ($expected.stored -and $entry.CompressedLength -ne $entry.Length) { throw "GGUF must remain uncompressed: $name" }
        $checks.Add(@{entry=$name;size=$entry.Length;sha256=$hash})
    }
    $native = @($zip.Entries | Where-Object { $_.FullName -like 'lib/*/*.so' })
    if ($native.Count -ne 3) { throw 'Unexpected native library set.' }
    foreach ($entry in $native) {
        if ($entry.FullName -notmatch '^lib/arm64-v8a/(libmodel_probe_qwen|libc\+\+_shared|libandroidx\.graphics\.path)\.so$') { throw 'Unexpected native ABI/library.' }
        $stream = $entry.Open()
        try { $hash = Hash-Stream $stream } finally { $stream.Dispose() }
        $output = Join-Path $stage $entry.Name
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$output,$false)
        $checks.Add(@{entry=$entry.FullName;size=$entry.Length;sha256=$hash})
    }
} finally { $zip.Dispose() }
$elfTool = Join-Path $env:ANDROID_HOME 'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe'
$elfEvidence = @()
foreach ($so in Get-ChildItem -LiteralPath $stage -Filter '*.so') {
    $headers = & $elfTool -lW $so.FullName
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect packaged ELF.' }
    $loads = @($headers | Where-Object { $_ -match '^\s+LOAD\s' })
    if ($loads.Count -eq 0 -or ($loads | Where-Object { $_ -notmatch '0x4000\s*$|0x10000\s*$' })) { throw 'Packaged ELF LOAD alignment is below 16KiB.' }
    $elfEvidence += @{library=$so.Name;programHeaders=$headers}
}
$apkHash = (Get-FileHash -LiteralPath $signed -Algorithm SHA256).Hash.ToLowerInvariant()
$receipt = [ordered]@{sourceCommit=$commit;createdUtc=[DateTime]::UtcNow.ToString('o');filename="$stem.apk";sizeBytes=(Get-Item -LiteralPath $signed).Length;sha256=$apkHash;package='com.xuesui.englishapp.modelprobe';versionName='0.1.0-probe';versionCode=1;modelManifest=$manifest;entries=$checks;signature=$signature;badging=$badging;zipAlignment='zipalign -c -P 16 4: passed';elf=$elfEvidence;moduleEvidence='experiments/tts-device-probe/SOURCE_MANIFEST.md sections 7-8';deviceStatus='not_run; user acceptance pending';sourceClean=$true}
if ((git status --porcelain) -or ((git rev-parse HEAD).Trim() -ne $commit)) { throw 'Source changed; do not archive.' }
New-Item -ItemType Directory -Force $archive | Out-Null
[IO.File]::Copy($signed,$finalApk,$false)
$receipt | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $receiptPath -Encoding utf8NoBOM
Write-Output "APK_VERIFIED $finalApk"
Write-Output "SHA256 $apkHash"
Write-Output "RECEIPT $receiptPath"
