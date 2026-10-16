@echo off
echo ===================================================
echo Starting Docket Docker services (PostgreSQL + React)
echo ===================================================
docker compose up --build --remove-orphans
pause
