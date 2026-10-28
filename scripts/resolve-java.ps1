$candidates = @()
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$currentJava = Get-Command java -ErrorAction SilentlyContinue
if ($currentJava) { $candidates += (Split-Path (Split-Path $currentJava.Source)) }
foreach ($base in @('C:/Program Files/Java','C:/Program Files/Eclipse Adoptium','C:/Program Files/Microsoft','C:/Users/'+$env:USERNAME+'/.jdks')) {
  if (Test-Path $base) { $candidates += Get-ChildItem $base -Directory | Select-Object -ExpandProperty FullName }
}
foreach ($base in @('C:/Program Files/JetBrains','D:/Program Files/JetBrains')) {
  if (Test-Path $base) { foreach ($ide in Get-ChildItem $base -Directory) { $candidates += Join-Path $ide.FullName 'jbr' } }
}
foreach ($candidate in $candidates | Select-Object -Unique) {
  $javaBin = Join-Path $candidate 'bin/java.exe'
  if (!(Test-Path $javaBin)) { continue }
  $version = (& $javaBin -version 2>&1 | Out-String)
  if ($version -match 'version "(\d+)' -and [int]$matches[1] -ge 21) {
    $env:JAVA_HOME=$candidate
    $env:PATH=(Join-Path $candidate 'bin')+';'+$env:PATH
    return
  }
}
throw 'Java 21 or newer is required. Install a JDK and set JAVA_HOME, or use docker compose up --build.'
