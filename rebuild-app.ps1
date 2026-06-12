# FlowPay Core - Docker Build Script for Windows
# Rebuilds the application image and restarts services

Write-Host "Rebuilding FlowPay Core Application..." -ForegroundColor Green

# Stop the application container
Write-Host "`nStopping application container..." -ForegroundColor Yellow
docker-compose stop flowpay-app

# Rebuild the image
Write-Host "`nBuilding new image..." -ForegroundColor Cyan
docker-compose build --no-cache flowpay-app

# Start the application
Write-Host "`nStarting application..." -ForegroundColor Green
docker-compose up -d flowpay-app

# Show logs
Write-Host "`nApplication rebuild complete. Showing logs..." -ForegroundColor Green
Start-Sleep -Seconds 3
docker-compose logs -f flowpay-app
