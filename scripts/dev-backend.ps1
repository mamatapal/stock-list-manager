# Starts the local MySQL (Docker) and runs the backend for development on http://localhost:8080
# Usage (PowerShell):  .\scripts\dev-backend.ps1
# Development only: Swagger is on and password-reset codes are written to this window.
$ErrorActionPreference = "Continue"   # exit codes are checked explicitly; docker writes normal messages to stderr
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

docker compose -f docker-compose.dev.yml up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "Could not start the database container (see the error above)." -ForegroundColor Red
    exit 1
}
Write-Host "Waiting for MySQL to accept connections..."
for ($i = 0; $i -lt 60; $i++) {
    docker compose -f docker-compose.dev.yml exec -T mysql mysqladmin ping -h 127.0.0.1 -ustocklist -pstocklist-dev --silent 2>$null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 2
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "MySQL did not start. Check Docker Desktop and the messages above." -ForegroundColor Red
    exit 1
}

# A fresh random signing key for this session (everyone signs in again after a restart, which is fine for development).
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$env:JWT_SIGNING_KEY = [Convert]::ToBase64String($bytes)
$env:DB_URL = "jdbc:mysql://localhost:3307/stocklist?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USER = "stocklist"
$env:DB_PASSWORD = "stocklist-dev"
$env:SWAGGER_ENABLED = "true"
$env:MAIL_LOG_TOKENS = "true"

Set-Location (Join-Path $root "backend")
Write-Host "Backend starting. Swagger UI: http://localhost:8080/swagger-ui.html   (Ctrl+C to stop)"
.\mvnw.cmd spring-boot:run
