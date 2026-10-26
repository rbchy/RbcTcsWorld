@echo off
REM RbcTcsWorld Shop - double-click to start the whole store and open it (Windows, Docker Desktop).
cd /d "%~dp0.."
docker info >nul 2>&1
if errorlevel 1 (
  echo Starting Docker Desktop...
  start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
  for /l %%i in (1,1,90) do (
    timeout /t 2 >nul
    docker info >nul 2>&1 && goto docker_ready
  )
  echo Docker Desktop did not start. Open it by hand, then run this launcher again.
  pause
  exit /b 1
)
:docker_ready
echo Starting RbcTcsWorld (database, backend, storefront)...
docker compose --profile app up -d --build --wait --wait-timeout 300
if errorlevel 1 (
  docker compose --profile app logs --no-color --tail=40
  pause
  exit /b 1
)
start "" http://localhost:5173
echo Ready: http://localhost:5173  -  in Chrome or Edge press "Install app" for a desktop window.
pause
