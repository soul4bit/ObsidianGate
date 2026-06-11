[CmdletBinding()]
param(
    [string]$ManifestPath = "examples/manifest.json",
    [string]$ClientSourceDir = "modpack/client",
    [string]$ServerSourceDir = "modpack/server",
    [string]$DistDir = "dist",
    [string]$ManifestVersion = (Get-Date -Format "yyyy.MM.dd"),
    [string]$LauncherUpdatePath = "launcher/obsidian-gate-launcher.jar",
    [string]$LauncherBootstrapSourceDir = "launcher/windows",
    [string]$PublicBaseUrl = $env:OBSIDIANGATE_PUBLIC_BASE_URL,
    [string]$PublicServerHost = $env:OBSIDIANGATE_PUBLIC_SERVER_HOST,
    [int]$PublicServerPort = [int]($env:OBSIDIANGATE_PUBLIC_SERVER_PORT -as [int]),
    [string]$PublicAuthBaseUrl = $env:OBSIDIANGATE_PUBLIC_AUTH_BASE_URL,
    [switch]$SkipSourceManifestUpdate,
    [switch]$SkipAuthRelease,
    [switch]$SkipLauncherRelease
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
$clientSourceFullPath = Resolve-InputPath $ClientSourceDir
$serverSourceFullPath = Resolve-InputPath $ServerSourceDir
$distFullPath = Resolve-InputPath $DistDir
$authMetadataPath = Join-Path $distFullPath "auth-release.json"
$distManifestPath = Join-Path $distFullPath "manifest.json"
$modpackMetadataPath = Join-Path $distFullPath "modpack-release.json"
$distServerRoot = Join-Path $distFullPath "server"
$distLauncherRoot = Join-Path $distFullPath "launcher"

$serverModPaths = @(
    "mods/[___MixinCompat-1.1-1.12.2___].jar",
    "mods/AdvSolarPatch-1.2.1.jar",
    "mods/Advanced Solar Panels-4.3.0.jar",
    "mods/appliedenergistics2-rv6-stable-7.jar",
    "mods/AutoRegLib-1.3-32.jar",
    "mods/Baubles-1.12-1.5.2.jar",
    "mods/bettercaves-1.12.2-1.6.0.jar",
    "mods/BetterQuesting-3.5.329.jar",
    "mods/bewitchment-1.12.2-0.0.22.65.jar",
    "mods/BiblioCraft[v2.4.5][MC1.12.2].jar",
    "mods/BiomesOPlenty_1.12.2_7.0.1.2444_universal.jar",
    "mods/Botania+r1.10-364.4.jar",
    "mods/CarbonConfig-1.12.2-1.2.4.jar",
    "mods/Chunk-Pregenerator-1.12.2-4.4.9.1.jar",
    "mods/Clumps-3.1.2.jar",
    "mods/crafttweaker2-1.12-4.1.20.jar",
    "mods/DivineRPG-1.7.1.jar",
    "mods/DynamicSurroundings-1.12.2-3.6.1.0.jar",
    "mods/DynamicTrees-1.12.2-0.9.7.jar",
    "mods/DynamicTreesBOP-1.12.2-1.4.1e.jar",
    "mods/Erebus-1.0.32.jar",
    "mods/foamfix-0.10.10-1.12.2.jar",
    "mods/ImmersiveEngineering-0.12-98.jar",
    "mods/industrialcraft-2-2.8.222-ex112.jar",
    "mods/ironchest-1.12.2-7.0.71.846.jar",
    "mods/IvToolkit-1.3.3-1.12.jar",
    "mods/Mantle-1.12-1.3.3.55.jar",
    "mods/OreLib-1.12.2-3.6.0.1.jar",
    "mods/Patchouli-1.0-23.6.jar",
    "mods/Quark-r1.6-179.jar",
    "mods/randompatches-1.12.2-1.21.0.0.jar",
    "mods/RecurrentComplex-1.4.8.6.jar",
    "mods/RoguelikeDungeons-1.12.2-1.8.0.jar",
    "mods/savemystronghold-1.12.2-1.0.0.jar",
    "mods/SereneSeasons_1.12.2_1.2.18_universal.jar",
    "mods/spark-unforged-1.11.140-forge.jar",
    "mods/StandardExpansion-3.4.173.jar",
    "mods/TConstruct-1.12.2-2.13.0.183.jar",
    "mods/Thaumcraft-1.12.2-6.1.BETA26.jar",
    "mods/ThaumicAugmentation-1.12.2-2.1.14.jar",
    "mods/thaumicenergistics-2.2.4.jar",
    "mods/ThaumicInventoryScanning_1.12.2-2.0.10.jar",
    "mods/twilightforest-1.12.2-3.11.1021-universal.jar",
    "mods/TravelersBackpack-1.12.2-1.0.35.jar"
)

if (-not (Test-Path $manifestFullPath)) {
    throw "Manifest not found: $manifestFullPath"
}

if (-not (Test-Path $clientSourceFullPath)) {
    throw "Client source directory not found: $clientSourceFullPath"
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

function Normalize-ManifestPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    return $Value.Trim().Replace('\', '/').TrimStart('/')
}

function Test-RelativeContentPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    $trimmed = $Value.Trim()
    if ($trimmed -match '^[a-zA-Z][a-zA-Z0-9+\-.]*://') {
        return $false
    }
    return -not $trimmed.StartsWith("/")
}

function Test-HasText {
    param(
        [AllowNull()]
        [string]$Value
    )

    return -not [string]::IsNullOrWhiteSpace($Value)
}

function Apply-PublicManifestOverrides {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Manifest
    )

    if (Test-HasText $PublicBaseUrl) {
        $Manifest.baseUrl = $PublicBaseUrl.Trim()
    }

    if (-not $Manifest.PSObject.Properties.Name.Contains("launcher") -or $null -eq $Manifest.launcher) {
        $Manifest | Add-Member -NotePropertyName "launcher" -NotePropertyValue ([pscustomobject]@{})
    }

    if (Test-HasText $PublicServerHost) {
        Set-ManifestProperty -Target $Manifest.launcher -Name "serverHost" -Value $PublicServerHost.Trim()
    }
    if ($PublicServerPort -gt 0) {
        Set-ManifestProperty -Target $Manifest.launcher -Name "serverPort" -Value $PublicServerPort
    }
    if (Test-HasText $PublicAuthBaseUrl) {
        Set-ManifestProperty -Target $Manifest.launcher -Name "authBaseUrl" -Value $PublicAuthBaseUrl.Trim()
    }
}

