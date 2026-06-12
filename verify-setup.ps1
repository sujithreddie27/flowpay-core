# FlowPay Core - Setup Verification Script
# Verifies that all Docker and configuration files are properly set up

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "FlowPay Core - Setup Verification" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

$allChecksPass = $true

# Function to check if file exists
function Test-FileExists {
    param($filePath, $description)
    
    if (Test-Path $filePath) {
        Write-Host "[OK] $description" -ForegroundColor Green
        return $true
    } else {
        Write-Host "[FAIL] $description - File not found: $filePath" -ForegroundColor Red
        return $false
    }
}

Write-Host "`n1. Checking Docker Configuration Files..." -ForegroundColor Yellow
Write-Host "-------------------------------------------" -ForegroundColor Gray

$allChecksPass = (Test-FileExists ".\Dockerfile" "Dockerfile") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\docker-compose.yml" "docker-compose.yml") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\docker-compose.prod.yml" "docker-compose.prod.yml") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\.dockerignore" ".dockerignore") -and $allChecksPass

Write-Host "`n2. Checking Environment Files..." -ForegroundColor Yellow
Write-Host "-------------------------------------------" -ForegroundColor Gray

$allChecksPass = (Test-FileExists ".\.env" ".env file") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\.env.example" ".env.example template") -and $allChecksPass

Write-Host "`n3. Checking Helper Scripts..." -ForegroundColor Yellow
Write-Host "-------------------------------------------" -ForegroundColor Gray

$allChecksPass = (Test-FileExists ".\start-docker.ps1" "Start Docker script") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\stop-docker.ps1" "Stop Docker script") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\rebuild-app.ps1" "Rebuild App script") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\health-check.ps1" "Health Check script") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\docker-helper.ps1" "Docker Helper script") -and $allChecksPass

Write-Host "`n4. Checking Application Configuration..." -ForegroundColor Yellow
Write-Host "-------------------------------------------" -ForegroundColor Gray

$allChecksPass = (Test-FileExists ".\src\main\resources\application.yml" "application.yml") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\src\main\resources\application-dev.yml" "application-dev.yml") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\src\main\resources\application-prod.yml" "application-prod.yml") -and $allChecksPass

Write-Host "`n5. Checking Health Indicators..." -ForegroundColor Yellow
Write-Host "-------------------------------------------" -ForegroundColor Gray

$allChecksPass = (Test-FileExists ".\src\main\java\com\flowpay\monitoring\health\DatabaseHealthIndicator.java" "Database Health Indicator") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\src\main\java\com\flowpay\monitoring\health\RedisHealthIndicator.java" "Redis Health Indicator") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\src\main\java\com\flowpay\monitoring\health\KafkaHealthIndicator.java" "Kafka Health Indicator") -and $allChecksPass

Write-Host "`n6. Checking Configuration Classes..." -ForegroundColor Yellow
Write-Host "-------------------------------------------" -ForegroundColor Gray

$allChecksPass = (Test-FileExists ".\src\main\java\com\flowpay\config\ActuatorConfig.java" "Actuator Configuration") -and $allChecksPass
$allChecksPass = (Test-FileExists ".\src\main\java\com\flowpay\monitoring\info\ApplicationInfoContributor.java" "Info Contributor") -and $allChecksPass

Write-Host "`n7. Checking Docker Prerequisites..." -ForegroundColor Yellow
Write-Host "-------------------------------------------" -ForegroundColor Gray

# Check if Docker is installed
try {
    $dockerVersion = docker --version 2>&1
    Write-Host "[OK] Docker installed: $dockerVersion" -ForegroundColor Green
} catch {
    Write-Host "[FAIL] Docker is not installed or not in PATH" -ForegroundColor Red
    $allChecksPass = $false
}

# Check if Docker Compose is available
try {
    $composeVersion = docker-compose --version 2>&1
    Write-Host "[OK] Docker Compose installed: $composeVersion" -ForegroundColor Green
} catch {
    Write-Host "[FAIL] Docker Compose is not installed or not in PATH" -ForegroundColor Red
    $allChecksPass = $false
}

# Check if Docker daemon is running
try {
    docker info | Out-Null
    Write-Host "[OK] Docker daemon is running" -ForegroundColor Green
} catch {
    Write-Host "[FAIL] Docker daemon is not running. Please start Docker Desktop." -ForegroundColor Red
    $allChecksPass = $false
}

Write-Host "`n8. Checking Maven Setup..." -ForegroundColor Yellow
Write-Host "-------------------------------------------" -ForegroundColor Gray

# Check if Maven is installed
try {
    $mavenVersion = mvn --version 2>&1 | Select-Object -First 1
    Write-Host "[OK] Maven installed: $mavenVersion" -ForegroundColor Green
} catch {
    Write-Host "[WARNING] Maven not found in PATH (required for local builds)" -ForegroundColor Yellow
}

Write-Host "`n==================================================" -ForegroundColor Cyan
if ($allChecksPass) {
    Write-Host "All checks passed! Setup is complete." -ForegroundColor Green
    Write-Host "`nNext steps:" -ForegroundColor Cyan
    Write-Host "  1. Review and update .env file with your configuration" -ForegroundColor White
    Write-Host "  2. Run: .\start-docker.ps1 to start all services" -ForegroundColor White
    Write-Host "  3. Run: .\health-check.ps1 to verify all services are healthy" -ForegroundColor White
    Write-Host "  4. Access application at: http://localhost:8080" -ForegroundColor White
    Write-Host "  5. Check health at: http://localhost:8080/actuator/health" -ForegroundColor White
} else {
    Write-Host "Some checks failed. Please review the errors above." -ForegroundColor Red
}
Write-Host "==================================================" -ForegroundColor Cyan
