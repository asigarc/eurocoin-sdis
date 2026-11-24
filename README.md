# Sistema Bancario Distribuido EuroCoin (`eurocoin-sdis`)

> **Stack Tecnológico:** `Java` · `TCP Sockets & Java RMI` · `Multithreading (ExecutorService)` · `Criptografía (SHA-256 / HMAC)` · `TLS / PKCS12` · `JSON (Gson)`

Proyecto completo correspondiente a las **Prácticas 1 y 2** (Módulos 1, 2 y 3 integrados) de la asignatura **Sistemas Distribuidos** (Universidad de Valladolid). El sistema implementa una arquitectura distribuida híbrida (Cliente-Servidor, Autoridad Central RMI y *Peer-to-Peer* P2P) para la gestión segura de moneda digital (**EuroCoin**), garantizando integridad, autenticación mutua y persistencia tanto en modo conectado (**Online**) como desconectado (**Offline**).

| Metadatos del Proyecto | Información | 
| ----- | ----- | 
| **Asignatura** | Sistemas Distribuidos (SDIS) | 
| **Institución** | Universidad de Valladolid (UVa) | 
| **Curso Académico** | 2025 - 2026 | 
| **Equipo** | Equipo 2.2 | 
| **Integrante 1** | Álvarez Vicente, Noelia | 
| **Integrante 2** | García del Barrio, Juan Guillermo | 
| **Integrante 3** | Hernández Sánchez, Jorge | 
| **Integrante 4** | García Anta, Asier | 

## 1. Arquitectura y Estructura del Repositorio

El proyecto sigue una estructura modular en Java separando el código fuente por responsabilidades, almacenes de certificados, librerías externas, ficheros de persistencia JSON/TXT y scripts de automatización:

```
eurocoin-sdis/
├── lib/
│   └── gson-2.13.2.jar               # Librería externa para serialización JSON
├── ps1/                              # Scripts PowerShell de compilación, despliegue y control
│   ├── compilar.ps1
│   ├── run_banco_central.ps1
│   ├── run_banco_alice.ps1
│   ├── run_banco_bob.ps1
│   ├── run_wallet.ps1
│   ├── run_tpvonline.ps1
│   ├── run_check_registros.ps1
│   ├── reset_datos.ps1
│   └── stop_java.ps1
├── src/
│   └── main/
│       └── java/
│           ├── eurocoin/
│           │   ├── app/              # Aplicaciones cliente (Wallet y TPV)
│           │   ├── banco/            # Servidores bancarios concurrentes (Alice / Bob)
│           │   ├── bancocentral/     # Autoridad central y concesión de permisos (GrantServer)
│           │   ├── common/           # Protocolos, primitivas (A1-A12, O1-O10) y excepciones
│           │   └── util/             # Criptografía (SHA-256/MAC), persistencia y autenticación
│           └── snippets/             # Clases de prueba unitaria (PruebaRegistro.java)
├── Client_Truststore.p12             # Almacén de confianza PKCS#12 para conexiones seguras
├── Movimientos.txt                   # Registro persistente de transacciones offline (JSON + MAC)
├── Registro.json                     # Configuración, credenciales y saldos locales del cliente
├── RegistroBanco.json                # Base de datos centralizada de las entidades bancarias
└── pom.xml                           # Descriptor de proyecto Maven

```

### Desglose de Paquetes

* **`eurocoin.bancocentral`**:

  * `ServidorBancoCentral.java`: Nodo de autoridad central encargado de coordinar entidades bancarias y validar autorizaciones globales.

  * `GrantServerObject.java`: Objeto remoto/servicio encargado de la concesión de permisos y tokens de autorización entre entidades del ecosistema.

* **`eurocoin.banco`**:

  * `Banco.java`: Servidor multihilo (`ExecutorService` con pool de 10 hilos) que escucha conexiones entrantes y controla las sesiones activas para bloquear intentos de *doble login*.

  * `BancoSirviente.java`: Hilo dedicado por cliente. Gestiona el protocolo online (`A1`–`A12`), verifica códigos MAC mediante `SecWallet`/`SecBanco` y actualiza `RegistroBanco.json` de forma atómica con cerrojos (`REGISTRO_LOCK`).

* **`eurocoin.app`**:

  * `Wallet.java`: Cartera principal del usuario para interactuar con el banco en modo online (consultas, recargas y transferencias).

  * `WalletOfflinePrimario.java` & `WalletOfflineSecundario.java`: Clientes P2P para transferencias directas entre carteras sin conexión al banco (`O1`–`O10`).

  * `TPVOffline.java` & `TPVOnline.java`: Terminal Punto de Venta para cobros offline a clientes y posterior descarga/consolidación de ingresos en el banco (`A11`/`A12`).

