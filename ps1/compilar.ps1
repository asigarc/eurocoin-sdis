$ROOT = Split-Path -Parent $PSScriptRoot
Set-Location $ROOT

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " LIMPIANDO COMPILADOS ANTIGUOS"
Write-Host "=====================================" -ForegroundColor Cyan

Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
Remove-Item -Recurse -Force .\out -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\eurocoin -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\target\classes -ErrorAction SilentlyContinue
Get-ChildItem -Recurse -Filter *.class | Remove-Item -Force

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " COMPILANDO PROYECTO"
Write-Host "=====================================" -ForegroundColor Cyan

javac -encoding UTF-8 -cp "lib/gson-2.13.2.jar" -d out `
src/main/java/eurocoin/common/*.java `
src/main/java/eurocoin/util/*.java `
src/main/java/eurocoin/bancocentral/*.java `
src/main/java/eurocoin/banco/*.java `
src/main/java/eurocoin/app/*.java

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Compilación completada correctamente." -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Error durante la compilación." -ForegroundColor Red
}