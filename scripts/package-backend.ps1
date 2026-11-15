# Builds the backend deployment package: dist\stock-list-backend-<version>.zip
# Contents: app.jar, Dockerfile, docker-compose.prod.yml, Caddyfile, .env.example, backup.sh, restore.sh,
#           db\migration\*.sql (applied automatically by the backend), docs\ (guides + openapi.json)
# Usage (PowerShell, from anywhere):  .\scripts\package-backend.ps1
# Run the tests first (.\mvnw.cmd test in backend\): they also write backend\target\openapi.json.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$version = "1.0.0"
$name = "stock-list-backend-$version"
$dist = Join-Path $root "dist"
$out = Join-Path $dist $name

Push-Location (Join-Path $root "backend")
& .\mvnw.cmd -q -DskipTests package
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Maven package failed" }
Pop-Location

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out, "$out\db\migration", "$out\docs" | Out-Null

Copy-Item (Join-Path $root "backend\target\stock-list-backend-$version.jar") "$out\app.jar"
Copy-Item (Join-Path $root "deploy\*") $out -Force
Copy-Item (Join-Path $root "deploy\.env.example") $out -Force
Copy-Item (Join-Path $root "backend\src\main\resources\db\migration\*.sql") "$out\db\migration"
Copy-Item (Join-Path $root "docs\*.md") "$out\docs"
$api = Join-Path $root "backend\target\openapi.json"
if (Test-Path $api) { Copy-Item $api "$out\docs\openapi.json" } else { Write-Warning "openapi.json missing: run the backend tests first" }

# Shell scripts must keep Unix line endings for the Linux server.
foreach ($f in "backup.sh", "restore.sh") {
    $p = Join-Path $out $f
    $text = [IO.File]::ReadAllText($p) -replace "`r`n", "`n"
    [IO.File]::WriteAllText($p, $text, (New-Object Text.UTF8Encoding $false))
}

$zip = Join-Path $dist "$name.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path "$out\*" -DestinationPath $zip
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
"$hash  $name.zip" | Set-Content -Encoding ascii (Join-Path $dist "$name.zip.sha256")
Write-Host "Package: $zip" -ForegroundColor Green
Write-Host "SHA-256: $hash"
