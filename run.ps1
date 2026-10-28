$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
. ./scripts/resolve-java.ps1
if (Test-Path '.env') {
  foreach ($line in Get-Content '.env') {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1],$matches[2].Trim().Trim('"'),'Process') }
  }
}
$jar='release/rain-cafe.jar'
if (!(Test-Path $jar)) { throw 'Run ./start.ps1 to build the application first, or use the packaged release.' }
Write-Host 'Rain Cafe: http://localhost:8080 (Ctrl+C to stop)'
java -jar $jar
