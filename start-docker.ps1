# FlowPay Core - Docker Startup Script for Windows
# Starts all services using docker-compose

Write-Host "Starting FlowPay Core Services..." -ForegroundColor Green

# Check if .env file exists
if (-Not (Test-Path ".env")) {
    Write-Host "Warning: .env file not found. Copying from .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
    Write-Host "Please update .env file with your configuration." -ForegroundColor Yellow
}

# Start all services
Write-Host "`nStarting Docker Compose services..." -ForegroundColor Cyan
docker-compose up -d

# Wait for services to be healthy
Write-Host "`nWaiting for services to be healthy..." -ForegroundColor Cyan
Start-Sleep -Seconds 10

# Show service status
Write-Host "`nService Status:" -ForegroundColor Green
docker-compose ps

Write-Host "`n==================================================" -ForegroundColor Green
Write-Host "FlowPay Core is starting up!" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host "Application URL: http://localhost:8080" -ForegroundColor Cyan
Write-Host "Health Check: http://localhost:8080/actuator/health" -ForegroundColor Cyan
Write-Host "Metrics: http://localhost:8080/actuator/metrics" -ForegroundColor Cyan
Write-Host "`nView logs with: docker-compose logs -f flowpay-app" -ForegroundColor Yellow
Write-Host "Stop services with: docker-compose down" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Green