function Set-ManifestProperty {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Target,

        [Parameter(Mandatory = $true)]
        [string]$Name,

        [AllowNull()]
        [object]$Value
    )

    if ($Target.PSObject.Properties.Name.Contains($Name)) {
        $Target.$Name = $Value
    } else {
        $Target | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    }
}

function Get-RelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,

        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $rootPath = [System.IO.Path]::GetFullPath($Root)
    if (-not $rootPath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $rootPath += [System.IO.Path]::DirectorySeparatorChar
    }

    $rootUri = New-Object System.Uri($rootPath)
    $fileUri = New-Object System.Uri(([System.IO.Path]::GetFullPath($Path)))
    $relativeUri = $rootUri.MakeRelativeUri($fileUri)
    return [System.Uri]::UnescapeDataString($relativeUri.ToString()).Replace('\', '/')
}

function Get-FileRecord {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.FileInfo]$File,

        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    return [pscustomobject][ordered]@{
        path = $RelativePath
        sha256 = (Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        size = [int64]$File.Length
        executable = $false
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
        Where-Object { $_.Name -notlike "original-*" -and $_.Name -notlike "*-shaded.jar" } |
        Sort-Object LastWriteTime -Descending)

    if (-not $artifacts) {
        throw "Artifact not found in $Directory for pattern $Pattern"
    }

    return $artifacts[0]
}

