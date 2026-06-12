# FlowPay Core - Docker Shutdown Script for Windows
# Stops all services and optionally removes volumes

Write-Host "Stopping FlowPay Core Services..." -ForegroundColor Yellow

$removeVolumes = Read-Host "`nDo you want to remove volumes (database data will be lost)? (y/N)"

if ($removeVolumes -eq "y" -or $removeVolumes -eq "Y") {
    Write-Host "`nStopping and removing containers with volumes..." -ForegroundColor Red
    docker-compose down -v
    Write-Host "All services and volumes removed." -ForegroundColor Red
} else {
    Write-Host "`nStopping containers (keeping volumes)..." -ForegroundColor Yellow
    docker-compose down
    Write-Host "Services stopped. Data volumes preserved." -ForegroundColor Green
}

Write-Host "`nFlowPay Core services stopped successfully." -ForegroundColor Green
