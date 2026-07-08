# FlowPay Load Test Runner
# Usage: .\run-load-test.ps1 [-Simulation "smoke"|"full"] [-BaseUrl "http://localhost:8080"] [-Token "your-jwt-token"]

param(
    [string]$Simulation = "smoke",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Token = "test-token"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  FlowPay Load Test Runner" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Simulation: $Simulation"
Write-Host "  Base URL:   $BaseUrl"
Write-Host "  Token:      $($Token.Substring(0, [Math]::Min(10, $Token.Length)))..."
Write-Host ""

$simulationClass = switch ($Simulation) {
    "smoke" { "com.flowpay.loadtest.PaymentSmokeSimulation" }
    "full"  { "com.flowpay.loadtest.PaymentLoadSimulation" }
    default { "com.flowpay.loadtest.PaymentSmokeSimulation" }
}

Write-Host "Running simulation: $simulationClass" -ForegroundColor Green
Write-Host ""

# Verify the app is accessible
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method GET -TimeoutSec 5
    Write-Host "Health check passed: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "WARNING: Could not reach $BaseUrl/actuator/health" -ForegroundColor Yellow
    Write-Host "Make sure the application is running." -ForegroundColor Yellow
    Write-Host ""
}

# Run Gatling via Maven
mvn gatling:test `
    -Dgatling.simulationClass=$simulationClass `
    -DbaseUrl=$BaseUrl `
    -DauthToken=$Token `
    -Dgatling.resultsFolder="target/gatling-results"

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Load test completed successfully!" -ForegroundColor Green
    Write-Host "  Results: target/gatling-results/" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "  Load test failed or assertions unmet" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
}
