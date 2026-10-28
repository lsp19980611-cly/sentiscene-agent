$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
. ./scripts/resolve-java.ps1
if (Test-Path '.env') {
  foreach ($line in Get-Content '.env') {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1],$matches[2].Trim().Trim('"'), 'Process') }
  }
}
Push-Location frontend
try { npm.cmd ci --no-audit --no-fund; if($LASTEXITCODE){throw 'npm ci failed'}; npm.cmd run build; if($LASTEXITCODE){throw 'Frontend build failed'} }
finally { Pop-Location }
New-Item -ItemType Directory -Force backend/src/main/resources/static | Out-Null
Copy-Item frontend/dist/* backend/src/main/resources/static -Recurse -Force
Push-Location backend
try { mvn.cmd -B verify; if($LASTEXITCODE){throw 'Backend build failed; Java 21 is required'}; java -jar target/rain-cafe-1.0.0.jar }
finally { Pop-Location }
