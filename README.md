# Entrega de la primera práctica de laboratorio.


| Asignatura    | Sistemas Distribuidos             |
| ------------  | :------------------------------   |
| Curso         | 2025-2026                         |
| Equipo:       | Equipo 2.2                        |
| Integrante 1: | Álvarez Vicente, Noelia           |
| Integrante 2: | García del Barrio, Juan Guillermo |
| Integrante 3: | Hernández Sánchez, Jorge          |
| Integrante 4: | García Anta, Asier                |



### Descripción de carpetas con su correspondiente código:
[![](https://mermaid.ink/img/pako:eNp1VF1vmzAU_SvIzzRNAvko2sva7m3R0Dqt0sTLLdxQV8YX2SbqlvHfZz7CqANIfJ3jc-65tuUzSylDFrFUgNaPHHIFRSI9e2mVep_-3tx4WClKiUsH1pKXJRrdwZdBHQdlOQW_gExpikipKEhOMZXhIvlfOrL37VWgFv2Yp3u2XQ2O5w5sJYPJ7ZDVwUdhHWac1qG6uA1cX-e4JBxyxKrCF_iOOddG0eINTjArtjEH3Y_457fjUXCJI00Hyyv0GYRAMwH1FrHiBShO8yOeMK1k5oypL6vSxWtnyxsSet59A3zw7LEnrk4cpcF5s26CR2af47jVHlBqeEPH9sI2nXDDTxN838msfjQXjQM4Iw4gemmsyNilFvTlPcXScJLzbTSbYdxEZWzbPIWM1FdKQThFHnmO2riV6WQjWR1ph5neNsxnueIZi44gNPqsQFVA88_aJAkzr1hgwiL7meERKmESlsja6kqQv4gKFhm7LX2mqMpfB5-qzMBgf0AMqEKZoXqgShoWrXatB4vO7J1Fm9Viu92H6124Du6Cfbjy2W8WhcHiLtiFQbhdhkGwWm9qn_1piy4X-91mObqsADNuSB36E6p51f8Aoml89g?type=png)](https://mermaid.ai/live/edit#pako:eNp1VF1vmzAU_SvIzzRNAvko2sva7m3R0Dqt0sTLLdxQV8YX2SbqlvHfZz7CqANIfJ3jc-65tuUzSylDFrFUgNaPHHIFRSI9e2mVep_-3tx4WClKiUsH1pKXJRrdwZdBHQdlOQW_gExpikipKEhOMZXhIvlfOrL37VWgFv2Yp3u2XQ2O5w5sJYPJ7ZDVwUdhHWac1qG6uA1cX-e4JBxyxKrCF_iOOddG0eINTjArtjEH3Y_457fjUXCJI00Hyyv0GYRAMwH1FrHiBShO8yOeMK1k5oypL6vSxWtnyxsSet59A3zw7LEnrk4cpcF5s26CR2af47jVHlBqeEPH9sI2nXDDTxN838msfjQXjQM4Iw4gemmsyNilFvTlPcXScJLzbTSbYdxEZWzbPIWM1FdKQThFHnmO2riV6WQjWR1ph5neNsxnueIZi44gNPqsQFVA88_aJAkzr1hgwiL7meERKmESlsja6kqQv4gKFhm7LX2mqMpfB5-qzMBgf0AMqEKZoXqgShoWrXatB4vO7J1Fm9Viu92H6124Du6Cfbjy2W8WhcHiLtiFQbhdhkGwWm9qn_1piy4X-91mObqsADNuSB36E6p51f8Aoml89g)

**eurocoin/banco/** -> Contiene el codigo del servidor bancario.  
Banco.java se encarga de iniciar el servidor y aceptar conexiones, mientras que BancoSirviente gestiona cada cliente con un hilo independiente, realizando autenticación y las operaciones como recargas y descargas.  

**eurocoin/app/** -> Contiene las aplicaciones cliente del sistema EuroCoin.  
Wallet representa la cartera del usuario, permitiendo operar en modo online (con el banco) y offline (pagos directos).  
TPV simula un terminal de cobros que puede solicitar pagos a un wallet modo offline.

**eurocoin/common/** -> Incluye las clases compartidas entre el banco y clientes.  
Se definen los mensajes, primitivas del protocolo y excepciones que permiten la comunicación entre las distintas partes del sistema.

**eurocoin/util/** -> Contiene clases auxiliares necesarias para el funcionamiento.  
Se encarga de operaciones como generación de hashes/MAC, gestión de registros (JSON) y almacenar de movimientos.  



>NOTA: Los miembros del equipo manifiestan ser los autores de la práctica presentada en este repositorio. Los autores manifiestan haber realizado la presente práctica ateniéndose al código de conducta establecido por la Universidad de Valladolid para la presentación de trabajos y uso responsable de herramientas de IA

--- 
  
*Linux:*
```bash 
javac -d . -cp ".:lib/gson-2.13.2.jar" src/main/java/eurocoin/app/*.java src/main/java/eurocoin/banco/*.java src/main/java/eurocoin/common/*.java src/main/java/eurocoin/util/*.java
java -cp ".:lib/gson-2.13.2.jar" eurocoin.banco.Banco
```
*Windows:*
```bash
javac -d . -cp ".;lib/gson-2.13.2.jar" src/main/java/eurocoin/app/*.java src/main/java/eurocoin/banco/*.java src/main/java/eurocoin/common/*.java src/main/java/eurocoin/util/*.java 
java -cp ".;lib/gson-2.13.2.jar" eurocoin.banco.Banco ```