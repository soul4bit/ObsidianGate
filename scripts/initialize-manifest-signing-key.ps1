[CmdletBinding()]
param(
    [string]$PrivateKeyPath = (Join-Path $HOME ".obsidiangate-release/manifest-ed25519-private.pem"),
    [string]$PublicKeyPath = "src/main/resources/ru/mcrpg/launcher/security/manifest-ed25519-public.pem",
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not [System.IO.Path]::IsPathRooted($PrivateKeyPath)) {
    $PrivateKeyPath = Join-Path $repoRoot $PrivateKeyPath
}
if (-not [System.IO.Path]::IsPathRooted($PublicKeyPath)) {
    $PublicKeyPath = Join-Path $repoRoot $PublicKeyPath
}

if (-not $Force -and ((Test-Path -LiteralPath $PrivateKeyPath) -or (Test-Path -LiteralPath $PublicKeyPath))) {
    throw "Signing key already exists. Use -Force only for an intentional trust-anchor rotation."
}

& mvn "-q" "-f" (Join-Path $repoRoot "pom.xml") "-DskipTests" "compile"
if ($LASTEXITCODE -ne 0) {
    throw "Could not compile the manifest signing tool."
}

& java "-cp" (Join-Path $repoRoot "target/classes") `
    "ru.mcrpg.launcher.ManifestSignatureTool" `
    "generate" `
    $PrivateKeyPath `
    $PublicKeyPath
if ($LASTEXITCODE -ne 0) {
    throw "Manifest signing key generation failed."
}

Write-Host "Private key: $PrivateKeyPath" -ForegroundColor Green
Write-Host "Public key embedded into launcher: $PublicKeyPath" -ForegroundColor Green
Write-Warning "Back up the private key securely. Rotating it requires publishing a new launcher trust anchor."
