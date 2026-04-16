package eurocoin.app;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import eurocoin.common.APPOfflineMensaje;
import eurocoin.common.APPOfflinePrimitive;
import eurocoin.common.MalMensajeProtocoloException;
import eurocoin.util.Digest;
import eurocoin.util.Movimientos;
import eurocoin.util.Registro;

public class TPVOffline {

    private static final String HOST_CLIENTE = "localhost";
    private static final int PUERTO_CLIENTE = 3000;

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private final Scanner teclado;

    private final Registro registro;
    private final Digest digest;
    private final Movimientos movimientos;
    private final Gson gson;

    private final String idVendedor;
    private final String masterKey;
    private String idCliente; 

    public TPVOffline(String archivoRegistro, String archivoMovimientos) throws IOException {
        this.registro = new Registro(archivoRegistro);
        this.digest = new Digest(registro.getValor("FuncionHash"));
        this.movimientos = new Movimientos(archivoMovimientos);
        this.teclado = new Scanner(System.in);
        this.gson = new Gson();

        this.idVendedor = registro.getValor("usuario");
        this.masterKey = registro.getValor("Masterkey");
    }

    public void iniciar() {
        try {
            if (!conectar()) return;

            if (autenticarOffline()) {
                System.out.println("[TPV] Autenticación exitosa con " + idCliente + ". Iniciando facturación...");
                facturar();
            } else {
                System.out.println("[TPV] Abortando: Fallo en la fase de Handshake.");
            }
        } catch (Exception e) {
            System.err.println("[TPV] Error crítico: " + e.getMessage());
        } finally {
            cerrar();
        }
    }

    private boolean conectar() {
        try {
            socket = new Socket(HOST_CLIENTE, PUERTO_CLIENTE);
            salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socket.getInputStream());
            return true;
        } catch (IOException e) {
            System.out.println("[TPV] No se pudo conectar con el cliente: " + e.getMessage());
            return false;
        }
    }

    private boolean autenticarOffline() throws IOException, ClassNotFoundException, MalMensajeProtocoloException {
        // O1: TPV -> Wallet
        String desafP = "DesafTPV-" + System.currentTimeMillis();
        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O1, desafP));
        salida.flush();

        // O2: Recibir y validar
        Object objO2 = entrada.readObject();
        if (!(objO2 instanceof APPOfflineMensaje)) {
            System.out.println("[TPV] Error O2: Tipo de objeto inesperado.");
            return false;
        }
        APPOfflineMensaje msgO2 = (APPOfflineMensaje) objO2;
        if (msgO2.getPrimitiva() != APPOfflinePrimitive.O2) {
            System.out.println("[TPV] Error O2: Se esperaba O2 y llegó " + msgO2.getPrimitiva());
            return false;
        }

        if (!digest.generateHash(desafP + masterKey).equals(msgO2.getArg2())) {
            System.out.println("[TPV] SEGURIDAD: El dispositivo remoto no superó la prueba MasterKey.");
            return false;
        }

        // O3: Responder desafío del secundario
        String desafS = msgO2.getArg1();
        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O3, idVendedor, digest.generateHash(desafS + masterKey)));
        salida.flush();

        // O4: Confirmación final de Handshake
        Object objO4 = entrada.readObject();
        if (!(objO4 instanceof APPOfflineMensaje)) {
            System.out.println("[TPV] Error O4: Tipo de objeto inesperado.");
            return false;
        }
        APPOfflineMensaje msgO4 = (APPOfflineMensaje) objO4;
        if (msgO4.getPrimitiva() != APPOfflinePrimitive.O4) {
            System.out.println("[TPV] Error O4: Se esperaba O4.");
            return false;
        }

        this.idCliente = msgO4.getArg1();
        return true;
    }

    private void facturar() throws IOException, ClassNotFoundException, MalMensajeProtocoloException {
        System.out.print(">>> Importe de la venta: ");
        String cantEntrada = teclado.nextLine();
        String idFact = "FACT-" + System.currentTimeMillis();

        String secWallet = registro.getValor("SecWallet");
        String jsonFact = "{ \"op\": \"factura\", \"de\": \"" + idVendedor 
                        + "\", \"a\": \"" + idCliente 
                        + "\", \"cantidad\": \"" + cantEntrada 
                        + "\", \"idFactura\": \"" + idFact + "\" }";
        
        String macFact = digest.generateHash(jsonFact + secWallet);
        movimientos.guardarMovimiento(jsonFact, macFact);

        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O7, jsonFact, macFact));
        salida.flush();

        // O8: Pago
        Object objO8 = entrada.readObject();
        if (!(objO8 instanceof APPOfflineMensaje) || ((APPOfflineMensaje)objO8).getPrimitiva() != APPOfflinePrimitive.O8) {
            System.out.println("[TPV] El cliente no envió el pago (O8) esperado.");
            return;
        }

        APPOfflineMensaje msgO8 = (APPOfflineMensaje) objO8;
        String jsonPago = msgO8.getArg1();

        // Validación de coherencia
        Map<String, String> datosPago = gson.fromJson(jsonPago, new TypeToken<Map<String, String>>(){}.getType());
        if (!cantEntrada.equals(datosPago.get("cantidad")) || !idFact.equals(datosPago.get("idFactura"))) {
            System.out.println("[TPV] FRAUDE DETECTADO: El pago no coincide con la factura.");
            return;
        }

        // Almacenar evidencia (notario)
        movimientos.guardarMovimiento(jsonPago, msgO8.getArg2());

        // Actualizar ingresos con protección null/empty
        String ingresosStr = registro.getValor("IngresosEC");
        double ingresosPrevios = (ingresosStr == null || ingresosStr.trim().isEmpty()) ? 0.0 : Double.parseDouble(ingresosStr);
        registro.setValor("IngresosEC", String.valueOf(ingresosPrevios + Double.parseDouble(cantEntrada)));

        System.out.println("[TPV] ¡Venta cobrada! Esperando cierre seguro...");

        // O9-O10: Despedida
        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O9));
        salida.flush();

        Object objO10 = entrada.readObject();
        if (objO10 instanceof APPOfflineMensaje && ((APPOfflineMensaje)objO10).getPrimitiva() == APPOfflinePrimitive.O10) {
            System.out.println("[TPV] Operación finalizada con éxito.");
        }
    }

    private void cerrar() {
        try {
            // No cerramos el Scanner para no matar el System.in de la APP principal
            if (salida != null) salida.close();
            if (entrada != null) entrada.close();
            if (socket != null) socket.close();
        } catch (IOException e) { }
    }

    public static void main(String[] args) {
        try {
            new TPVOffline("Registro.json", "Movimientos.txt").iniciar();
        } catch (IOException e) {
            System.err.println("[TPV] Fallo al cargar configuración.");
        }
    }
}