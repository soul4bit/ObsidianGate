[CmdletBinding()]
param(
    [string]$LauncherJar = "obsidian-gate-launcher.jar",
    [string]$LauncherUrl = "http://obsidiangates.duckdns.org:8080/launcher/obsidian-gate-launcher.jar",
    [int]$MinimumJavaMajor = 17,
    [string]$RuntimeUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[ObsidianGate] $Message"
}

function Get-ScriptDirectory {
    if ($PSScriptRoot) {
        return $PSScriptRoot
    }
    return Split-Path -Parent $MyInvocation.MyCommand.Path
}

function Get-JavaMajorVersion {
    param([Parameter(Mandatory = $true)][string]$JavaExe)

    if (-not (Test-Path -LiteralPath $JavaExe) -and -not (Get-Command $JavaExe -ErrorAction SilentlyContinue)) {
        return $null
    }

    try {
        $output = & $JavaExe -version 2>&1 | Out-String
    } catch {
        return $null
    }

    if ($output -notmatch 'version "([^"]+)"') {
        return $null
    }

    $version = $Matches[1]
    $parts = $version.Split(".")
    if ($parts.Length -ge 2 -and $parts[0] -eq "1") {
        return [int]$parts[1]
    }
    return [int]$parts[0]
}

function Resolve-SystemJava {
    param([int]$MinimumMajor)

    $javaCommand = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if (-not $javaCommand) {
        $javaCommand = Get-Command "java" -ErrorAction SilentlyContinue
    }
    if (-not $javaCommand) {
        return $null
    }

    $major = Get-JavaMajorVersion -JavaExe $javaCommand.Source
    if ($major -ge $MinimumMajor) {
        Write-Step "Using system Java ${major}: $($javaCommand.Source)"
        return $javaCommand.Source
    }

    Write-Step "System Java is missing or too old. Required: $MinimumMajor+."
    return $null
}

function Install-PortableRuntime {
    param(
        [Parameter(Mandatory = $true)][string]$DownloadUrl,
        [Parameter(Mandatory = $true)][string]$RuntimeDirectory
    )

    if (-not [Environment]::Is64BitOperatingSystem) {
        throw "This bootstrapper currently supports Windows x64 only."
    }

    $installRoot = Split-Path -Parent $RuntimeDirectory
    $tempRoot = Join-Path $installRoot ("runtime-download-" + [Guid]::NewGuid().ToString("N"))
    $archivePath = Join-Path $tempRoot "jre.zip"

    New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Write-Step "Downloading portable Java runtime..."
        Invoke-WebRequest -Uri $DownloadUrl -OutFile $archivePath -UseBasicParsing

        Write-Step "Extracting portable Java runtime..."
        Expand-Archive -LiteralPath $archivePath -DestinationPath $tempRoot -Force
        $java = Get-ChildItem -LiteralPath $tempRoot -Recurse -Filter "java.exe" -File |
            Where-Object { $_.FullName -match "\\bin\\java\.exe$" } |
            Select-Object -First 1

        if (-not $java) {
            throw "Downloaded runtime does not contain bin\java.exe."
        }

        $runtimeRoot = Split-Path -Parent (Split-Path -Parent $java.FullName)
        $runtimeParent = Split-Path -Parent $RuntimeDirectory
        New-Item -ItemType Directory -Force -Path $runtimeParent | Out-Null
        if (Test-Path -LiteralPath $RuntimeDirectory) {
            Remove-Item -LiteralPath $RuntimeDirectory -Recurse -Force
        }
        Move-Item -LiteralPath $runtimeRoot -Destination $RuntimeDirectory
    } finally {
        if (Test-Path -LiteralPath $tempRoot) {
            Remove-Item -LiteralPath $tempRoot -Recurse -Force
        }
    }

    $portableJava = Join-Path $RuntimeDirectory "bin\java.exe"
    $major = Get-JavaMajorVersion -JavaExe $portableJava
    if ($major -lt 17) {
        throw "Portable Java runtime is too old: $major."
    }
    Write-Step "Portable Java installed: $portableJava"
    return $portableJava
}

function Resolve-Java {
    param(
        [Parameter(Mandatory = $true)][string]$RuntimeDirectory,
        [Parameter(Mandatory = $true)][string]$DownloadUrl,
        [int]$MinimumMajor
    )

    $portableJava = Join-Path $RuntimeDirectory "bin\java.exe"
    $portableMajor = Get-JavaMajorVersion -JavaExe $portableJava
    if ($portableMajor -ge $MinimumMajor) {
        Write-Step "Using bundled Java ${portableMajor}: $portableJava"
        return $portableJava
    }

    $systemJava = Resolve-SystemJava -MinimumMajor $MinimumMajor
    if ($systemJava) {
        return $systemJava
    }

    return Install-PortableRuntime -DownloadUrl $DownloadUrl -RuntimeDirectory $RuntimeDirectory
}

function Resolve-LauncherJar {
    param(
        [Parameter(Mandatory = $true)][string]$ScriptDirectory,
        [Parameter(Mandatory = $true)][string]$InstallDirectory,
        [Parameter(Mandatory = $true)][string]$JarName,
        [Parameter(Mandatory = $true)][string]$DownloadUrl
    )

    $localJar = Join-Path $ScriptDirectory $JarName
    if (Test-Path -LiteralPath $localJar) {
        return $localJar
    }

    New-Item -ItemType Directory -Force -Path $InstallDirectory | Out-Null
    $installedJar = Join-Path $InstallDirectory $JarName
    Write-Step "Downloading launcher jar..."
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $installedJar -UseBasicParsing
    return $installedJar
}

try {
    if ($env:OS -and $env:OS -ne "Windows_NT") {
        throw "This bootstrapper is for Windows."
    }

    $scriptDirectory = Get-ScriptDirectory
    $installDirectory = Join-Path $env:LOCALAPPDATA "ObsidianGate\launcher"
    $runtimeDirectory = Join-Path $installDirectory "runtime\jre21"

    $java = Resolve-Java -RuntimeDirectory $runtimeDirectory -DownloadUrl $RuntimeUrl -MinimumMajor $MinimumJavaMajor
    $jar = Resolve-LauncherJar -ScriptDirectory $scriptDirectory -InstallDirectory $installDirectory -JarName $LauncherJar -DownloadUrl $LauncherUrl

    Write-Step "Starting launcher..."
    & $java -jar $jar @args
    exit $LASTEXITCODE
} catch {
    Write-Host ""
    Write-Host "[ObsidianGate] Launcher bootstrap failed:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    Read-Host "Press Enter to close"
    exit 1
}
