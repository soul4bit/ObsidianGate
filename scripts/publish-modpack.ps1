[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$ManifestPath = "examples/manifest.json",
    [string]$ClientSourceDir = "modpack/client",
    [string]$ServerSourceDir = "modpack/server",
    [string]$DistDir = "dist",
    [string]$ManifestVersion = (Get-Date -Format "yyyy.MM.dd"),
    [string]$PublicBaseUrl = $env:OBSIDIANGATE_PUBLIC_BASE_URL,
    [string]$PublicServerHost = $env:OBSIDIANGATE_PUBLIC_SERVER_HOST,
    [int]$PublicServerPort = [int]($env:OBSIDIANGATE_PUBLIC_SERVER_PORT -as [int]),
    [string]$PublicAuthBaseUrl = $env:OBSIDIANGATE_PUBLIC_AUTH_BASE_URL,
    [string]$ManifestPrivateKeyPath = $env:OBSIDIANGATE_MANIFEST_PRIVATE_KEY,
    [switch]$SkipSourceManifestUpdate,
    [switch]$SkipLauncherRelease,
    [string]$Target = "mc-rpg-deploy",
    [string]$RemoteHome = "/home/minecraft",
    [string]$RemoteStageDir = "/home/minecraft/obsidiangate-deploy",
    [string]$RemoteServerModsDir = "/home/minecraft/mc-rpg/mods",
    [string]$RemoteWebRoot = "/var/www/mc-rpg",
    [string]$ServiceName = "mc-rpg.service",
    [string]$RemoteDeployCommand = "/usr/local/bin/obsidiangate-deploy",
    [switch]$LegacyPromptSudo,
    [switch]$DisableRsync,
    [switch]$SkipConnectivityCheck,
    [switch]$SkipRestart
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$releaseScript = Join-Path $PSScriptRoot "release-modpack.ps1"
$deployScript = Join-Path $PSScriptRoot "deploy-modpack.ps1"

function Test-DeployTargetReachability {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedTarget
    )

    foreach ($command in @("ssh")) {
        $null = Get-Command $command -ErrorAction Stop
    }

    Write-Host "==> Preflight SSH check for $ResolvedTarget" -ForegroundColor Cyan
    & ssh -o BatchMode=yes -o ConnectTimeout=5 $ResolvedTarget "exit 0"
    if ($LASTEXITCODE -ne 0) {
        throw @"
SSH target '$ResolvedTarget' is not reachable before publish.
Check that the host alias/IP is correct, the server is online, and you are on the right LAN/VPN.
If you only need release artifacts without deploy, run scripts/release-modpack.ps1 instead.
"@
    }
}

if (-not $WhatIfPreference -and -not $SkipConnectivityCheck) {
    Test-DeployTargetReachability -ResolvedTarget $Target
}

& $releaseScript `
    -ManifestPath $ManifestPath `
    -ClientSourceDir $ClientSourceDir `
    -ServerSourceDir $ServerSourceDir `
    -DistDir $DistDir `
    -ManifestVersion $ManifestVersion `
    -PublicBaseUrl $PublicBaseUrl `
    -PublicServerHost $PublicServerHost `
    -PublicServerPort $PublicServerPort `
    -PublicAuthBaseUrl $PublicAuthBaseUrl `
    -ManifestPrivateKeyPath $ManifestPrivateKeyPath `
    -SkipSourceManifestUpdate:$SkipSourceManifestUpdate `
    -SkipLauncherRelease:$SkipLauncherRelease

if ($LASTEXITCODE -ne 0) {
    throw "release-modpack.ps1 failed."
}

& $deployScript `
    -DistDir $DistDir `
    -Target $Target `
    -RemoteHome $RemoteHome `
    -RemoteStageDir $RemoteStageDir `
    -RemoteServerModsDir $RemoteServerModsDir `
    -RemoteWebRoot $RemoteWebRoot `
    -ServiceName $ServiceName `
    -RemoteDeployCommand $RemoteDeployCommand `
    -LegacyPromptSudo:$LegacyPromptSudo `
    -DisableRsync:$DisableRsync `
    -SkipRestart:$SkipRestart `
    -WhatIf:$WhatIfPreference

if ($LASTEXITCODE -ne 0) {
    throw "deploy-modpack.ps1 failed."
}
