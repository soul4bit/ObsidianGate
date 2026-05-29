[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$DistDir = "dist",
    [string]$Target = "mc-rpg-deploy",
    [string]$RemoteHome = "/home/minecraft",
    [string]$RemoteStageDir = "/home/minecraft/obsidiangate-deploy",
    [string]$RemoteServerRoot = "/home/minecraft/mc-rpg",
    [string]$RemoteServerModsDir = "/home/minecraft/mc-rpg/mods",
    [string]$RemoteWebRoot = "/var/www/mc-rpg",
    [string]$ServiceName = "mc-rpg.service",
    [string]$RemoteDeployCommand = "/usr/local/bin/obsidiangate-deploy",
    [switch]$LegacyPromptSudo,
    [switch]$DisableRsync,
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
$metadataPath = Join-Path $distFullPath "modpack-release.json"
$manifestPath = Join-Path $distFullPath "manifest.json"
$clientDirPath = Join-Path $distFullPath "client"
$serverDirPath = Join-Path $distFullPath "server"
$launcherDirPath = Join-Path $distFullPath "launcher"

if (-not (Test-Path $metadataPath)) {
    throw "Modpack release metadata not found: $metadataPath. Run scripts/release-modpack.ps1 first."
}

foreach ($command in @("scp", "ssh")) {
    $null = Get-Command $command -ErrorAction Stop
}

$rsyncCommand = if ($DisableRsync) { $null } else { Get-Command "rsync" -ErrorAction SilentlyContinue }
$useRsync = $null -ne $rsyncCommand

$metadata = Get-Content $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
$serverFileName = $metadata.artifacts.server.fileName
$launcherUpdateFileName = if ($metadata.launcherUpdate -and $metadata.launcherUpdate.fileName) {
    [string]$metadata.launcherUpdate.fileName
} else {
    "obsidian-gate-launcher.jar"
}
$serverJarPath = Join-Path $distFullPath $serverFileName

$requiredPaths = @($serverJarPath, $manifestPath, $clientDirPath)
if (Test-Path $launcherDirPath) {
    $requiredPaths += $launcherDirPath
}

foreach ($path in $requiredPaths) {
    if (-not (Test-Path $path)) {
        throw "Required release file not found: $path"
    }
}

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

function Test-DeployTargetReachability {
    if (-not $PSCmdlet.ShouldProcess($Target, "Check SSH reachability")) {
        return
    }

    Write-Host "==> Preflight SSH check for $Target" -ForegroundColor Cyan
    & ssh -o BatchMode=yes -o ConnectTimeout=5 $Target "exit 0"
    if ($LASTEXITCODE -ne 0) {
        throw @"
SSH target '$Target' is not reachable before deploy.
Check that the host alias/IP is correct, the server is online, and you are on the right LAN/VPN.
"@
    }
}

function Invoke-RsyncUpload {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SourceDir,

        [Parameter(Mandatory = $true)]
        [string]$RemoteDir
    )

    if (-not $PSCmdlet.ShouldProcess($Target, "Upload client files with rsync")) {
        return $true
    }

    $source = (Resolve-Path $SourceDir).Path
    if (-not $source.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $source += [System.IO.Path]::DirectorySeparatorChar
    }

    Write-Host "==> Uploading client files with rsync --delete" -ForegroundColor Cyan
    & rsync -az --delete --stats $source "${Target}:$RemoteDir/"
    return $LASTEXITCODE -eq 0
}

Test-DeployTargetReachability

Write-Host "==> Preparing remote staging directory" -ForegroundColor Cyan
$prepareRemoteScript = if ($useRsync) {
    "rm -f '$RemoteStageDir/manifest.json' '$RemoteStageDir'/obsidiangate-forge-auth-server-*.jar; mkdir -p '$RemoteStageDir'"
} else {
    "rm -rf '$RemoteStageDir/client' '$RemoteStageDir/server' '$RemoteStageDir/launcher' '$RemoteStageDir/manifest.json'; mkdir -p '$RemoteStageDir'"
}
Invoke-External -Command "ssh" -Arguments @(
    $sshTtyArgs +
    @(
        $Target,
        $prepareRemoteScript
    )
) -Action "Prepare remote staging directory"

Write-Host "==> Uploading modpack release to $Target" -ForegroundColor Cyan
$uploadPaths = @($serverJarPath, $manifestPath)
$directoryUploads = @(
    [pscustomobject]@{ Name = "client"; Path = $clientDirPath; RemoteDir = "$RemoteStageDir/client" }
)
if (Test-Path $serverDirPath) {
    $directoryUploads += [pscustomobject]@{ Name = "server"; Path = $serverDirPath; RemoteDir = "$RemoteStageDir/server" }
}
if (Test-Path $launcherDirPath) {
    $directoryUploads += [pscustomobject]@{ Name = "launcher"; Path = $launcherDirPath; RemoteDir = "$RemoteStageDir/launcher" }
}
if ($useRsync) {
    foreach ($directoryUpload in $directoryUploads) {
        $rsyncSucceeded = Invoke-RsyncUpload -SourceDir $directoryUpload.Path -RemoteDir $directoryUpload.RemoteDir
        if (-not $rsyncSucceeded) {
            Write-Host "==> rsync failed for $($directoryUpload.Name), falling back to scp -r" -ForegroundColor Yellow
            Invoke-External -Command "ssh" -Arguments @(
                $sshTtyArgs +
                @(
                    $Target,
                    "rm -rf '$($directoryUpload.RemoteDir)'; mkdir -p '$RemoteStageDir'"
                )
            ) -Action "Prepare remote staging directory for scp fallback"
            $uploadPaths = @($directoryUpload.Path) + $uploadPaths
        }
    }
} else {
    Write-Host "==> rsync not found locally, using scp -r for release directories" -ForegroundColor Yellow
    $uploadPaths = @($directoryUploads | ForEach-Object { $_.Path }) + $uploadPaths
}

