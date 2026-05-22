$ROOT = Split-Path -Parent $PSScriptRoot
Set-Location $ROOT

java -cp "out;lib/gson-2.13.2.jar" eurocoin.app.Wallet