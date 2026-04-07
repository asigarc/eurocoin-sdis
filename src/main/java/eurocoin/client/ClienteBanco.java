package eurocoin.client;

// Utilidades propias del proyecto
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import eurocoin.common.APPBancoMensaje; // Añadido para el Módulo 3
import eurocoin.common.APPBancoPrimitive;
import eurocoin.common.MalMensajeProtocoloException; // Añadido para el Módulo 3
import utiles.movimientos.Movimientos; // Añadido para el Módulo 3
import utiles.digest.Digest;
import utiles.registro.Registro;

/**
 * ClienteBanco
 *
 * Esta clase gestiona la conexión TCP con el Servidor Bancario y lleva a cabo
 * el protocolo de autenticación mutua APPBancoProtocol tal y como se define
 * en la documentación de la práctica EuroCoin.
 *
 * Flujo del protocolo (primitivas A1-A8):
 *
 * Banco → APP  A1: saludo(desafio_banco)
 * APP → Banco  A2: saludo(desafio_app, id_cliente, HASH(desafio_banco + secreto_app))
 * Banco → APP  A3: saludo_err          [si falla la auth del cliente]
 * Banco → APP  A4: saludo_ok(respuesta) [respuesta = HASH(desafio_app + secreto_banco)]
 * APP → Banco  A5: saludo_ok           [si la respuesta del banco es correcta]
 * APP → Banco  A6: saludo_err          [si la respuesta del banco es incorrecta]
 * APP → Banco  A7: final
 * Banco → APP  A8: final_ok
 *
 * Uso típico:
 * ClienteBanco cliente = new ClienteBanco("Registro.json");
 * cliente.iniciar();
 */
public class ClienteBanco {

    // -------------------------------------------------------------------------
    // Configuración de la conexión
    // -------------------------------------------------------------------------

    /** Dirección IP o nombre del host donde escucha el Servidor Bancario. */
    private static final String HOST_BANCO = "localhost";

    /** Puerto en el que el Servidor Bancario acepta conexiones. */
    private static final int PUERTO_BANCO = 2000;

    // -------------------------------------------------------------------------
    // Atributos de instancia
    // -------------------------------------------------------------------------

    /** Socket TCP con el banco. */
    private Socket socket;

    /** Stream de salida para enviar objetos al banco. */
    private ObjectOutputStream salida;

    /** Stream de entrada para recibir objetos del banco. */
    private ObjectInputStream entrada;

    /** Registro JSON con los secretos y datos de la APP. */
    private final Registro registro;

    /** Función de hash usada en el protocolo. */
    private final Digest digest;

    /** Gestor de movimientos para registrar los recibos del banco. */
    private final Movimientos movimientos; // Añadido para el Módulo 3

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Crea un ClienteBanco cargando los datos desde el archivo de registro.
     *
     * @param archivoRegistro Ruta al archivo Registro.json
     * @throws IOException Si el archivo no existe o no puede leerse
     */
    public ClienteBanco(String archivoRegistro) throws IOException {
        this.registro = new Registro(archivoRegistro);

        // Leemos el algoritmo de hash del registro (ej: "SHA-256")
        String hashFunc = registro.getValor("FuncionHash");
        this.digest = new Digest(hashFunc);

        // Inicializamos el gestor de movimientos para guardar los recibos
        this.movimientos = new Movimientos("Movimientos.txt"); // Añadido para el Módulo 3

        System.out.println("[ClienteBanco] Iniciado. Hash: " + hashFunc);
    }

    // -------------------------------------------------------------------------
    // Método principal: une todo el flujo
    // -------------------------------------------------------------------------

    /**
     * Punto de entrada principal del cliente.
     * Intenta conectar, autenticar y operar con el banco.
     * Si la conexión o autenticación falla, entra en modo OFFLINE.
     */
    public void iniciar() {
        // -- Paso 0: Intentar conectar al banco via TCP --
        boolean conectado = conectar();

        if (!conectado) {
            // No hay red o el banco no está activo
            System.out.println("[ClienteBanco] MODO OFFLINE: no se pudo conectar al banco.");
            return;
        }

        try {
            // -- Pasos 1-6: Autenticación mutua --
            boolean autenticado = autenticar();

            if (!autenticado) {
                System.out.println("[ClienteBanco] MODO OFFLINE: autenticación fallida.");
                return;
            }

            // -- Pasos 7-8: Operar en modo online y luego cerrar sesión --
            System.out.println("[ClienteBanco] MODO ONLINE: autenticación completada con éxito.");
            
            pedirRecarga(); // MÓDULO 3: Solicitamos la recarga de fondos
            
            operarYDespedirse();

        // Se han añadido ClassNotFoundException y MalMensajeProtocoloException
        } catch (IOException | ClassNotFoundException | MalMensajeProtocoloException e) {
            System.out.println("[ClienteBanco] Error durante la sesión: " + e.getMessage());
        } finally {
            cerrar();
        }
    }