function Patch-BiblioCraftRecipes {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JarPath
    )

    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        return
    }

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $disabledRecipeJson = @"
{
    "conditions": [
        {
            "type": "forge:false"
        }
    ],
    "type": "minecraft:crafting_shaped",
    "pattern": [
        "X"
    ],
    "key": {
        "X": {
            "item": "minecraft:barrier"
        }
    },
    "result": {
        "item": "minecraft:barrier"
    },
    "group": "bibliocraft"
}
"@

    $encoding = New-Object System.Text.UTF8Encoding($false)
    $fixedZipTimestamp = [System.DateTimeOffset]::new(2000, 1, 1, 0, 0, 0, [System.TimeSpan]::Zero)
    $zip = [System.IO.Compression.ZipFile]::Open($JarPath, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        foreach ($entryName in @(
            "assets/bibliocraft/recipes/markerpole.json",
            "assets/bibliocraft/recipes/clipboard.json"
        )) {
            $existingEntry = $zip.GetEntry($entryName)
            if ($existingEntry -ne $null) {
                $existingEntry.Delete()
            }

            $entry = $zip.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime = $fixedZipTimestamp
            $stream = $entry.Open()
            try {
                $writer = New-Object System.IO.StreamWriter($stream, $encoding)
                try {
                    $writer.Write($disabledRecipeJson)
                } finally {
                    $writer.Dispose()
                }
            } finally {
                $stream.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }
}

function Get-ProjectVersion {
    $pomPath = Join-Path $repoRoot "pom.xml"
    [xml]$pom = Get-Content $pomPath
    return [string]$pom.project.version
}

function Build-LauncherArtifact {
    Write-Host "==> Building launcher update jar" -ForegroundColor Cyan
    & mvn "-f" (Join-Path $repoRoot "pom.xml") "package" "-DskipTests" |
        ForEach-Object { Write-Host $_ }
    $mavenExitCode = $LASTEXITCODE
    if ($mavenExitCode -ne 0) {
        throw "Launcher Maven build failed."
    }

    $projectVersion = Get-ProjectVersion
    $targetDirectory = Join-Path $repoRoot "target"
    $versionedArtifact = Join-Path $targetDirectory "obsidian-gate-launcher-$projectVersion.jar"
    if (Test-Path -LiteralPath $versionedArtifact -PathType Leaf) {
        return (Get-Item -LiteralPath $versionedArtifact)
    }

    $versionedShadedArtifact = Join-Path $targetDirectory "obsidian-gate-launcher-$projectVersion-shaded.jar"
    if (Test-Path -LiteralPath $versionedShadedArtifact -PathType Leaf) {
        return (Get-Item -LiteralPath $versionedShadedArtifact)
    }

    return (Get-ArtifactFile -Directory "target" -Pattern "obsidian-gate-launcher-*.jar")
}

function Copy-DirectoryContent {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Source,

        [Parameter(Mandatory = $true)]
        [string]$Destination
    )

    if (Test-Path $Destination) {
        Remove-Item $Destination -Recurse -Force
    }

    $null = New-Item -ItemType Directory -Path $Destination -Force

    Get-ChildItem $Source -Force | ForEach-Object {
        Copy-Item $_.FullName -Destination $Destination -Recurse -Force
    }
}

function Copy-DirectoryContentMerge {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Source,

        [Parameter(Mandatory = $true)]
        [string]$Destination
    )

    $null = New-Item -ItemType Directory -Path $Destination -Force

    Get-ChildItem $Source -Force | ForEach-Object {
        Copy-Item $_.FullName -Destination $Destination -Recurse -Force
    }
}

function Copy-RelativeFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SourceRoot,

        [Parameter(Mandatory = $true)]
        [string]$DestinationRoot,

        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $normalizedPath = Normalize-ManifestPath $RelativePath
    $sourcePath = Join-Path $SourceRoot ($normalizedPath.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "Server file declared for release was not found: $normalizedPath"
    }

    $destinationPath = Join-Path $DestinationRoot ($normalizedPath.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
    $destinationParent = Split-Path -Parent $destinationPath
    $null = New-Item -ItemType Directory -Path $destinationParent -Force
    Copy-Item -LiteralPath $sourcePath -Destination $destinationPath -Force
    return Get-Item -LiteralPath $destinationPath
}

