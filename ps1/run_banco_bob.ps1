$ROOT = Split-Path -Parent $PSScriptRoot
Set-Location $ROOT

java `
"-Djavax.net.ssl.keyStore=Server_Keystore.p12" `
"-Djavax.net.ssl.keyStorePassword=servidorpass" `
"-Djavax.net.ssl.trustStore=Client_Truststore.p12" `
"-Djavax.net.ssl.trustStorePassword=clientepass" `
-cp "out;lib/gson-2.13.2.jar" `
eurocoin.banco.Banco 2000 data/bancoBob/RegistroBanco.json data/bancoBob/MovimientosBanco.txt BancoBob