    // -------------------------------------------------------------------------
    // Conexión TCP
    // -------------------------------------------------------------------------

    /**
     * Abre la conexión TCP con el banco y prepara los streams de
     * entrada/salida para el intercambio de objetos serializados.
     *
     * @return true si la conexión se estableció correctamente, false si falló
     */
    private boolean conectar() {
        try {
            socket = new Socket(HOST_BANCO, PUERTO_BANCO);

            // Importante: el ObjectOutputStream se crea ANTES del ObjectInputStream
            // para evitar un deadlock en la negociación de la cabecera del stream.
            salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socket.getInputStream());

            System.out.println("[ClienteBanco] Conectado al banco en "
                    + HOST_BANCO + ":" + PUERTO_BANCO);
            return true;

        } catch (IOException e) {
            System.out.println("[ClienteBanco] Sin conexión con el banco: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Autenticación mutua (primitivas A1 - A6)
    // -------------------------------------------------------------------------

    /**
     * Realiza la autenticación mutua APP ↔ Banco según el protocolo
     * APPBancoProtocol (primitivas A1 a A6).
     *
     * @return true si ambas partes se autenticaron correctamente
     * @throws IOException            Si hay un error de comunicación
     * @throws ClassNotFoundException Si se recibe un objeto de tipo inesperado
     */
    private boolean autenticar() throws IOException, ClassNotFoundException {

        // ---- A1: Banco → APP  saludo(desafio_banco) -------------------------
        APPBancoPrimitive prim = (APPBancoPrimitive) entrada.readObject();
        if (prim != APPBancoPrimitive.A1) {
            System.out.println("[ClienteBanco] Se esperaba A1, llegó: " + prim);
            return false;
        }
        String desafioBanco = (String) entrada.readObject();
        System.out.println("[ClienteBanco] A1 recibido. Desafío del banco: " + desafioBanco);

        // ---- Preparamos la respuesta al banco --------------------------------

        // Leemos nuestros datos del registro
        String idCliente  = registro.getValor("usuario");
        String secretoApp = registro.getValor("SecWallet"); // secreto compartido APP ↔ Banco

        // Generamos nuestro propio desafío aleatorio (distinto cada vez)
        // para evitar ataques por repetición de mensajes.
        String desafioApp = Long.toHexString(System.currentTimeMillis())
                          + Integer.toHexString((int)(Math.random() * 0xFFFF));

        // Calculamos HASH(desafio_banco + secreto_app)
        // El banco reconstruirá este mismo hash con su copia del secreto_app
        // para verificar que somos quienes decimos ser.
        String hashParaBanco = digest.generateHash(desafioBanco + secretoApp);

        // ---- A2: APP → Banco  saludo(desafio_app, id_cliente, hash) ----------
        System.out.println("[ClienteBanco] A2 enviado. id=" + idCliente
                + "  desafioApp=" + desafioApp
                + "  hash=" + hashParaBanco);
        salida.writeObject(APPBancoPrimitive.A2);
        salida.writeObject(desafioApp);
        salida.writeObject(idCliente);
        salida.writeObject(hashParaBanco);
        salida.flush();

        // ---- A3 / A4: respuesta del banco ------------------------------------
        APPBancoPrimitive respBanco = (APPBancoPrimitive) entrada.readObject();

        if (respBanco == APPBancoPrimitive.A3) {
            // El banco no nos reconoce: APP queda comprometida
            System.out.println("[ClienteBanco] A3 recibido: saludo_err. "
                    + "La APP ha sido rechazada por el banco.");
            return false;
        }

        if (respBanco != APPBancoPrimitive.A4) {
            System.out.println("[ClienteBanco] Mensaje inesperado: " + respBanco);
            return false;
        }

        // A4: saludo_ok(HASH(desafio_app + secreto_banco))
        // El banco nos demuestra que conoce el secreto_banco compartido.
        String respuestaBanco = (String) entrada.readObject();
        System.out.println("[ClienteBanco] A4 recibido. Respuesta del banco: " + respuestaBanco);

        // ---- Verificamos la identidad del banco ------------------------------
        String secretoBanco   = registro.getValor("SecBanco");
        String hashEsperado   = digest.generateHash(desafioApp + secretoBanco);

        if (hashEsperado.equals(respuestaBanco)) {
            // ---- A5: APP → Banco  saludo_ok  --------------------------------
            System.out.println("[ClienteBanco] Banco verificado correctamente. Enviando A5: saludo_ok.");
            salida.writeObject(APPBancoPrimitive.A5);
            salida.flush();
            return true; // Autenticación mutua completada con éxito

        } else {
            // ---- A6: APP → Banco  saludo_err --------------------------------
            System.out.println("[ClienteBanco] El banco NO superó la verificación. Enviando A6: saludo_err.");
            salida.writeObject(APPBancoPrimitive.A6);
            salida.flush();
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Módulo 3: Petición de recarga al banco (Primitivas A9 y A10)
    // -------------------------------------------------------------------------

    private void pedirRecarga() throws IOException, ClassNotFoundException, MalMensajeProtocoloException {
        System.out.println("--------------------------------------------------");
        System.out.println(" Iniciando proceso de recarga de fondos...");
        System.out.println("--------------------------------------------------");

        String idCliente = registro.getValor("usuario");
        java.util.Scanner escaner = new java.util.Scanner(System.in);
        System.out.print(">>> Introduce la cantidad de EuroCoins a recargar: ");
        String cantidadRecarga = escaner.nextLine();
        
        // 1. Construimos el JSON y su firma
        String jsonRecarga = "{\"op\": \"recarga\", \"titular\": \"" + idCliente + "\", \"cantidad\": \"" + cantidadRecarga + "\"}";
        String secWallet = registro.getValor("SecWallet");
        String macRecarga = digest.generateHash(jsonRecarga + secWallet);

        // 2. Guardamos la operación saliente en Movimientos.txt
        movimientos.guardarMovimiento(jsonRecarga, macRecarga);

        // 3. Enviamos A9 al banco usando el nuevo "sobre" APPBancoMensaje
        System.out.println("[ClienteBanco] Enviando A9: Solicitando " + cantidadRecarga + " al banco...");
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A9, jsonRecarga, macRecarga));
        salida.flush();

        // 4. Esperamos la confirmación A10
        APPBancoMensaje respuesta = (APPBancoMensaje) entrada.readObject();

        if (respuesta.getPrimitiva() == APPBancoPrimitive.A10) {
            String jsonRecibo = respuesta.getArg1();
            String macRecibo = respuesta.getArg2();

            // 5. Guardamos el recibo entrante en Movimientos.txt
            movimientos.guardarMovimiento(jsonRecibo, macRecibo);

            // 6. Actualizamos nuestros fondos en el Registro
            String fondosActuales = registro.getValor("FondosEC");
            if (fondosActuales == null) fondosActuales = "0.0"; // Por precaución
            
            double nuevosFondos = Double.parseDouble(fondosActuales) + Double.parseDouble(cantidadRecarga);
            registro.setValor("FondosEC", String.valueOf(nuevosFondos));

            System.out.println("[ClienteBanco] ¡Recarga completada! Saldo actualizado: " + nuevosFondos + " EC");
            System.out.println("--------------------------------------------------");
        } else {
            System.out.println("[ClienteBanco] Error en recarga. Se esperaba A10 pero llegó: " + respuesta.getPrimitiva());
        }
    }

    // -------------------------------------------------------------------------
    // Operación y despedida (primitivas A7 - A8)
    // -------------------------------------------------------------------------

    /**
     * Simula la fase de operación con el banco (paso 7 de la documentación):
     * deja la aplicación en espera hasta que el usuario pulse ENTER,
     * y luego cierra la sesión enviando la primitiva final (A7).
     * El banco responde con final_ok (A8).
     *
     * @throws IOException            Si hay un error de comunicación
     * @throws ClassNotFoundException Si se recibe un objeto de tipo inesperado
     */
    private void operarYDespedirse() throws IOException, ClassNotFoundException {
        System.out.println("--------------------------------------------------");
        System.out.println("  Sesión activa con el banco. Pulsa ENTER para");
        System.out.println("  terminar la interacción.");
        System.out.println("--------------------------------------------------");

        // Esperamos la pulsación de tecla del usuario
        System.in.read();

        // ---- A7: APP → Banco  final -----------------------------------------
        System.out.println("[ClienteBanco] Enviando A7: final.");
        salida.writeObject(APPBancoPrimitive.A7);
        salida.flush();

        // ---- A8: Banco → APP  final_ok --------------------------------------
        APPBancoPrimitive respuesta = (APPBancoPrimitive) entrada.readObject();
        if (respuesta == APPBancoPrimitive.A8) {
            System.out.println("[ClienteBanco] A8 recibido: final_ok. Sesión cerrada correctamente.");
        } else {
            System.out.println("[ClienteBanco] Respuesta inesperada al final: " + respuesta);
        }
    }

    // -------------------------------------------------------------------------
    // Cierre de recursos
    // -------------------------------------------------------------------------

    /**
     * Cierra el socket y los streams de forma limpia.
     * Se llama siempre en el bloque finally de iniciar().
     */
    private void cerrar() {
        try {
            if (salida != null) salida.close();
            if (entrada != null) entrada.close();
            if (socket != null) socket.close();
            System.out.println("[ClienteBanco] Recursos liberados.");
        } catch (IOException e) {
            System.out.println("[ClienteBanco] Error al cerrar recursos: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            ClienteBanco cliente = new ClienteBanco("Registro.json");
            cliente.iniciar();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}