* **`eurocoin.common`**:

  * `APPBancoPrimitive.java` & `APPBancoMensaje.java`: Definición de mensajes y primitivas del protocolo Online (`A1` a `A12`).

  * `APPOfflinePrimitive.java` & `APPOfflineMensaje.java`: Definición de mensajes y primitivas del protocolo Offline P2P (`O1` a `O10`).

  * `MalMensajeProtocoloException.java`: Excepción personalizada ante fallos de secuencia, formato o integridad criptográfica.

* **`eurocoin.util`**:

  * `Digest.java`: Generación de resúmenes criptográficos `SHA-256` y verificación de firmas MAC.

  * `AutenticadorLocal.java`: Control de acceso local mediante contraseña y *salt*.

  * `Registro.java`: Lectura y escritura segura de los ficheros `.json`.

  * `Movimientos.java`: Gestión de almacenamiento y limpieza del historial offline (`Movimientos.txt`).

## 2. Manual de Instalación y Requisitos

### Requisitos Previos

1. **Java Development Kit (JDK):** Versión 11 o superior instalada y configurada en la variable de entorno `PATH`.

2. **Dependencias y Seguridad:**

   * `lib/gson-2.13.2.jar` debe encontrarse en la carpeta `lib/`.

   * `Client_Truststore.p12` debe ubicarse en la raíz del proyecto para la validación de certificados de confianza.

3. **Permisos de Scripts (Windows PowerShell):** Para ejecutar los scripts automatizados de la carpeta `ps1/`, habilita la política de ejecución en la sesión actual:

   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   ```

### Configuración Inicial de Ficheros JSON

Antes de arrancar el sistema, verifica que en la raíz del proyecto existen los archivos de configuración base:

* **`RegistroBanco.json`**: Contiene el diccionario maestro de usuarios y entidades registradas en el banco (`BancoBob`, `BancoAlice`, `PedroLuis`, `AngelAntonio`, etc.) con sus respectivas `MasterKey`, `SecWallet`, `SecBanco`, `Saldo` y `FondosEC`.

* **`Registro.json`**: Configuración del terminal cliente.

  * *Usuario de prueba por defecto:* `PedroLuis`

  * *Contraseña local por defecto:* `HolaSoyPedroLuis`

> **Nota para pruebas multi-terminal:** Dado que el servidor bloquea sesiones simultáneas del mismo usuario (error `A3`), si vas a ejecutar simultáneamente una `Wallet` y un `TPVOnline` contra el banco, asegúrate de configurar identidades distintas en sus respectivos archivos JSON (por ejemplo, `PedroLuis` para la Wallet y `TiendaPaco` para el TPV).

## 3. Manual de Compilación y Ejecución

Todos los comandos deben lanzarse desde la **raíz del proyecto** (`eurocoin-sdis/`).

### Opción A: Ejecución Rápida mediante Scripts (`ps1/`) — *Windows*

La carpeta `ps1/` automatiza la compilación, el despliegue de los distintos nodos del sistema y el mantenimiento del entorno:

```powershell
# 1. Compilar todos los paquetes del proyecto
.\ps1\compilar.ps1

# 2. Iniciar la infraestructura bancaria (en terminales separados)
.\ps1\run_banco_central.ps1      # Arranca la Autoridad Central / GrantServer
.\ps1\run_banco_alice.ps1        # Arranca la instancia bancaria Alice
.\ps1\run_banco_bob.ps1          # Arranca la instancia bancaria Bob

# 3. Iniciar Aplicaciones Cliente (en terminales separados)
.\ps1\run_wallet.ps1             # Ejecuta la cartera de usuario
.\ps1\run_tpvonline.ps1          # Ejecuta el TPV en modo online para consolidación

