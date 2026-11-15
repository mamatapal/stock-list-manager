# Runs every Phase 1 check and installs the three test copies of the app on the running emulator.
# Usage (PowerShell, with Docker Desktop and the emulator running):
#   powershell -ExecutionPolicy Bypass -File .\scripts\verify-phase1.ps1
# Logs: backend\build-log.txt and android\build-log.txt (Claude reads these).
$root = Split-Path -Parent $PSScriptRoot

Write-Host "1/2 Backend tests (MySQL in Docker)..." -ForegroundColor Cyan
Set-Location (Join-Path $root "backend")
.\mvnw.cmd test *> build-log.txt
if ($LASTEXITCODE -ne 0) { Write-Host "Backend tests FAILED - see backend\build-log.txt" -ForegroundColor Red; exit 1 }
Write-Host "Backend tests passed." -ForegroundColor Green

Write-Host "2/2 Android unit tests and install of 3 copies (Stock List Manager, SLM Phone B, SLM Phone C)..." -ForegroundColor Cyan
Set-Location (Join-Path $root "android")
$env:JAVA_HOME = Join-Path $HOME ".jdks\jbr-21.0.11"
.\gradlew.bat --stop *> $null
.\gradlew.bat testDebugUnitTest installDebug installDevB installDevC *> build-log.txt
if ($LASTEXITCODE -ne 0) { Write-Host "Android build/install FAILED - see android\build-log.txt (is the emulator running?)" -ForegroundColor Red; exit 1 }
Write-Host "Done. Three copies of the app are installed on the emulator." -ForegroundColor Green
