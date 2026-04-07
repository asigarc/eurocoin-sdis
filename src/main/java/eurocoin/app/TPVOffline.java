package eurocoin.app;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import eurocoin.common.APPOfflineMensaje;
import eurocoin.common.APPOfflinePrimitiva;
import eurocoin.common.MalMensajeProtocoloException;
import eurocoin.util.Digest;
import eurocoin.util.Movimientos;
import eurocoin.util.Registro;

// Clase TPV (Terminal de Punto de Venta)
public class TPVOffline {

    private static final String HOST_SECUNDARIO = "localhost";
    private static final int PUERTO_SECUNDARIO = 3000;

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;

    private final Registro registro;
    private final Digest digest;
    private final Movimientos movimientos;

    private final String idVendedor;
    private final String masterkey;
    private final String secWallet;
    private String idCliente; // Se obtiene durante la autenticación O4

    public TPVOffline(String archivoRegistro, String archivoMovimientos) throws IOException {
        this.registro = new Registro(archivoRegistro);
        String algoritmo = registro.getValor("FuncionHash");
        this.digest = new Digest(algoritmo);
        this.movimientos = new Movimientos(archivoMovimientos);

        // Cargamos los secretos necesarios para operar
        this.idVendedor = registro.getValor("usuario");
        this.masterkey = registro.getValor("Masterkey");
        this.secWallet = registro.getValor("SecWallet");
    }

    public void iniciar() {
        if (!conectar()) {
            System.out.println("[TPV] No se pudo conectar con el Wallet Secundario.");
            return;
        }

        try {
            if (autenticarOffline()) {
                System.out.println("[TPV] Autenticación completada con " + idCliente + ". Iniciando facturación...");
                facturar();
            } else {
                System.out.println("[TPV] Abortando operación por fallo de seguridad.");
            }
        } catch (MalMensajeProtocoloException | IOException | ClassNotFoundException e) {
            System.err.println("[TPV] Error en la transacción: " + e.getMessage());
        } finally {
            cerrar();
        }
    }

    private boolean conectar() {
        try {
            System.out.println("[TPV] Conectando al Wallet cliente en el puerto " + PUERTO_SECUNDARIO + "...");
            socket = new Socket(HOST_SECUNDARIO, PUERTO_SECUNDARIO);
            salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socket.getInputStream());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Autenticación Offline (Primitivas O1 a O4)

    private boolean autenticarOffline() throws IOException, ClassNotFoundException, MalMensajeProtocoloException {
        // O1: TPV -> Wallet (envía desafio_prim)
        String desafioPrim = Long.toHexString(System.currentTimeMillis());
        System.out.println("[TPV] Enviando O1 con desafioPrim: " + desafioPrim);
        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O1, desafioPrim));
        salida.flush();

        // O2: Wallet -> TPV (recibe desafio_sec y hash)
        APPOfflineMensaje msgO2 = (APPOfflineMensaje) entrada.readObject();
        if (msgO2.getPrimitiva() != APPOfflinePrimitiva.O2)
            return false;

        String desafioSec = msgO2.getArg1();
        String hashRecibido = msgO2.getArg2();

        // Verificamos el hash con la Masterkey
        String hashEsperado = digest.generateHash(desafioPrim + masterkey);
        if (!hashEsperado.equals(hashRecibido)) {
            System.out.println("Error grave de autenticación"); // Requisito del PDF
            return false;
        }

        // O3: TPV -> Wallet (envía idVendedor y hash validando el desafio_sec)
        String miHash = digest.generateHash(desafioSec + masterkey);
        System.out.println("[TPV] Hash correcto. Enviando O3...");
        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O3, idVendedor, miHash));
        salida.flush();

        // O4: Wallet -> TPV (recibe idCliente)
        APPOfflineMensaje msgO4 = (APPOfflineMensaje) entrada.readObject();
        if (msgO4.getPrimitiva() != APPOfflinePrimitiva.O4)
            return false;

        this.idCliente = msgO4.getArg1();
        return true;
    }

    // Proceso de Facturación y Pago (Primitivas O7 a O10)

    private void facturar() throws IOException, ClassNotFoundException, MalMensajeProtocoloException {
        String idFactura = "FACT-" + System.currentTimeMillis();

        java.util.Scanner escaner = new java.util.Scanner(System.in);
        System.out.print(">>> Introduce el importe de la factura a cobrar: ");
        String cantidad = escaner.nextLine();

        // 1. Construir el documento JSON de la factura EXACTO
        String jsonFactura = "{ \"op\": \"factura\", \"de\": \"" + idVendedor + "\", \"a\": \"" + idCliente
                + "\", \"cantidad\": \"" + cantidad + "\", \"idFactura\": \"" + idFactura + "\" }";

        // 2. Calcular el MAC (Hash del JSON + Secreto Privado)
        String macFactura = digest.generateHash(jsonFactura + secWallet);

        // 3. Guardar en Movimientos.txt ANTES de enviar
        movimientos.guardarMovimiento(jsonFactura, macFactura);
        System.out.println("[TPV] Factura guardada en movimientos.");

        // 4. O7: TPV -> Wallet (Enviar Factura)
        System.out.println("[TPV] Enviando O7 (Factura)...");
        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O7, jsonFactura, macFactura));
        salida.flush();

        // 5. O8: Wallet -> TPV (Recibir Pago)
        APPOfflineMensaje msgO8 = (APPOfflineMensaje) entrada.readObject();
        if (msgO8.getPrimitiva() == APPOfflinePrimitiva.O8) {
            String jsonPago = msgO8.getArg1();
            String macPago = msgO8.getArg2();

            // 6. Guardar el pago recibido en Movimientos.txt
            movimientos.guardarMovimiento(jsonPago, macPago);
            System.out.println("[TPV] Pago O8 recibido y guardado en movimientos. ¡Venta completada!");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }

            String ingresosActuales = registro.getValor("IngresosEC");
            if (ingresosActuales == null)
                ingresosActuales = "0.0";
            double nuevosIngresos = Double.parseDouble(ingresosActuales) + Double.parseDouble(cantidad);
            registro.setValor("IngresosEC", String.valueOf(nuevosIngresos));

        } else {
            System.out.println("[TPV] Error: Se esperaba el pago O8 pero llegó " + msgO8.getPrimitiva());
            return; // Abortamos si no hay pago
        }

        // 7. Despedida O9 y O10
        System.out.println("[TPV] Enviando O9 (Final)...");
        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O9));
        salida.flush();

        APPOfflineMensaje msgO10 = (APPOfflineMensaje) entrada.readObject();
        if (msgO10.getPrimitiva() == APPOfflinePrimitiva.O10) {
            System.out.println("[TPV] Recibido O10 (Final OK). Desconexión segura.");
        }
    }

    private void cerrar() {
        try {
            if (salida != null)
                salida.close();
            if (entrada != null)
                entrada.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            System.out.println("[TPV] Error cerrando recursos: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            // Asegúrate de que tienes un archivo Movimientos.txt vacío o creado en la raíz
            TPVOffline tpv = new TPVOffline("Registro.json", "Movimientos.txt");
            tpv.iniciar();
        } catch (IOException e) {
            System.err.println("[TPV] Error de I/O al arrancar: " + e.getMessage());
        }
    }
}