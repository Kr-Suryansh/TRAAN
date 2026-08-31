#!/usr/bin/env pwsh

Write-Host "Tearing down existing database container..."
docker compose down -v

Write-Host "Starting fresh PostGIS container..."
docker compose up -d db

Write-Host "Waiting for database to be ready..."
Start-Sleep -Seconds 5

Write-Host "Activating virtual environment..."
. .\.venv\Scripts\Activate.ps1

Write-Host "Running Alembic migrations..."
$env:PYTHONPATH = "."
alembic upgrade head

Write-Host "Running tests..."
pytest -v

Write-Host "Verification complete. Tearing down..."
docker compose down -v
