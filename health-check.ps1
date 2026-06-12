# FlowPay Core - Health Check Script
# Monitors the health of all services

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "FlowPay Core - Service Health Check" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# Check if services are running
Write-Host "`nChecking Docker services..." -ForegroundColor Yellow
$services = docker-compose ps --services --filter "status=running"

if ($services) {
    Write-Host "Running services:" -ForegroundColor Green
    docker-compose ps
} else {
    Write-Host "No services are running!" -ForegroundColor Red
    exit 1
}

# Check application health
Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "Checking Application Health Endpoint..." -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -Method Get -TimeoutSec 5
    $healthData = $response.Content | ConvertFrom-Json
    
    Write-Host "`nApplication Status: $($healthData.status)" -ForegroundColor Green
    Write-Host "`nDetailed Health Information:" -ForegroundColor Cyan
    Write-Host ($response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10)
    
} catch {
    Write-Host "`nApplication health check failed!" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}

# Check database connectivity
Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "Checking Database Connectivity..." -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan

$postgresHealth = docker exec flowpay-postgres pg_isready -U flowpay -d flowpay 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "PostgreSQL: $postgresHealth" -ForegroundColor Green
} else {
    Write-Host "PostgreSQL: Not ready" -ForegroundColor Red
}

# Check Redis connectivity
Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "Checking Redis Connectivity..." -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan

$redisHealth = docker exec flowpay-redis redis-cli ping 2>&1
if ($redisHealth -eq "PONG") {
    Write-Host "Redis: $redisHealth" -ForegroundColor Green
} else {
    Write-Host "Redis: Not responding" -ForegroundColor Red
}

# Check Kafka connectivity
Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "Checking Kafka Connectivity..." -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan

$kafkaHealth = docker exec flowpay-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "Kafka: Connected" -ForegroundColor Green
} else {
    Write-Host "Kafka: Not responding" -ForegroundColor Red
}

# Show application logs (last 20 lines)
Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "Recent Application Logs (last 20 lines):" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan
docker-compose logs --tail=20 flowpay-app

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "Health check complete!" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Cyan
