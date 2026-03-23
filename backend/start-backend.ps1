$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceFile = Join-Path $projectRoot 'backend/src/main/java/com/smartcomplaintsystem/SimpleBackendServer.java'
$outDir = Join-Path $projectRoot 'backend/out'

if (-not (Test-Path $sourceFile)) {
  throw "Source file not found: $sourceFile"
}

New-Item -ItemType Directory -Path $outDir -Force | Out-Null
javac -d $outDir $sourceFile
if ($LASTEXITCODE -ne 0) {
  throw 'Compilation failed.'
}

Write-Host 'Starting backend at http://localhost:8080 ...'
java -cp $outDir com.smartcomplaintsystem.SimpleBackendServer
