[CmdletBinding()]
param(
    [string]$ManifestPath = "examples/manifest.json",
    [string]$DistDir = "dist",
    [string]$ManifestVersion = (Get-Date -Format "yyyy.MM.dd"),
    [string]$ManifestPrivateKeyPath = $env:OBSIDIANGATE_MANIFEST_PRIVATE_KEY
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

$manifestFullPath = Resolve-InputPath $ManifestPath
$distFullPath = Resolve-InputPath $DistDir

if (-not (Test-Path $manifestFullPath)) {
    throw "Manifest not found: $manifestFullPath"
}

$null = Get-Command mvn -ErrorAction Stop

function Invoke-MavenBuild {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PomFile,

        [Parameter(Mandatory = $true)]
        [string[]]$Goals
    )

    Write-Host "==> mvn -f $PomFile $($Goals -join ' ')" -ForegroundColor Cyan
    & mvn "-f" (Join-Path $repoRoot $PomFile) @Goals
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed for $PomFile"
    }
}

function Get-ArtifactFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Directory,

        [Parameter(Mandatory = $true)]
        [string]$Pattern
    )

    $artifacts = Get-ChildItem (Join-Path $repoRoot $Directory) -File -Filter $Pattern |
        Where-Object { $_.Name -notlike "original-*" } |
        Sort-Object LastWriteTime -Descending

    if (-not $artifacts) {
        throw "Artifact not found in $Directory for pattern $Pattern"
    }

    return $artifacts[0]
}

function Get-ArtifactRecord {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.FileInfo]$File,

        [string]$ManifestFilePath = ""
    )

    return [pscustomobject][ordered]@{
        fileName = $File.Name
        size = [int64]$File.Length
        sha256 = (Get-FileHash $File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        manifestPath = $ManifestFilePath
    }
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

Invoke-MavenBuild -PomFile "game-auth-common/pom.xml" -Goals @("clean", "install")
Invoke-MavenBuild -PomFile "forge-auth-client/pom.xml" -Goals @("clean", "package")
Invoke-MavenBuild -PomFile "forge-auth-server/pom.xml" -Goals @("clean", "package")

$commonJar = Get-ArtifactFile -Directory "game-auth-common/target" -Pattern "obsidiangate-game-auth-common-*.jar"
$clientJar = Get-ArtifactFile -Directory "forge-auth-client/target" -Pattern "obsidiangate-forge-auth-client-*.jar"
$serverJar = Get-ArtifactFile -Directory "forge-auth-server/target" -Pattern "obsidiangate-forge-auth-server-*.jar"

$clientManifestPath = "mods/$($clientJar.Name)"
$commonRecord = Get-ArtifactRecord -File $commonJar
$clientRecord = Get-ArtifactRecord -File $clientJar -ManifestFilePath $clientManifestPath
$serverRecord = Get-ArtifactRecord -File $serverJar

$manifest = Get-Content $manifestFullPath -Raw -Encoding UTF8 | ConvertFrom-Json

if (-not $manifest.PSObject.Properties.Name.Contains("files")) {
    $manifest | Add-Member -NotePropertyName "files" -NotePropertyValue @()
}

$manifest.version = $ManifestVersion

$remainingFiles = @(
    $manifest.files | Where-Object {
        $_.path -notmatch "^mods/obsidiangate-forge-auth-client-.*\.jar$"
    }
)

$clientEntry = [pscustomobject][ordered]@{
    path = $clientRecord.manifestPath
    sha256 = $clientRecord.sha256
    size = $clientRecord.size
    executable = $false
}

$manifest.files = @($remainingFiles + $clientEntry)

$manifestJson = $manifest | ConvertTo-Json -Depth 10
Write-Utf8NoBom -Path $manifestFullPath -Content ($manifestJson + [Environment]::NewLine)

if (Test-Path $distFullPath) {
    Remove-Item $distFullPath -Recurse -Force
}

$null = New-Item -ItemType Directory -Path $distFullPath

Copy-Item $commonJar.FullName (Join-Path $distFullPath $commonJar.Name)
Copy-Item $clientJar.FullName (Join-Path $distFullPath $clientJar.Name)
Copy-Item $serverJar.FullName (Join-Path $distFullPath $serverJar.Name)
Copy-Item $manifestFullPath (Join-Path $distFullPath "manifest.json")
& (Join-Path $PSScriptRoot "sign-manifest.ps1") `
    -ManifestPath (Join-Path $distFullPath "manifest.json") `
    -PrivateKeyPath $ManifestPrivateKeyPath
if ($LASTEXITCODE -ne 0) {
    throw "Manifest signing failed."
}

$metadata = [pscustomobject][ordered]@{
    generatedAt = (Get-Date).ToString("o")
    manifest = [pscustomobject][ordered]@{
        sourcePath = $ManifestPath
        version = $manifest.version
        distPath = "manifest.json"
        signatureDistPath = "manifest.json.sig"
    }
    artifacts = [pscustomobject][ordered]@{
        common = $commonRecord
        client = $clientRecord
        server = $serverRecord
    }
}

$metadataJson = $metadata | ConvertTo-Json -Depth 10
Write-Utf8NoBom -Path (Join-Path $distFullPath "auth-release.json") -Content ($metadataJson + [Environment]::NewLine)

Write-Host ""
Write-Host "Auth release prepared in $distFullPath" -ForegroundColor Green
Write-Host "Manifest version: $($manifest.version)"
Write-Host "Client auth mod: $($clientRecord.fileName) sha256=$($clientRecord.sha256) size=$($clientRecord.size)"
Write-Host "Server auth mod: $($serverRecord.fileName) sha256=$($serverRecord.sha256) size=$($serverRecord.size)"