function Update-LauncherBootstrapHash {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BootstrapPath,

        [Parameter(Mandatory = $true)]
        [string]$LauncherSha256
    )

    if (-not (Test-Path -LiteralPath $BootstrapPath -PathType Leaf)) {
        return
    }

    $content = Get-Content -LiteralPath $BootstrapPath -Raw -Encoding UTF8
    $pattern = '\[string\]\$LauncherSha256\s*=\s*"[^"]*"'
    $replacement = '[string]$LauncherSha256 = "' + $LauncherSha256 + '"'
    if ($content -notmatch $pattern) {
        throw "Launcher bootstrap does not expose LauncherSha256: $BootstrapPath"
    }

    Write-Utf8NoBom -Path $BootstrapPath -Content ([regex]::Replace($content, $pattern, $replacement, 1))
}

if (-not $SkipAuthRelease) {
    Write-Host "==> Preparing auth release artifacts" -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "release-auth.ps1") `
        -ManifestPath $ManifestPath `
        -DistDir $DistDir `
        -ManifestVersion $ManifestVersion
    if ($LASTEXITCODE -ne 0) {
        throw "release-auth.ps1 failed."
    }
}

foreach ($path in @($authMetadataPath, $distManifestPath)) {
    if (-not (Test-Path $path)) {
        throw "Required auth release file not found: $path"
    }
}

$authMetadata = Get-Content $authMetadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
$manifest = Get-Content $distManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$clientJarName = $authMetadata.artifacts.client.fileName
$clientJarPath = Join-Path $distFullPath $clientJarName

if (-not (Test-Path $clientJarPath)) {
    throw "Client auth jar not found in dist: $clientJarPath"
}

$distClientRoot = Join-Path $distFullPath "client"
Copy-DirectoryContent -Source $clientSourceFullPath -Destination $distClientRoot
Patch-BiblioCraftRecipes -JarPath (Join-Path $distClientRoot "mods/BiblioCraft[v2.4.5][MC1.12.2].jar")

$distClientModsDir = Join-Path $distClientRoot "mods"
$null = New-Item -ItemType Directory -Path $distClientModsDir -Force

Get-ChildItem $distClientModsDir -Filter "obsidiangate-forge-auth-client-*.jar" -File -ErrorAction SilentlyContinue |
    Remove-Item -Force

Copy-Item $clientJarPath (Join-Path $distClientModsDir $clientJarName) -Force

Write-Host "==> Preparing server-side modpack files" -ForegroundColor Cyan
if (Test-Path $distServerRoot) {
    Remove-Item $distServerRoot -Recurse -Force
}

$null = New-Item -ItemType Directory -Path $distServerRoot -Force
$serverFiles = [System.Collections.Generic.List[object]]::new()

foreach ($serverModPath in $serverModPaths) {
    $serverModFile = Copy-RelativeFile `
        -SourceRoot $clientSourceFullPath `
        -DestinationRoot $distServerRoot `
        -RelativePath $serverModPath
    if ((Normalize-ManifestPath $serverModPath) -eq "mods/BiblioCraft[v2.4.5][MC1.12.2].jar") {
        Patch-BiblioCraftRecipes -JarPath $serverModFile.FullName
        $serverModFile = Get-Item -LiteralPath $serverModFile.FullName
    }
    $serverFiles.Add((Get-FileRecord -File $serverModFile -RelativePath (Normalize-ManifestPath $serverModPath)))
}

foreach ($serverDirectoryName in @("config", "scripts")) {
    $sourceDirectory = Join-Path $clientSourceFullPath $serverDirectoryName
    if (-not (Test-Path -LiteralPath $sourceDirectory -PathType Container)) {
        continue
    }

    $destinationDirectory = Join-Path $distServerRoot $serverDirectoryName
    Copy-DirectoryContent -Source $sourceDirectory -Destination $destinationDirectory
    Get-ChildItem $destinationDirectory -Recurse -File -Force |
        Sort-Object FullName |
        ForEach-Object {
            $relativePath = Get-RelativePath -Root $distServerRoot -Path $_.FullName
            $serverFiles.Add((Get-FileRecord -File $_ -RelativePath $relativePath))
        }
}

