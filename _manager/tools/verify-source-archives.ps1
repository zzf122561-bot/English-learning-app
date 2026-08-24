[CmdletBinding()]
param(
    [string]$ArchiveDirectory = (Join-Path $PSScriptRoot '..\vendor\source-archives')
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression.FileSystem

$archiveRoot = (Resolve-Path -LiteralPath $ArchiveDirectory).Path
$expectations = @(
    [pscustomobject]@{
        Name             = 'mdict-reader-e25373923035f06156dbfa8aedeb802b5167e6df.zip'
        Sha256           = '87C45EC9B6775B954B3EED20EF213A7E0F259E43727D7042E18803C545C99AE0'
        TopDirectory     = 'mdict-reader-e25373923035f06156dbfa8aedeb802b5167e6df/'
        RequiredFiles    = @('LICENSE', 'Cargo.toml', 'Cargo.lock', 'src/lib.rs')
        VerifyMdictCargo = $true
    }
    [pscustomobject]@{
        Name             = 'lzokay-rs-c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0.zip'
        Sha256           = '0E56626FCF4A76A92C6D38D644AFA0FB54761121E4D0227E971F6FF8AB5AF218'
        TopDirectory     = 'lzokay-rs-c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0/'
        RequiredFiles    = @('LICENSE', 'Cargo.toml', 'src/lib.rs')
        VerifyMdictCargo = $false
    }
)

function Read-ZipText {
    param(
        [System.IO.Compression.ZipArchive]$Archive,
        [string]$EntryName
    )

    $entry = $Archive.GetEntry($EntryName)
    if ($null -eq $entry) {
        throw "Missing ZIP entry: $EntryName"
    }

    $stream = $entry.Open()
    try {
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $true)
        try {
            return $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

$results = foreach ($expected in $expectations) {
    $archivePath = Join-Path $archiveRoot $expected.Name
    if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) {
        throw "Missing source archive: $archivePath"
    }

    $item = Get-Item -LiteralPath $archivePath
    if ($item.Length -le 0) {
        throw "Source archive is empty: $archivePath"
    }

    $actualHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash
    if ($actualHash -ne $expected.Sha256) {
        throw "SHA-256 mismatch for $($expected.Name): expected $($expected.Sha256), got $actualHash"
    }

    $archive = [System.IO.Compression.ZipFile]::OpenRead($archivePath)
    try {
        $invalidEntry = $archive.Entries | Where-Object {
            $_.FullName.StartsWith('/') -or
            $_.FullName.StartsWith('\\') -or
            $_.FullName -match '(^|/)\.\.(/|$)' -or
            -not $_.FullName.StartsWith($expected.TopDirectory, [System.StringComparison]::Ordinal)
        } | Select-Object -First 1

        if ($null -ne $invalidEntry) {
            throw "Unexpected or unsafe ZIP entry in $($expected.Name): $($invalidEntry.FullName)"
        }

        foreach ($relativePath in $expected.RequiredFiles) {
            $entryName = $expected.TopDirectory + $relativePath
            if ($null -eq $archive.GetEntry($entryName)) {
                throw "Missing required entry in $($expected.Name): $entryName"
            }
        }

        $licenseText = Read-ZipText -Archive $archive -EntryName ($expected.TopDirectory + 'LICENSE')
        if ($licenseText -notmatch 'MIT License' -or $licenseText -notmatch 'Permission is hereby granted') {
            throw "MIT license text not confirmed in $($expected.Name)"
        }

        $rustSources = @($archive.Entries | Where-Object {
            $_.FullName.StartsWith($expected.TopDirectory + 'src/', [System.StringComparison]::Ordinal) -and
            $_.FullName.EndsWith('.rs', [System.StringComparison]::Ordinal)
        })
        if ($rustSources.Count -eq 0) {
            throw "No Rust reference source files found in $($expected.Name)"
        }

        if ($expected.VerifyMdictCargo) {
            $cargoLock = Read-ZipText -Archive $archive -EntryName ($expected.TopDirectory + 'Cargo.lock')
            if ($cargoLock -notmatch '(?ms)^\[\[package\]\]\s*\r?\nname = "lzokay"\s*\r?\nversion = "2\.0\.1"') {
                throw "mdict-reader Cargo.lock does not pin lzokay 2.0.1"
            }
        }

        [pscustomobject]@{
            Name        = $expected.Name
            Bytes       = $item.Length
            SHA256      = $actualHash
            TopDirectory = $expected.TopDirectory.TrimEnd('/')
            Entries     = $archive.Entries.Count
            RustSources = $rustSources.Count
            License     = 'MIT'
        }
    }
    finally {
        $archive.Dispose()
    }
}

$results | Format-Table -AutoSize
Write-Output "SOURCE_ARCHIVE_CHECK_PASSED archives=$($results.Count)"
