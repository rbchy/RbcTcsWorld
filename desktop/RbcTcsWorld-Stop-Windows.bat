@echo off
REM Stops the store; the database volume is kept.
cd /d "%~dp0.."
docker compose --profile app down
pause
