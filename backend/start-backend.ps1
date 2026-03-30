$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceFile = Join-Path $projectRoot 'backend/src/main/java/com/smartcomplaintsystem/SimpleBackendServer.java'
$outDir = Join-Path $projectRoot 'backend/out'
$libDir = Join-Path $projectRoot 'backend/lib'
$mysqlJar = Join-Path $libDir 'mysql-connector-j-9.3.0.jar'
$mysqlUrl = 'https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.3.0/mysql-connector-j-9.3.0.jar'

if (-not (Test-Path $sourceFile)) {
  throw "Source file not found: $sourceFile"
}

New-Item -ItemType Directory -Path $outDir -Force | Out-Null
New-Item -ItemType Directory -Path $libDir -Force | Out-Null

if (-not (Test-Path $mysqlJar)) {
  Write-Host 'Downloading MySQL JDBC driver...'
  Invoke-WebRequest -Uri $mysqlUrl -OutFile $mysqlJar
}

javac -cp $mysqlJar -d $outDir $sourceFile
if ($LASTEXITCODE -ne 0) {
  throw 'Compilation failed.'
}

Write-Host 'Starting backend with MySQL support...'
java -cp "$outDir;$mysqlJar" com.smartcomplaintsystem.SimpleBackendServer
