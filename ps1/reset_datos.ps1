$ROOT = Split-Path -Parent $PSScriptRoot
Set-Location $ROOT

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " REINICIANDO DATOS DE PRUEBA"
Write-Host "=====================================" -ForegroundColor Cyan

Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force

New-Item -ItemType Directory -Force data | Out-Null
New-Item -ItemType Directory -Force data\bancoCentral | Out-Null
New-Item -ItemType Directory -Force data\bancoBob | Out-Null
New-Item -ItemType Directory -Force data\bancoAlice | Out-Null

@'
{
  "BancoBob": {
    "MasterKey": "9942koofffo",
    "SecWallet": "Secreto-BancoBob-APP",
    "SecBanco": "Secreto-BancoBob",
    "FondosEC": "50000",
    "LWEurocoins": "10000",
    "HWEurocoins": "100000"
  },
  "PedroLuis": {
    "MasterKey": "9942koofffo",
    "SecWallet": "Secreto-PedroLuis",
    "SecBanco": "Secreto-BancoBob",
    "Saldo": "1500",
    "FondosEC": "500"
  }
}
'@ | Set-Content -Encoding UTF8 data\bancoBob\RegistroBanco.json

@'
{
  "BancoAlice": {
    "MasterKey": "9942koofffo",
    "SecWallet": "Secreto-BancoAlice-APP",
    "SecBanco": "Secreto-BancoAlice",
    "FondosEC": "60000",
    "LWEurocoins": "10000",
    "HWEurocoins": "100000"
  },
  "AngelAntonio": {
    "MasterKey": "9942koofffo",
    "SecWallet": "Secreto-AngelAntonio",
    "SecBanco": "Secreto-BancoAlice",
    "Saldo": "1500",
    "FondosEC": "500"
  }
}
'@ | Set-Content -Encoding UTF8 data\bancoAlice\RegistroBanco.json

@'
{}
'@ | Set-Content -Encoding UTF8 data\bancoCentral\RegistroBancoCentral.json

@'
{
  "MasterKey": "9942koofffo",
  "FuncionHash": "SHA-256",
  "PasswordSalt": "abcd0987",
  "PasswordHash": "d169bb0f45c9dc5480e7ff107ffa9091002ea45204637db0b8c7a97748af402a",
  "usuario": "PedroLuis",
  "SecWallet": "Secreto-PedroLuis",
  "SecBanco": "Secreto-BancoBob",
  "FondosEC": "500",
  "IngresosEC": "0"
}
'@ | Set-Content -Encoding UTF8 Registro.json

New-Item -ItemType File -Force data\bancoBob\MovimientosBanco.txt | Out-Null
Clear-Content data\bancoBob\MovimientosBanco.txt

New-Item -ItemType File -Force data\bancoAlice\MovimientosBanco.txt | Out-Null
Clear-Content data\bancoAlice\MovimientosBanco.txt

New-Item -ItemType File -Force Movimientos.txt | Out-Null
Clear-Content Movimientos.txt

Write-Host "Datos reiniciados correctamente." -ForegroundColor Green