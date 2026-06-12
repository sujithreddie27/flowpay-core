# FlowPay Core - Docker Management Helper
# Provides common docker operations

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet('start', 'stop', 'restart', 'logs', 'build', 'clean', 'health', 'status', 'shell')]
    [string]$Action = 'help'
)

function Show-Help {
    Write-Host "`nFlowPay Core - Docker Management Helper" -ForegroundColor Cyan
    Write-Host "========================================`n" -ForegroundColor Cyan
    Write-Host "Usage: .\docker-helper.ps1 [action]`n" -ForegroundColor Yellow
    Write-Host "Available actions:" -ForegroundColor Green
    Write-Host "  start     - Start all services" -ForegroundColor White
    Write-Host "  stop      - Stop all services" -ForegroundColor White
    Write-Host "  restart   - Restart all services" -ForegroundColor White
    Write-Host "  logs      - View application logs" -ForegroundColor White
    Write-Host "  build     - Rebuild application image" -ForegroundColor White
    Write-Host "  clean     - Stop and remove all containers and volumes" -ForegroundColor White
    Write-Host "  health    - Check health of all services" -ForegroundColor White
    Write-Host "  status    - Show status of all containers" -ForegroundColor White
    Write-Host "  shell     - Open shell in application container" -ForegroundColor White
    Write-Host "`nExample: .\docker-helper.ps1 start`n" -ForegroundColor Yellow
}

function Start-Services {
    Write-Host "Starting FlowPay Core services..." -ForegroundColor Green
    docker-compose up -d
    Write-Host "`nServices started. Use '.\docker-helper.ps1 status' to check status." -ForegroundColor Green
}

function Stop-Services {
    Write-Host "Stopping FlowPay Core services..." -ForegroundColor Yellow
    docker-compose down
    Write-Host "Services stopped." -ForegroundColor Green
}

function Restart-Services {
    Write-Host "Restarting FlowPay Core services..." -ForegroundColor Yellow
    docker-compose restart
    Write-Host "Services restarted." -ForegroundColor Green
}

function Show-Logs {
    Write-Host "Showing FlowPay application logs (Ctrl+C to exit)..." -ForegroundColor Cyan
    docker-compose logs -f flowpay-app
}

function Build-Application {
    Write-Host "Rebuilding FlowPay application..." -ForegroundColor Cyan
    docker-compose build --no-cache flowpay-app
    Write-Host "Build complete." -ForegroundColor Green
}

function Clean-All {
    $confirm = Read-Host "This will remove all containers and volumes. Are you sure? (yes/no)"
    if ($confirm -eq "yes") {
        Write-Host "Cleaning up..." -ForegroundColor Red
        docker-compose down -v --remove-orphans
        Write-Host "Cleanup complete." -ForegroundColor Green
    } else {
        Write-Host "Cleanup cancelled." -ForegroundColor Yellow
    }
}

function Check-Health {
    & .\health-check.ps1
}

function Show-Status {
    Write-Host "Container Status:" -ForegroundColor Cyan
    docker-compose ps
}

function Open-Shell {
    Write-Host "Opening shell in application container..." -ForegroundColor Cyan
    docker exec -it flowpay-app sh
}

# Execute action
switch ($Action) {
    'start'   { Start-Services }
    'stop'    { Stop-Services }
    'restart' { Restart-Services }
    'logs'    { Show-Logs }
    'build'   { Build-Application }
    'clean'   { Clean-All }
    'health'  { Check-Health }
    'status'  { Show-Status }
    'shell'   { Open-Shell }
    default   { Show-Help }
}
