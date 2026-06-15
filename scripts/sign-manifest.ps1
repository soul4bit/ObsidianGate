[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ManifestPath,

    [string]$PrivateKeyPath = $env:OBSIDIANGATE_MANIFEST_PRIVATE_KEY
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not [System.IO.Path]::IsPathRooted($ManifestPath)) {
    $ManifestPath = Join-Path $repoRoot $ManifestPath
}
if ([string]::IsNullOrWhiteSpace($PrivateKeyPath)) {
    $PrivateKeyPath = Join-Path $HOME ".obsidiangate-release/manifest-ed25519-private.pem"
} elseif (-not [System.IO.Path]::IsPathRooted($PrivateKeyPath)) {
    $PrivateKeyPath = Join-Path $repoRoot $PrivateKeyPath
}

if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    throw "Manifest not found: $ManifestPath"
}
if (-not (Test-Path -LiteralPath $PrivateKeyPath -PathType Leaf)) {
    throw @"
Manifest signing key not found: $PrivateKeyPath
Set OBSIDIANGATE_MANIFEST_PRIVATE_KEY or run scripts/initialize-manifest-signing-key.ps1.
"@
}

& mvn "-q" "-f" (Join-Path $repoRoot "pom.xml") "-DskipTests" "compile"
if ($LASTEXITCODE -ne 0) {
    throw "Could not compile the manifest signing tool."
}

& java "-cp" (Join-Path $repoRoot "target/classes") `
    "ru.mcrpg.launcher.ManifestSignatureTool" `
    "sign" `
    $PrivateKeyPath `
    $ManifestPath
if ($LASTEXITCODE -ne 0) {
    throw "Manifest signing failed."
}

Write-Host "Signed manifest: $ManifestPath.sig" -ForegroundColor Green
