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
[![](https://mermaid.ink/img/pako:eNqFVNuOmzAU_BV0nkmWhMtGqC_dy1ujom7VSitevOaE9crYyJho25R_r7mWdUBF4jZzZs4csHwBKjOEGCgnVfXASK5IkQrHHJWizqc_m42DtZJUMmHBlWBlibrq4bGo50hZLsEvRFC5RFBZFFIsMbVmPP3XOjbnzVWgDv2Yp792U02Olx7sJJPJzZTVwmdhLWae1qL6uC3cXOcYE045ElXjC_mGOau0kts3ciarYhNz0n1Pfnw9nTgTONP0sLhCfxLOUS9Ag0WiWEEUk-sVT0hrkVk1zfhX-njd13KmhI5z1wIfPAfsiakzQ6Fx3az_wDOzz0nSaY8oKvKGlu3ItpMwzc4L_D3vWq7qh0n_x48diFVxJHyQJkpqsxS4fHynWGomxfqY7WKZD1lrk5FRkkn1RVLCrSYPLMdK253l2UQyOllZzPKyAhdyxTKIT4RX6EKBqiDtO3RJUtCvWGAKsXnM8ERqrlNIRWN0JRHPUhYQa7NsXVCyzl8nn7rMiMZhA5lKUGSo7mUtNMS7IOo8IL7AO8SBt42iQ-jvw10YBl7oBy78gnjjB1sv2oe-v98dDt5tGDUu_O7aetvDbegCZkxLdRy2rfbW_AVguoTH?type=png)](https://mermaid.ai/live/edit#pako:eNqFVNuOmzAU_BV0nkmWhMtGqC_dy1ujom7VSitevOaE9crYyJho25R_r7mWdUBF4jZzZs4csHwBKjOEGCgnVfXASK5IkQrHHJWizqc_m42DtZJUMmHBlWBlibrq4bGo50hZLsEvRFC5RFBZFFIsMbVmPP3XOjbnzVWgDv2Yp792U02Olx7sJJPJzZTVwmdhLWae1qL6uC3cXOcYE045ElXjC_mGOau0kts3ciarYhNz0n1Pfnw9nTgTONP0sLhCfxLOUS9Ag0WiWEEUk-sVT0hrkVk1zfhX-njd13KmhI5z1wIfPAfsiakzQ6Fx3az_wDOzz0nSaY8oKvKGlu3ItpMwzc4L_D3vWq7qh0n_x48diFVxJHyQJkpqsxS4fHynWGomxfqY7WKZD1lrk5FRkkn1RVLCrSYPLMdK253l2UQyOllZzPKyAhdyxTKIT4RX6EKBqiDtO3RJUtCvWGAKsXnM8ERqrlNIRWN0JRHPUhYQa7NsXVCyzl8nn7rMiMZhA5lKUGSo7mUtNMS7IOo8IL7AO8SBt42iQ-jvw10YBl7oBy78gnjjB1sv2oe-v98dDt5tGDUu_O7aetvDbegCZkxLdRy2rfbW_AVguoTH)

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