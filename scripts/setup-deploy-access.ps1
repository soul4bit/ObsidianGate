[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$Target = "minecraft@your.server.host",
    [string]$HostAlias = "mc-rpg-deploy",
    [string]$HostName = "your.server.host",
    [string]$User = "minecraft",
    [string]$IdentityFile = "~/.ssh/id_ed25519",
    [string]$RemoteDeployCommand = "/usr/local/bin/obsidiangate-deploy",
    [string]$RemoteSudoersPath = "/etc/sudoers.d/obsidiangate-deploy",
    [switch]$SkipSshConfig
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$wrapperScriptPath = Join-Path $PSScriptRoot "obsidiangate-remote-deploy.sh"

foreach ($command in @("ssh", "scp")) {
    $null = Get-Command $command -ErrorAction Stop
}

function Expand-UserPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    if ($Value.StartsWith("~/")) {
        return Join-Path $env:USERPROFILE $Value.Substring(2)
    }

    return $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Value)
}

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

$identityFileFullPath = Expand-UserPath $IdentityFile
$publicKeyPath = "$identityFileFullPath.pub"

if (-not (Test-Path $identityFileFullPath)) {
    throw "SSH private key not found: $identityFileFullPath"
}

if (-not (Test-Path $publicKeyPath)) {
    throw "SSH public key not found: $publicKeyPath"
}

if (-not (Test-Path $wrapperScriptPath)) {
    throw "Remote wrapper script not found: $wrapperScriptPath"
}

if (-not $SkipSshConfig) {
    $sshDir = Join-Path $env:USERPROFILE ".ssh"
    $sshConfigPath = Join-Path $sshDir "config"
    $identityForConfig = $identityFileFullPath.Replace('\', '/')
    $configEntry = @"
Host $HostAlias
    HostName $HostName
    User $User
    IdentityFile $identityForConfig
    IdentitiesOnly yes

"@

    if (-not (Test-Path $sshDir)) {
        $null = New-Item -ItemType Directory -Path $sshDir -Force
    }

    if (-not (Test-Path $sshConfigPath)) {
        Set-Content -Path $sshConfigPath -Value $configEntry -NoNewline
    } else {
        $configContent = Get-Content $sshConfigPath -Raw
        if ($configContent -notmatch "(?m)^Host\s+$([regex]::Escape($HostAlias))\s*$") {
            Add-Content -Path $sshConfigPath -Value ("`r`n" + $configEntry)
        }
    }

    Write-Host "SSH config updated: $sshConfigPath" -ForegroundColor Green
}

$tempDir = Join-Path $env:TEMP ("obsidiangate-deploy-" + [guid]::NewGuid().ToString("N"))
$null = New-Item -ItemType Directory -Path $tempDir -Force
$tempPubKeyPath = Join-Path $tempDir "deploy-key.pub"
$tempSudoersPath = Join-Path $tempDir "obsidiangate-deploy.sudoers"

try {
    Copy-Item $publicKeyPath $tempPubKeyPath -Force

    $sudoersContent = @"
$User ALL=(root) NOPASSWD: $RemoteDeployCommand *
"@
    Set-Content -Path $tempSudoersPath -Value $sudoersContent -NoNewline

    Write-Host "==> Uploading SSH public key" -ForegroundColor Cyan
    Invoke-External -Command "scp" -Arguments @(
        $tempPubKeyPath,
        "${Target}:~/obsidiangate-deploy-key.pub"
    ) -Action "Upload SSH public key"

    Write-Host "==> Installing SSH public key" -ForegroundColor Cyan
    $installKeyScript = @"
set -e
umask 077
mkdir -p ~/.ssh
touch ~/.ssh/authorized_keys
grep -qxF "`$(cat ~/obsidiangate-deploy-key.pub)" ~/.ssh/authorized_keys || cat ~/obsidiangate-deploy-key.pub >> ~/.ssh/authorized_keys
rm -f ~/obsidiangate-deploy-key.pub
"@
    Invoke-External -Command "ssh" -Arguments @(
        "-tt",
        $Target,
        $installKeyScript
    ) -Action "Install SSH public key"

    Write-Host "==> Uploading remote deploy wrapper and sudoers file" -ForegroundColor Cyan
    Invoke-External -Command "scp" -Arguments @(
        $wrapperScriptPath,
        $tempSudoersPath,
        "${Target}:~/"
    ) -Action "Upload wrapper and sudoers file"

    Write-Host "==> Installing remote deploy wrapper and sudoers file" -ForegroundColor Cyan
    $remoteBootstrapScript = @"
set -e
sudo install -m 755 ~/obsidiangate-remote-deploy.sh '$RemoteDeployCommand'
sudo install -m 440 ~/obsidiangate-deploy.sudoers '$RemoteSudoersPath'
sudo visudo -cf '$RemoteSudoersPath'
rm -f ~/obsidiangate-remote-deploy.sh ~/obsidiangate-deploy.sudoers
sudo -n '$RemoteDeployCommand' --self-test
"@
    Invoke-External -Command "ssh" -Arguments @(
        "-tt",
        $Target,
        $remoteBootstrapScript
    ) -Action "Install deploy wrapper and passwordless sudo rule"

    Write-Host ""
    Write-Host "Bootstrap complete." -ForegroundColor Green
    Write-Host "SSH alias: $HostAlias"
    Write-Host "Next deploy command:"
    Write-Host "powershell -ExecutionPolicy Bypass -File .\scripts\publish-modpack.ps1 -Target $HostAlias -ManifestVersion $(Get-Date -Format 'yyyy.MM.dd')" -ForegroundColor Yellow
} finally {
    if (Test-Path $tempDir) {
        Remove-Item $tempDir -Recurse -Force
    }
}
