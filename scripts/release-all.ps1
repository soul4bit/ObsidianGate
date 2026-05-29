[CmdletBinding()]
param(
    [string]$ManifestPath = "examples/manifest.json",
    [string]$ClientSourceDir = "modpack/client",
    [string]$ServerSourceDir = "modpack/server",
    [string]$DistDir = "dist",
    [string]$ManifestVersion = (Get-Date -Format "yyyy.MM.dd"),
    [string]$LauncherUpdatePath = "client/launcher/obsidian-gate-launcher.jar",
    [string]$LauncherBootstrapSourceDir = "launcher/windows",
    [string]$PublicBaseUrl = $env:OBSIDIANGATE_PUBLIC_BASE_URL,
    [string]$PublicServerHost = $env:OBSIDIANGATE_PUBLIC_SERVER_HOST,
    [int]$PublicServerPort = [int]($env:OBSIDIANGATE_PUBLIC_SERVER_PORT -as [int]),
    [string]$PublicAuthBaseUrl = $env:OBSIDIANGATE_PUBLIC_AUTH_BASE_URL,
    [switch]$UpdateSourceManifest,
    [switch]$SkipPreflightTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Resolve-InputPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    if ([System.IO.Path]::IsPathRooted($Value)) {
        return $Value
    }

    return (Join-Path $repoRoot $Value)
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Invoke-Maven {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    Write-Host "==> $Description" -ForegroundColor Cyan
    Write-Host "    mvn $($Arguments -join ' ')" -ForegroundColor DarkGray
    & mvn @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven command failed: mvn $($Arguments -join ' ')"
    }
}

function Get-ArtifactFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Directory,

        [Parameter(Mandatory = $true)]
        [string]$Pattern
    )

    $artifacts = @(Get-ChildItem (Join-Path $repoRoot $Directory) -File -Filter $Pattern |
        Where-Object { $_.Name -notlike "original-*" -and $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending)

    if (-not $artifacts) {
        throw "Artifact not found in $Directory for pattern $Pattern"
    }

    return $artifacts[0]
}

