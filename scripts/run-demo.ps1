param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (-not $env:JAVA_HOME) {
    $portableRoot = Join-Path (Split-Path -Parent $projectRoot) '.tools'
    if (Test-Path -LiteralPath $portableRoot) {
        $portableJdk = Get-ChildItem -LiteralPath $portableRoot -Directory -Filter 'jdk-21*' | Select-Object -First 1
        if ($portableJdk) { $env:JAVA_HOME = $portableJdk.FullName }
    }
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Install Java 21 and set JAVA_HOME, then run this script again.' }
Write-Host "Starting LearnReserve demo at http://localhost:$Port/swagger-ui.html"
Write-Host 'Admin: admin@learnreserve.local / DemoAdmin!2026'
Write-Host 'Learner: learner@learnreserve.local / DemoLearner!2026'
& .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo' "-Dspring-boot.run.arguments=--server.port=$Port --server.address=127.0.0.1 --debug=false"
exit $LASTEXITCODE