if (Test-Path -LiteralPath $serverSourceFullPath -PathType Container) {
    foreach ($serverDirectoryName in @("mods", "config", "scripts", "systemd")) {
        $sourceDirectory = Join-Path $serverSourceFullPath $serverDirectoryName
        if (-not (Test-Path -LiteralPath $sourceDirectory -PathType Container)) {
            continue
        }

        $destinationDirectory = Join-Path $distServerRoot $serverDirectoryName
        Copy-DirectoryContentMerge -Source $sourceDirectory -Destination $destinationDirectory
        Get-ChildItem $sourceDirectory -Recurse -File -Force |
            Sort-Object FullName |
            ForEach-Object {
                $sourceRelativePath = Get-RelativePath -Root $sourceDirectory -Path $_.FullName
                $relativePath = Normalize-ManifestPath ("$serverDirectoryName/$sourceRelativePath")
                $destinationPath = Join-Path $distServerRoot ($relativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
                $serverFiles.Add((Get-FileRecord -File (Get-Item -LiteralPath $destinationPath) -RelativePath $relativePath))
            }
    }

    $serverIconPath = Join-Path $serverSourceFullPath "server-icon.png"
    if (Test-Path -LiteralPath $serverIconPath -PathType Leaf) {
        $destinationPath = Join-Path $distServerRoot "server-icon.png"
        Copy-Item -LiteralPath $serverIconPath -Destination $destinationPath -Force
        $serverFiles.Add((Get-FileRecord -File (Get-Item -LiteralPath $destinationPath) -RelativePath "server-icon.png"))
    }
}

$manifest.version = $ManifestVersion

$launcherRecord = $null
$launcherClientRelativePath = $null
if (-not $SkipLauncherRelease) {
    $launcherUpdateRequired = $false
    if ($manifest.PSObject.Properties.Name.Contains("launcherUpdate") `
        -and $null -ne $manifest.launcherUpdate `
        -and $manifest.launcherUpdate.PSObject.Properties.Name.Contains("required")) {
        $launcherUpdateRequired = [bool]$manifest.launcherUpdate.required
    }

    $launcherJar = Build-LauncherArtifact
    $normalizedLauncherPath = Normalize-ManifestPath $LauncherUpdatePath
    if (-not (Test-RelativeContentPath $normalizedLauncherPath)) {
        throw "LauncherUpdatePath must be a relative web path: $LauncherUpdatePath"
    }

    $distLauncherPath = Join-Path $distFullPath ($normalizedLauncherPath.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
    $distLauncherParent = Split-Path -Parent $distLauncherPath

    $null = New-Item -ItemType Directory -Path $distLauncherRoot -Force
    $launcherBootstrapSourceFullPath = Resolve-InputPath $LauncherBootstrapSourceDir
    if (Test-Path -LiteralPath $launcherBootstrapSourceFullPath -PathType Container) {
        Copy-DirectoryContent -Source $launcherBootstrapSourceFullPath -Destination $distLauncherRoot
    }

    $null = New-Item -ItemType Directory -Path $distLauncherParent -Force
    Copy-Item $launcherJar.FullName $distLauncherPath -Force
    $distLauncherFile = Get-Item $distLauncherPath
    $distLauncherMirrorPath = Join-Path $distLauncherRoot $distLauncherFile.Name
    if ($distLauncherMirrorPath -ne $distLauncherPath) {
        Copy-Item $launcherJar.FullName $distLauncherMirrorPath -Force
    }

    $launcherRecord = [pscustomobject][ordered]@{
        version = $ManifestVersion
        url = $normalizedLauncherPath
        sha256 = (Get-FileHash -LiteralPath $distLauncherFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        size = [int64]$distLauncherFile.Length
        required = $launcherUpdateRequired
        artifactVersion = Get-ProjectVersion
        fileName = $distLauncherFile.Name
    }

    Update-LauncherBootstrapHash `
        -BootstrapPath (Join-Path $distLauncherRoot "ObsidianGateLauncher.ps1") `
        -LauncherSha256 $launcherRecord.sha256

    if ($normalizedLauncherPath.StartsWith("client/", [System.StringComparison]::OrdinalIgnoreCase)) {
        $launcherClientRelativePath = $normalizedLauncherPath.Substring("client/".Length)
    }

    if ($manifest.PSObject.Properties.Name.Contains("launcherUpdate")) {
        $manifest.launcherUpdate = $launcherRecord
    } else {
        $manifest | Add-Member -NotePropertyName "launcherUpdate" -NotePropertyValue $launcherRecord
    }
}

$runtimePaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
if ($manifest.runtime -and $manifest.runtime.packages) {
    foreach ($package in $manifest.runtime.packages) {
        if (-not $package.url) {
            continue
        }

        $normalizedPath = Normalize-ManifestPath $package.url
        if (-not (Test-RelativeContentPath $normalizedPath)) {
            continue
        }

        $null = $runtimePaths.Add($normalizedPath)
        $runtimeFilePath = Join-Path $distClientRoot ($normalizedPath.Replace('/', [System.IO.Path]::DirectorySeparatorChar))

        if (-not (Test-Path $runtimeFilePath)) {
            throw "Runtime package declared in manifest was not found in client source: $normalizedPath"
        }

        $runtimeFile = Get-Item $runtimeFilePath
        $package.sha256 = (Get-FileHash -LiteralPath $runtimeFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $package.size = [int64]$runtimeFile.Length
    }
}

$ignoredNames = @(".gitkeep", ".DS_Store", "Thumbs.db")
$manifestFiles = [System.Collections.Generic.List[object]]::new()

Get-ChildItem $distClientRoot -Recurse -File -Force |
    Where-Object { $ignoredNames -notcontains $_.Name } |
    Sort-Object FullName |
    ForEach-Object {
        $relativePath = Get-RelativePath -Root $distClientRoot -Path $_.FullName
        if ($runtimePaths.Contains($relativePath)) {
            return
        }
        if ($launcherClientRelativePath -and $relativePath.Equals($launcherClientRelativePath, [System.StringComparison]::OrdinalIgnoreCase)) {
            return
        }

        $manifestFiles.Add((Get-FileRecord -File $_ -RelativePath $relativePath))
    }

$manifest.files = @($manifestFiles)

if (-not $SkipSourceManifestUpdate) {
    $sourceManifestJson = $manifest | ConvertTo-Json -Depth 10
    Write-Utf8NoBom -Path $manifestFullPath -Content ($sourceManifestJson + [Environment]::NewLine)
}

Apply-PublicManifestOverrides -Manifest $manifest
$manifestJson = $manifest | ConvertTo-Json -Depth 10
Write-Utf8NoBom -Path $distManifestPath -Content ($manifestJson + [Environment]::NewLine)

$metadata = [pscustomobject][ordered]@{
    generatedAt = (Get-Date).ToString("o")
    manifest = [pscustomobject][ordered]@{
        sourcePath = $ManifestPath
        version = $manifest.version
        distPath = "manifest.json"
    }
    client = [pscustomobject][ordered]@{
        sourcePath = $ClientSourceDir
        distPath = "client"
        fileCount = $manifestFiles.Count
    }
    server = [pscustomobject][ordered]@{
        sourcePath = $ClientSourceDir
        serverSourcePath = $ServerSourceDir
        distPath = "server"
        modCount = $serverModPaths.Count
        fileCount = $serverFiles.Count
    }
    artifacts = $authMetadata.artifacts
    launcherUpdate = $launcherRecord
}

$metadataJson = $metadata | ConvertTo-Json -Depth 10
Write-Utf8NoBom -Path $modpackMetadataPath -Content ($metadataJson + [Environment]::NewLine)

Write-Host ""
Write-Host "Modpack release prepared in $distFullPath" -ForegroundColor Green
Write-Host "Manifest version: $($manifest.version)"
Write-Host "Client source: $ClientSourceDir"
Write-Host "Web files: $($manifestFiles.Count)"
Write-Host "Server files: $($serverFiles.Count)"
Write-Host "Client auth mod: $clientJarName"
if ($launcherRecord) {
    Write-Host "Launcher update: $($launcherRecord.url) sha256=$($launcherRecord.sha256) size=$($launcherRecord.size)"
}