function Get-ArtifactRecord {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.FileInfo]$File,

        [string]$DistPath = $File.Name
    )

    return [pscustomobject][ordered]@{
        fileName = $File.Name
        distPath = $DistPath.Replace('\', '/')
        size = [int64]$File.Length
        sha256 = (Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

$null = Get-Command mvn -ErrorAction Stop
$effectiveManifestPath = $ManifestPath

if (-not $UpdateSourceManifest) {
    $sourceManifestPath = Resolve-InputPath $ManifestPath
    if (-not (Test-Path -LiteralPath $sourceManifestPath -PathType Leaf)) {
        throw "Manifest not found: $sourceManifestPath"
    }

    $tempReleaseDir = Join-Path $repoRoot "tmp/release-all"
    $null = New-Item -ItemType Directory -Path $tempReleaseDir -Force
    $tempManifestPath = Join-Path $tempReleaseDir ("manifest-" + [System.Guid]::NewGuid().ToString("N") + ".json")
    Copy-Item -LiteralPath $sourceManifestPath -Destination $tempManifestPath -Force
    $effectiveManifestPath = $tempManifestPath
}

if (-not $SkipPreflightTests) {
    Invoke-Maven -Description "Testing launcher" -Arguments @("test")
    Invoke-Maven -Description "Building Auth API" -Arguments @("-f", "auth-api/pom.xml", "clean", "package")
    Invoke-Maven -Description "Installing common game auth module" -Arguments @("-f", "game-auth-common/pom.xml", "clean", "install")
    Invoke-Maven -Description "Building Forge auth client" -Arguments @("-f", "forge-auth-client/pom.xml", "clean", "package")
    Invoke-Maven -Description "Building Forge auth server" -Arguments @("-f", "forge-auth-server/pom.xml", "clean", "package")
} else {
    Invoke-Maven -Description "Building Auth API without tests" -Arguments @("-f", "auth-api/pom.xml", "clean", "package", "-DskipTests")
}

$releaseModpackScript = Join-Path $PSScriptRoot "release-modpack.ps1"
Write-Host "==> Preparing launcher, Forge and modpack release artifacts" -ForegroundColor Cyan
& $releaseModpackScript `
    -ManifestPath $effectiveManifestPath `
    -ClientSourceDir $ClientSourceDir `
    -ServerSourceDir $ServerSourceDir `
    -DistDir $DistDir `
    -ManifestVersion $ManifestVersion `
    -LauncherUpdatePath $LauncherUpdatePath `
    -LauncherBootstrapSourceDir $LauncherBootstrapSourceDir `
    -PublicBaseUrl $PublicBaseUrl `
    -PublicServerHost $PublicServerHost `
    -PublicServerPort $PublicServerPort `
    -PublicAuthBaseUrl $PublicAuthBaseUrl `
    -SkipSourceManifestUpdate:(!$UpdateSourceManifest)

if ($LASTEXITCODE -ne 0) {
    throw "release-modpack.ps1 failed."
}

$distFullPath = Resolve-InputPath $DistDir
$authApiJar = Get-ArtifactFile -Directory "auth-api/target" -Pattern "obsidiangate-auth-api-*.jar"
$distAuthApiDir = Join-Path $distFullPath "auth-api"
$null = New-Item -ItemType Directory -Path $distAuthApiDir -Force
$distAuthApiPath = Join-Path $distAuthApiDir $authApiJar.Name
Copy-Item -LiteralPath $authApiJar.FullName -Destination $distAuthApiPath -Force

$modpackMetadataPath = Join-Path $distFullPath "modpack-release.json"
$authMetadataPath = Join-Path $distFullPath "auth-release.json"

if (-not (Test-Path -LiteralPath $modpackMetadataPath -PathType Leaf)) {
    throw "Modpack release metadata not found: $modpackMetadataPath"
}
if (-not (Test-Path -LiteralPath $authMetadataPath -PathType Leaf)) {
    throw "Auth release metadata not found: $authMetadataPath"
}

$modpackMetadata = Get-Content -LiteralPath $modpackMetadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
$authMetadata = Get-Content -LiteralPath $authMetadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
$authApiRecord = Get-ArtifactRecord -File (Get-Item -LiteralPath $distAuthApiPath) -DistPath "auth-api/$($authApiJar.Name)"
$manifestRecord = $modpackMetadata.manifest
$manifestRecord.sourcePath = $ManifestPath

$releaseMetadata = [pscustomobject][ordered]@{
    generatedAt = (Get-Date).ToString("o")
    version = $ManifestVersion
    distPath = $DistDir
    sourceManifestUpdated = [bool]$UpdateSourceManifest
    preflightTestsSkipped = [bool]$SkipPreflightTests
    artifacts = [pscustomobject][ordered]@{
        launcherUpdate = $modpackMetadata.launcherUpdate
        authApi = $authApiRecord
        common = $authMetadata.artifacts.common
        forgeAuthClient = $authMetadata.artifacts.client
        forgeAuthServer = $authMetadata.artifacts.server
        manifest = $manifestRecord
        clientModpack = $modpackMetadata.client
        serverModpack = $modpackMetadata.server
    }
}

$releaseJson = $releaseMetadata | ConvertTo-Json -Depth 12
Write-Utf8NoBom -Path (Join-Path $distFullPath "release.json") -Content ($releaseJson + [Environment]::NewLine)

Write-Host ""
Write-Host "Full local release prepared in $distFullPath" -ForegroundColor Green
Write-Host "Version: $ManifestVersion"
Write-Host "Auth API: auth-api/$($authApiJar.Name)"
Write-Host "Manifest: manifest.json"
Write-Host "Metadata: release.json"
