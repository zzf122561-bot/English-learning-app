[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$manifestPath = Join-Path $repo '_manager/probes/tts-device/model-manifest.json'
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
$assetRoot = Join-Path $repo 'app/src/main/assets'
$modelSource = Join-Path $repo '_manager/local/tts-models/qwen3-1.7b-customvoice-q4-b7ee2e8'
New-Item -ItemType Directory -Force (Join-Path $assetRoot 'models') | Out-Null
foreach ($file in $manifest.models[0].files) {
    if ($file.assetPath -ne "models/$($file.name)" -or $file.name -notmatch '^qwen-[a-zA-Z0-9_.-]+\.gguf$') { throw 'Unexpected pinned asset path' }
    $source = Join-Path $modelSource $file.name
    $target = Join-Path $assetRoot $file.assetPath
    if ((Get-Item -LiteralPath $source).Length -ne $file.sizeBytes) { throw 'Pinned source size changed' }
    if (!(Test-Path -LiteralPath $target)) {
        Copy-Item -LiteralPath $source -Destination $target
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $file.sha256) { throw 'New speech asset copy hash mismatch; preserve for inspection' }
    }
    if ((Get-Item -LiteralPath $target).Length -ne $file.sizeBytes) { throw 'Existing speech asset size changed' }
}
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $assetRoot 'models/manifest.json')
# Every archived APK is independently stream-hashed; unchanged source files are not re-audited here.
'SPEECH_ASSETS_PREPARED: fixed 1.7B Q4 pair; final APK bytes still require archive verification'
