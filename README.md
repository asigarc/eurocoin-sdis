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
[![](https://mermaid.ink/img/pako:eNqFVNtunDAQ_RU0z2RD2F0oqC-59KEPq6KmaqSKF8fMEqfGRrZZpd3y7zXXEhZUJHM5Z-bM8TDyGajMEGKgnGj9wEiuSJEKx15aUefjn6srByslqWRiBmvByhKN7uAhqONIWS7Bz0RQuURQWRRSLDGVYTz9Vzq26_rCUIu-99Pd212NiucObFNGkevR6wyfmJ0xU7czqrPbwPWlj8Hh6CNRFT6Tr5gzbZTcvJITWU22Nse8b8n3L8cjZwInOR0sLtAnwjmaBaiXSBQriGJyPeIRaSWyWUw9_JXOXtstZ3ToOHcN8E6zxx6ZOjEUBtfFugZPxG6TpM09oNDkFWeyA9vshBl2WuDveVvys13qSOh6xGqFvhf_4wcPZGnra-UPhPe6iZLGThKXn94oloZJsd6lZtYmPXpgOWozV5Yn68duTOoZszx14EKuWAbxkXCNLhSoCtJ8Q1spBfOCBaYQ21c7ET9TSEVtk0oifkhZQGzsSLugZJW_jCJVmRGD_eEyogpFhupeVsJA7Ide0KpAfIY3iEN_8yHce14Y-NE22u23LvyC-Mb3Nv5NFPrBPvD3tQu_25qejd1FkcV3wW4bBoHvAmbMSHXoD7fmUf8FUkqPQQ?type=png)](https://mermaid.ai/live/edit#pako:eNqFVNtunDAQ_RU0z2RD2F0oqC-59KEPq6KmaqSKF8fMEqfGRrZZpd3y7zXXEhZUJHM5Z-bM8TDyGajMEGKgnGj9wEiuSJEKx15aUefjn6srByslqWRiBmvByhKN7uAhqONIWS7Bz0RQuURQWRRSLDGVYTz9Vzq26_rCUIu-99Pd212NiucObFNGkevR6wyfmJ0xU7czqrPbwPWlj8Hh6CNRFT6Tr5gzbZTcvJITWU22Nse8b8n3L8cjZwInOR0sLtAnwjmaBaiXSBQriGJyPeIRaSWyWUw9_JXOXtstZ3ToOHcN8E6zxx6ZOjEUBtfFugZPxG6TpM09oNDkFWeyA9vshBl2WuDveVvys13qSOh6xGqFvhf_4wcPZGnra-UPhPe6iZLGThKXn94oloZJsd6lZtYmPXpgOWozV5Yn68duTOoZszx14EKuWAbxkXCNLhSoCtJ8Q1spBfOCBaYQ21c7ET9TSEVtk0oifkhZQGzsSLugZJW_jCJVmRGD_eEyogpFhupeVsJA7Ide0KpAfIY3iEN_8yHce14Y-NE22u23LvyC-Mb3Nv5NFPrBPvD3tQu_25qejd1FkcV3wW4bBoHvAmbMSHXoD7fmUf8FUkqPQQ)


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