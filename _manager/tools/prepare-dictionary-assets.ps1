[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$sourceRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'dictionary-users'))
$assetRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'app\src\main\assets'))
$targetRoot = [System.IO.Path]::GetFullPath((Join-Path $assetRoot 'dictionaries'))
$operationId = [Guid]::NewGuid().ToString('N')
$stagingRoot = [System.IO.Path]::GetFullPath((Join-Path $assetRoot ".dictionary-assets-staging-$operationId"))
$backupRoot = [System.IO.Path]::GetFullPath((Join-Path $assetRoot ".dictionary-assets-backup-$operationId"))

function Assert-ManagedAssetPath {
    param([Parameter(Mandatory)][string] $Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $prefix = $assetRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $fullPath.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing unmanaged asset path: $fullPath"
    }
    if ($fullPath.Equals($assetRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to operate on the asset root itself"
    }
    return $fullPath
}

function Assert-ExpectedHash {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][string] $Expected
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing private dictionary input: $Path"
    }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if (-not $actual.Equals($Expected, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Dictionary hash mismatch: $Path`nExpected: $Expected`nActual:   $actual"
    }
}

$dictionaries = @(
    [ordered]@{
        id = 'builtin.collins-advanced-bilingual'
        displayName = '柯林斯高阶英汉双解学习词典'
        sourceDirectory = '柯林斯双解学习词典'
        assetDirectory = 'collins-advanced-bilingual'
        mdx = [ordered]@{
            fileName = '柯林斯高阶英汉双解学习词典（好看）.mdx'
            sha256 = '14843F7E443FB1CDACC94145C9AE68879A582F13FA68399D21CAA5768A704C10'
        }
        resources = @(
            [ordered]@{
                fileName = '柯林斯高阶英汉双解学习词典（好看）.mdd'
                sha256 = '15A1668EAB08DBAD960C942CB99A7842AC8C7B77736A1936A4E77FD00FE6ED6C'
            }
        )
    },
    [ordered]@{
        id = 'builtin.oxford-ald9-en-en'
        displayName = 'Oxford Advanced Learner''s Dictionary 9'
        sourceDirectory = '牛津9英英(推荐)'
        assetDirectory = 'oxford-ald9-en-en'
        mdx = [ordered]@{
            fileName = 'Oxford ALD_9th_En-En.mdx'
            sha256 = '8E140C288D9F8D195F707F1297ADD0233E9CB44C31DC0BBCFEB46063C99C24BE'
        }
        resources = @(
            [ordered]@{
                fileName = 'Oxford ALD_9th_En-En.mdd'
                sha256 = '4ED26627CD7FC26916CA23CF9A3AF44FD03710EECFCDDC8607B2717C1AAF1BAE'
            }
        )
    }
)

[void](Assert-ManagedAssetPath -Path $targetRoot)
[void](Assert-ManagedAssetPath -Path $stagingRoot)
[void](Assert-ManagedAssetPath -Path $backupRoot)

if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
    throw "Missing private dictionary directory: $sourceRoot"
}
[System.IO.Directory]::CreateDirectory($assetRoot) | Out-Null
[System.IO.Directory]::CreateDirectory($stagingRoot) | Out-Null

$manifestItems = @()
$targetMovedToBackup = $false
try {
    foreach ($dictionary in $dictionaries) {
        $destinationDirectory = Join-Path $stagingRoot $dictionary.assetDirectory
        [System.IO.Directory]::CreateDirectory($destinationDirectory) | Out-Null

        $sourceDirectory = Join-Path $sourceRoot $dictionary.sourceDirectory
        $sourceMdx = Join-Path $sourceDirectory $dictionary.mdx.fileName
        Assert-ExpectedHash -Path $sourceMdx -Expected $dictionary.mdx.sha256
        $destinationMdx = Join-Path $destinationDirectory $dictionary.mdx.fileName
        Copy-Item -LiteralPath $sourceMdx -Destination $destinationMdx
        Assert-ExpectedHash -Path $destinationMdx -Expected $dictionary.mdx.sha256

        $manifestResources = @()
        foreach ($resource in $dictionary.resources) {
            $sourceResource = Join-Path $sourceDirectory $resource.fileName
            Assert-ExpectedHash -Path $sourceResource -Expected $resource.sha256
            $destinationResource = Join-Path $destinationDirectory $resource.fileName
            Copy-Item -LiteralPath $sourceResource -Destination $destinationResource
            Assert-ExpectedHash -Path $destinationResource -Expected $resource.sha256
            $manifestResources += [ordered]@{
                path = "$($dictionary.assetDirectory)/$($resource.fileName)"
                sha256 = $resource.sha256.ToLowerInvariant()
            }
        }

        $manifestItems += [ordered]@{
            id = $dictionary.id
            displayName = $dictionary.displayName
            mdx = [ordered]@{
                path = "$($dictionary.assetDirectory)/$($dictionary.mdx.fileName)"
                sha256 = $dictionary.mdx.sha256.ToLowerInvariant()
            }
            resources = $manifestResources
        }
    }

    $manifest = [ordered]@{
        schemaVersion = 1
        dictionaries = $manifestItems
    }
    $manifestPath = Join-Path $stagingRoot 'manifest.json'
    $json = $manifest | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText($manifestPath, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))

    if (Test-Path -LiteralPath $targetRoot) {
        Move-Item -LiteralPath $targetRoot -Destination $backupRoot
        $targetMovedToBackup = $true
    }
    Move-Item -LiteralPath $stagingRoot -Destination $targetRoot

    if ($targetMovedToBackup -and (Test-Path -LiteralPath $backupRoot)) {
        [void](Assert-ManagedAssetPath -Path $backupRoot)
        Remove-Item -LiteralPath $backupRoot -Recurse -Force
    }
} catch {
    if (Test-Path -LiteralPath $stagingRoot) {
        [void](Assert-ManagedAssetPath -Path $stagingRoot)
        Remove-Item -LiteralPath $stagingRoot -Recurse -Force
    }
    if ($targetMovedToBackup -and -not (Test-Path -LiteralPath $targetRoot) -and (Test-Path -LiteralPath $backupRoot)) {
        Move-Item -LiteralPath $backupRoot -Destination $targetRoot
    }
    throw
}

$manifestHash = (Get-FileHash -LiteralPath (Join-Path $targetRoot 'manifest.json') -Algorithm SHA256).Hash
Write-Host "Prepared 2 built-in dictionaries under $targetRoot"
Write-Host "Manifest SHA-256: $manifestHash"