# Utilidades de auditoría, mantenimiento y limpieza
.\ps1\run_check_registros.ps1    # Verifica el estado y saldos de los ficheros JSON
.\ps1\reset_datos.ps1            # Restaura los saldos y ficheros JSON/TXT iniciales
.\ps1\stop_java.ps1              # Detiene todos los procesos Java activos del entorno
```

### Opción B: Compilación y Ejecución Manual (Windows / Linux / macOS)

#### 1. Compilación (generando `.class` ordenados con `-d .`)

* **Windows (PowerShell / CMD):**

  ```powershell
  javac -d . -cp ".;lib/gson-2.13.2.jar" src/main/java/eurocoin/common/*.java src/main/java/eurocoin/util/*.java src/main/java/eurocoin/bancocentral/*.java src/main/java/eurocoin/banco/*.java src/main/java/eurocoin/app/*.java
  ```

* **Linux / macOS:**

  ```bash
  javac -d . -cp ".:lib/gson-2.13.2.jar" src/main/java/eurocoin/common/*.java src/main/java/eurocoin/util/*.java src/main/java/eurocoin/bancocentral/*.java src/main/java/eurocoin/banco/*.java src/main/java/eurocoin/app/*.java
  ```

#### 2. Ejecución de Componentes

*(En Linux/macOS sustituye el punto y coma `;` por dos puntos `:` dentro del parámetro `-cp`).*

* **Servidor Banco Central (Autoridad de permisos):**

  ```powershell
  java -cp ".;lib/gson-2.13.2.jar" eurocoin.bancocentral.ServidorBancoCentral
  ```

* **Servidor Bancario (Debe arrancarse antes que los clientes online):**

  ```powershell
  java -cp ".;lib/gson-2.13.2.jar" eurocoin.banco.Banco
  ```

* **Cartera Cliente (Modo Online):**

  ```powershell
  java -cp ".;lib/gson-2.13.2.jar" eurocoin.app.Wallet
  ```

* **TPV Online (Consolidación de ingresos con el banco):**

  ```powershell
  java -cp ".;lib/gson-2.13.2.jar" eurocoin.app.TPVOnline
  ```

* **TPV Offline (Cobro a clientes sin conexión):**

  ```powershell
  java -cp ".;lib/gson-2.13.2.jar" eurocoin.app.TPVOffline
  ```

* **Transferencia P2P Offline (Entre dos carteras):**

  ```powershell
  # Terminal Receptor (Arrancar primero)
  java -cp ".;lib/gson-2.13.2.jar" eurocoin.app.WalletOfflineSecundario

  # Terminal Emisor
  java -cp ".;lib/gson-2.13.2.jar" eurocoin.app.WalletOfflinePrimario
  ```

## 4. Manual de Usuario y Flujo de Operaciones

### Escenario 1: Operaciones Online (`Wallet` ↔ `Banco`)

1. Arranca el `BancoCentral` y el `Banco` (`.\ps1\run_banco_central.ps1` y `.\ps1\run_banco_bob.ps1`). Verás `----Server waiting For Client----`.

2. Inicia `Wallet` (`.\ps1\run_wallet.ps1`) e introduce la contraseña local (`HolaSoyPedroLuis`).

3. Selecciona el **Modo Online** para establecer sesión autenticada con el banco (`A1`–`A6`).

4. Desde el menú podrás realizar **Recargas de EuroCoins** (`A7`/`A8`): el banco descontará el importe de tu `Saldo` bancario y lo sumará a tus `FondosEC`, persistiendo los cambios inmediatamente en `RegistroBanco.json` y `Registro.json`.

### Escenario 2: Pagos Offline (`Wallet` ↔ `TPVOffline` / P2P)

1. Inicia el dispositivo receptor (`TPVOffline` o `WalletOfflineSecundario`).

2. Inicia el dispositivo emisor (`Wallet` en modo offline o `WalletOfflinePrimario`).

3. Ambos dispositivos realizarán el *handshake* verificando la `MasterKey` (`O1`–`O4`).

4. El emisor enviará el pago firmado con su `SecWallet` (`O5`–`O8`). Ambos dispositivos registrarán el movimiento junto con su firma MAC en `Movimientos.txt` y actualizarán sus contadores locales (`FondosEC` e `IngresosEC`).

### Escenario 3: Consolidación de Ingresos (`TPVOnline` ↔ `Banco`)

1. Tras haber registrado cobros offline en el comercio, asegúrate de que el servidor bancario esté activo.

2. Ejecuta `TPVOnline` (`.\ps1\run_tpvonline.ps1`) e identifícate.

3. Selecciona la opción **1. Descargar ingresos al banco (A11)**.

4. El TPV enviará al banco los movimientos acumulados en `Movimientos.txt`. Una vez que el banco valide todos los códigos MAC y responda con la confirmación firmada (`A12`), el saldo del comercio se incrementará en `RegistroBanco.json` y el archivo `Movimientos.txt` quedará limpio.

## 5. Declaración de Autoría y Código Ético

> **NOTA:** Los miembros del Equipo 2.2 manifiestan ser los autores de la práctica presentada en este repositorio. Los autores manifiestan haber realizado la presente práctica ateniéndose al código de conducta establecido por la Universidad de Valladolid para la presentación de trabajos y uso responsable de herramientas de IA.