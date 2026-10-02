# Manager-only retrieval of the pinned public Qwen probe weights.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$destination = Join-Path $repoRoot '_manager/local/tts-models/qwen3-1.7b-customvoice-q4-b7ee2e8'
$revision = 'b7ee2e8c7459c3bea99da23e3d178125a7d1713c'
$files = @(
    @{ Name = 'qwen-talker-1.7b-customvoice-Q4_K_M.gguf'; Bytes = 1182631296L; Sha256 = 'cc328834a631bc08bf9f43e62fa23f8a1383d9b429864ce6690cfb172077fc4a' },
    @{ Name = 'qwen-tokenizer-12hz-Q4_K_M.gguf'; Bytes = 254974752L; Sha256 = 'cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039' }
)
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$curl = (Get-Command curl.exe -ErrorAction Stop).Source
foreach ($file in $files) {
    $final = Join-Path $destination $file.Name
    $partial = $final + '.partial'
    $candidate = $final
    if (-not (Test-Path -LiteralPath $final)) {
        Write-Output "DOWNLOADING $($file.Name) expectedBytes=$($file.Bytes)"
        $curlArgs = @('--fail', '--location', '--silent', '--show-error', '--proto', '=https', '--proto-redir', '=https', '--connect-timeout', '30', '--max-time', '1800', '--speed-limit', '1024', '--speed-time', '60', '--retry', '3', '--retry-all-errors', '--continue-at', '-', '--output', $partial, "https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF/resolve/$revision/$($file.Name)")
        & $curl @curlArgs
        if ($LASTEXITCODE -ne 0) { throw "Model download failed; retained partial file: $partial" }
        $candidate = $partial
    }
    if ((Get-Item -LiteralPath $candidate).Length -ne $file.Bytes) { throw "Model length mismatch: $candidate" }
    $actual = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $file.Sha256) { throw "Model SHA256 mismatch; retained for diagnosis: $candidate" }
    if ($candidate -eq $partial) { Move-Item -LiteralPath $partial -Destination $final }
    Write-Output "VERIFIED $($file.Name) bytes=$($file.Bytes) sha256=$actual"
}
Write-Output "MODEL_PAIR_VERIFIED revision=$revision directory=$destination"
