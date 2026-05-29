[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$DistDir = "dist",
    [string]$Target = "mc-rpg-deploy",
    [string]$RemoteHome = "/home/minecraft",
    [string]$RemoteServerModsDir = "/home/minecraft/mc-rpg/mods",
    [string]$RemoteWebRoot = "/var/www/mc-rpg",
    [string]$ServiceName = "mc-rpg.service",
    [switch]$SkipRestart
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

$distFullPath = Resolve-InputPath $DistDir
$metadataPath = Join-Path $distFullPath "auth-release.json"
$manifestPath = Join-Path $distFullPath "manifest.json"

if (-not (Test-Path $metadataPath)) {
    throw "Release metadata not found: $metadataPath. Run scripts/release-auth.ps1 first."
}

foreach ($command in @("scp", "ssh")) {
    $null = Get-Command $command -ErrorAction Stop
}

$metadata = Get-Content $metadataPath -Raw | ConvertFrom-Json
$serverFileName = $metadata.artifacts.server.fileName
$clientFileName = $metadata.artifacts.client.fileName

$serverJarPath = Join-Path $distFullPath $serverFileName
$clientJarPath = Join-Path $distFullPath $clientFileName

foreach ($path in @($serverJarPath, $clientJarPath, $manifestPath)) {
    if (-not (Test-Path $path)) {
        throw "Required release file not found: $path"
    }
}

$remoteClientModsDir = "$RemoteWebRoot/client/mods"
$sshTtyArgs = @("-tt")

function Invoke-External {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [Parameter(Mandatory = $true)]
        [string]$Action
    )

    if (-not $PSCmdlet.ShouldProcess($Target, $Action)) {
        return
    }

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $Command $($Arguments -join ' ')"
    }
}

Write-Host "==> Uploading auth artifacts to $Target" -ForegroundColor Cyan
Invoke-External -Command "scp" -Arguments @(
    $serverJarPath,
    $clientJarPath,
    $manifestPath,
    "${Target}:$RemoteHome/"
) -Action "Upload dist artifacts"

Write-Host "==> Installing auth artifacts on remote host" -ForegroundColor Cyan
$remoteCommands = [System.Collections.Generic.List[string]]::new()
$remoteCommands.Add("set -e")
$remoteCommands.Add("mkdir -p '$RemoteServerModsDir'")
$remoteCommands.Add("install -m 644 '$RemoteHome/$serverFileName' '$RemoteServerModsDir/$serverFileName'")
$remoteCommands.Add("sudo -v")
$remoteCommands.Add("sudo mkdir -p '$remoteClientModsDir'")
$remoteCommands.Add("sudo install -m 644 '$RemoteHome/$clientFileName' '$remoteClientModsDir/$clientFileName'")
$remoteCommands.Add("sudo install -m 644 '$RemoteHome/manifest.json' '$RemoteWebRoot/manifest.json'")
$remoteCommands.Add("sha256sum '$RemoteServerModsDir/$serverFileName'")
$remoteCommands.Add("sha256sum '$remoteClientModsDir/$clientFileName'")

if (-not $SkipRestart) {
    Write-Host "==> Restarting $ServiceName" -ForegroundColor Cyan
    $remoteCommands.Add("sudo systemctl restart '$ServiceName'")
    $remoteCommands.Add("sudo systemctl status '$ServiceName' --no-pager -l")
}

$remoteScript = $remoteCommands -join "`n"

Invoke-External -Command "ssh" -Arguments @(
    $sshTtyArgs +
    @(
        $Target,
        $remoteScript
    )
) -Action "Install artifacts, verify hashes, and optionally restart service"

Write-Host ""
Write-Host "Deploy complete for $Target" -ForegroundColor Green
