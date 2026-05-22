$ROOT = Split-Path -Parent $PSScriptRoot
Set-Location $ROOT

Write-Host "Parando procesos Java..." -ForegroundColor Yellow

Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force

Write-Host "Procesos Java detenidos." -ForegroundColor Green