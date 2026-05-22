$ROOT = Split-Path -Parent $PSScriptRoot
Set-Location $ROOT

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " REGISTRO WALLET"
Write-Host "=====================================" -ForegroundColor Cyan
Get-Content Registro.json

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " REGISTRO BANCO BOB"
Write-Host "=====================================" -ForegroundColor Cyan
Get-Content data\bancoBob\RegistroBanco.json

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " REGISTRO BANCO ALICE"
Write-Host "=====================================" -ForegroundColor Cyan
Get-Content data\bancoAlice\RegistroBanco.json

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " REGISTRO BANCO CENTRAL"
Write-Host "=====================================" -ForegroundColor Cyan
Get-Content data\bancoCentral\RegistroBancoCentral.json