Invoke-External -Command "scp" -Arguments (@("-r") + $uploadPaths + @("${Target}:$RemoteStageDir/")) -Action "Upload modpack release artifacts"

Write-Host "==> Installing modpack release on remote host" -ForegroundColor Cyan
$remoteScript = $null

if ($LegacyPromptSudo) {
    $remoteCommands = [System.Collections.Generic.List[string]]::new()
    $remoteCommands.Add("set -e")
    $remoteCommands.Add("sudo -v")
    $remoteCommands.Add("mkdir -p '$RemoteServerModsDir'")
    $remoteCommands.Add("if [ -d '$RemoteStageDir/server/mods' ]; then")
    $remoteCommands.Add("  find '$RemoteServerModsDir' -mindepth 1 -maxdepth 1 ! -name 'obsidiangate-forge-auth-server-*.jar' -exec rm -rf {} +")
    $remoteCommands.Add("  cp -a '$RemoteStageDir/server/mods/.' '$RemoteServerModsDir/'")
    $remoteCommands.Add("fi")
    $remoteCommands.Add("install -m 644 '$RemoteStageDir/$serverFileName' '$RemoteServerModsDir/$serverFileName'")
    $remoteCommands.Add("if [ -d '$RemoteStageDir/server/config' ]; then")
    $remoteCommands.Add("  mkdir -p '$RemoteServerRoot/config'")
    $remoteCommands.Add("  cp -a '$RemoteStageDir/server/config/.' '$RemoteServerRoot/config/'")
    $remoteCommands.Add("fi")
    $remoteCommands.Add("if [ -d '$RemoteStageDir/server/scripts' ]; then")
    $remoteCommands.Add("  rm -rf '$RemoteServerRoot/scripts'")
    $remoteCommands.Add("  mkdir -p '$RemoteServerRoot/scripts'")
    $remoteCommands.Add("  cp -a '$RemoteStageDir/server/scripts/.' '$RemoteServerRoot/scripts/'")
    $remoteCommands.Add("fi")
    $remoteCommands.Add("sudo mkdir -p '$RemoteWebRoot'")
    $remoteCommands.Add("if command -v rsync >/dev/null 2>&1; then")
    $remoteCommands.Add("  sudo mkdir -p '$RemoteWebRoot/client'")
    $remoteCommands.Add("  sudo rsync -a --delete '$RemoteStageDir/client/' '$RemoteWebRoot/client/'")
    $remoteCommands.Add("else")
    $remoteCommands.Add("  sudo mkdir -p '$RemoteWebRoot/client'")
    $remoteCommands.Add("  sudo cp -a '$RemoteStageDir/client/.' '$RemoteWebRoot/client/'")
    $remoteCommands.Add("fi")
    $remoteCommands.Add("if [ -d '$RemoteStageDir/launcher' ]; then")
    $remoteCommands.Add("  sudo mkdir -p '$RemoteWebRoot/launcher'")
    $remoteCommands.Add("  sudo cp -a '$RemoteStageDir/launcher/.' '$RemoteWebRoot/launcher/'")
    $remoteCommands.Add("fi")
    $remoteCommands.Add("sudo install -m 644 '$RemoteStageDir/manifest.json' '$RemoteWebRoot/manifest.json'")
    $remoteCommands.Add("sha256sum '$RemoteServerModsDir/$serverFileName'")
    $remoteCommands.Add("sha256sum '$RemoteWebRoot/manifest.json'")

    if (-not $SkipRestart) {
        $remoteCommands.Add("sudo systemctl restart '$ServiceName'")
        $remoteCommands.Add("sudo systemctl status '$ServiceName' --no-pager -l")
    }

    $remoteScript = $remoteCommands -join "`n"
} else {
    $skipRestartFlag = if ($SkipRestart) { "1" } else { "0" }
    $remoteScript = "sudo -n '$RemoteDeployCommand' '$RemoteStageDir' '$serverFileName' '$RemoteServerModsDir' '$RemoteWebRoot' '$ServiceName' '$skipRestartFlag' '$RemoteServerRoot'"
}

Invoke-External -Command "ssh" -Arguments @(
    $sshTtyArgs +
    @(
        $Target,
        $remoteScript
    )
) -Action "Install modpack web files and restart service"

if (Test-Path $launcherDirPath) {
    Write-Host "==> Verifying launcher update artifact in web root" -ForegroundColor Cyan
    $remoteLauncherVerifyScript = @"
set -e
if [ -d '$RemoteStageDir/launcher' ]; then
  if mkdir -p '$RemoteWebRoot/launcher' 2>/dev/null && cp -a '$RemoteStageDir/launcher/.' '$RemoteWebRoot/launcher/' 2>/dev/null; then
    :
  else
    sudo mkdir -p '$RemoteWebRoot/launcher'
    sudo cp -a '$RemoteStageDir/launcher/.' '$RemoteWebRoot/launcher/'
  fi
fi
test -f '$RemoteWebRoot/launcher/$launcherUpdateFileName'
sha256sum '$RemoteWebRoot/launcher/$launcherUpdateFileName'
"@

    Invoke-External -Command "ssh" -Arguments @(
        $sshTtyArgs +
        @(
            $Target,
            $remoteLauncherVerifyScript
        )
    ) -Action "Verify launcher update artifact"
}

Write-Host ""
Write-Host "Modpack deploy complete for $Target" -ForegroundColor Green
