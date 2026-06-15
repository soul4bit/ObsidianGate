[CmdletBinding()]
param(
    [string[]]$ManifestPath = @("examples/manifest.json", "dist/manifest.json"),
    [string]$ManifestPrivateKeyPath = $env:OBSIDIANGATE_MANIFEST_PRIVATE_KEY,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AdditionalManifestPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$utf8Strict = New-Object System.Text.UTF8Encoding($false, $true)
$cp1251 = [System.Text.Encoding]::GetEncoding(1251)
$cyrillicEr = [char]0x0420
$cyrillicEs = [char]0x0421
$latinEth = [char]0x00D0
$latinNtilde = [char]0x00D1
if ($AdditionalManifestPath) {
    $ManifestPath = @($ManifestPath) + @($AdditionalManifestPath)
}

function Resolve-InputPath {
    param([Parameter(Mandatory = $true)][string]$Value)
    if ([System.IO.Path]::IsPathRooted($Value)) {
        return $Value
    }
    return (Join-Path $repoRoot $Value)
}

function Repair-Mojibake {
    param([AllowNull()][string]$Value)
    if ($null -eq $Value -or $Value.Length -eq 0) {
        return $Value
    }

    $candidate = $Value
    for ($attempt = 0; $attempt -lt 2; $attempt++) {
        if (-not (Test-LooksMojibake $candidate)) {
            break
        }
        try {
            $bytes = $cp1251.GetBytes($candidate)
            $decoded = $utf8Strict.GetString($bytes)
            if ($decoded -eq $candidate) {
                break
            }
            $candidate = $decoded
        } catch {
            break
        }
    }
    return $candidate
}

function Test-LooksMojibake {
    param([AllowNull()][string]$Value)
    if ($null -eq $Value -or $Value.Length -eq 0) {
        return $false
    }
    return $Value.IndexOf($cyrillicEr) -ge 0 `
        -or $Value.IndexOf($cyrillicEs) -ge 0 `
        -or $Value.IndexOf($latinEth) -ge 0 `
        -or $Value.IndexOf($latinNtilde) -ge 0
}

function Repair-Object {
    param([AllowNull()][object]$Value)
    if ($null -eq $Value) {
        return $null
    }
    if ($Value -is [string]) {
        return Repair-Mojibake $Value
    }
    if ($Value -is [pscustomobject]) {
        foreach ($property in $Value.PSObject.Properties) {
            $property.Value = Repair-Object $property.Value
        }
        return $Value
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        $items = New-Object System.Collections.ArrayList
        foreach ($item in $Value) {
            [void]$items.Add((Repair-Object $item))
        }
        return ,([object[]]$items.ToArray())
    }
    return $Value
}

function Convert-ToArray {
    param([AllowNull()][object]$Value)
    if ($null -eq $Value) {
        return ,(New-Object 'object[]' 0)
    }
    if ($Value -is [System.Array]) {
        return ,([object[]]$Value)
    }
    return ,([object[]]@($Value))
}

function Normalize-NewsEntry {
    param([AllowNull()][object]$Entry)
    if ($null -eq $Entry) {
        return $null
    }

    $Entry = Repair-Object $Entry
    foreach ($name in @("highlights", "newMods", "removedMods", "important")) {
        if ($Entry.PSObject.Properties.Name.Contains($name)) {
            $Entry.$name = [object[]](Convert-ToArray $Entry.$name)
        }
    }
    return $Entry
}

foreach ($rawPath in $ManifestPath) {
    $path = Resolve-InputPath $rawPath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Write-Warning "Manifest not found: $path"
        continue
    }

    $manifest = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($manifest.PSObject.Properties.Name.Contains("news") -and $manifest.news) {
        $manifest.news = Normalize-NewsEntry $manifest.news
    }
    if ($manifest.PSObject.Properties.Name.Contains("changelog") -and $manifest.changelog) {
        $manifest.changelog = Normalize-NewsEntry $manifest.changelog
    }
    if ($manifest.PSObject.Properties.Name.Contains("history") -and $manifest.history) {
        $history = New-Object System.Collections.ArrayList
        foreach ($entry in (Convert-ToArray $manifest.history)) {
            [void]$history.Add((Normalize-NewsEntry $entry))
        }
        $manifest.history = [object[]]$history.ToArray()
    }

    $json = ($manifest | ConvertTo-Json -Depth 100) + [Environment]::NewLine
    [System.IO.File]::WriteAllText($path, $json, $utf8NoBom)
    & (Join-Path $PSScriptRoot "sign-manifest.ps1") `
        -ManifestPath $path `
        -PrivateKeyPath $ManifestPrivateKeyPath
    if ($LASTEXITCODE -ne 0) {
        throw "Manifest signing failed after normalization: $path"
    }
    Write-Host "Normalized manifest news: $rawPath"
}
