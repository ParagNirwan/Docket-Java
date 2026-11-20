@echo off
REM Equivalent to `npm start` and start.ps1 — keep all three in sync.
echo ===================================================
echo Starting Docket (PostgreSQL + Spring Boot + React + Mailpit)
echo ===================================================
docker compose up --build --remove-orphans
