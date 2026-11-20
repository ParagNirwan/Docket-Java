# Equivalent to `npm start` and start.bat — keep all three in sync.
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "Starting Docket (PostgreSQL + Spring Boot + React + Mailpit)" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
docker compose up --build --remove-orphans
