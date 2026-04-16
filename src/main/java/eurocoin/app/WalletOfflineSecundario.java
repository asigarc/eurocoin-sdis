package eurocoin.app;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import eurocoin.common.APPOfflineMensaje;
import eurocoin.common.APPOfflinePrimitive;
import eurocoin.util.Digest;
import eurocoin.util.Movimientos;
import eurocoin.util.Registro;

public class WalletOfflineSecundario {

    public static void main(String[] args) {
        
        // --- INICIO DE SEGURIDAD LOCAL ---
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        if (!eurocoin.util.AutenticadorLocal.verificarContrasena(scanner)) {
            System.out.println("\n[ERROR] Se han agotado los intentos o hubo un error.");
            System.out.println("Invalidación de la APP. Cerrando sistema...");
            scanner.close();
            System.exit(1); 
        }
        // --- FIN DE SEGURIDAD LOCAL ---
        
        int puerto = 3000;

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("[Secundario] Servidor offline activo en puerto " + puerto);

            while (true) {
                Socket socketPrimario = serverSocket.accept();
                atenderPrimario(socketPrimario);
            }
        } catch (Exception e) {
            System.err.println("[Secundario] Error en ServerSocket: " + e.getMessage());
        }
    }

    private static void atenderPrimario(Socket socket) {
        try (ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())) {

            salida.flush();

            Registro registro = new Registro("Registro.json");
            Digest digest = new Digest(registro.getValor("FuncionHash"));
            Movimientos movimientos = new Movimientos("Movimientos.txt");
            Gson gson = new Gson();

            String masterKey = registro.getValor("Masterkey");
            String secWallet = registro.getValor("SecWallet");
            String miNombre = registro.getValor("usuario");

            // =========================================================
            // FASE 1: AUTENTICACIÓN OFFLINE (O1-O4) -> MASTERKEY
            // =========================================================

            Object objO1 = entrada.readObject();
            if (!(objO1 instanceof APPOfflineMensaje)) {
                System.out.println("[Secundario] Error: Se esperaba APPOfflineMensaje en O1.");
                return;
            }

            APPOfflineMensaje msgO1 = (APPOfflineMensaje) objO1;
            if (msgO1.getPrimitiva() != APPOfflinePrimitive.O1) {
                System.out.println("[Secundario] Error: Se esperaba O1.");
                return;
            }

            String desafioPrim = msgO1.getArg1();
            String desafioSec = "DesafSec-" + System.currentTimeMillis();

            String hashParaPrim = digest.generateHash(desafioPrim + masterKey);
            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O2, desafioSec, hashParaPrim));
            salida.flush();

            Object objO3 = entrada.readObject();
            if (!(objO3 instanceof APPOfflineMensaje)) {
                System.out.println("[Secundario] Error: Se esperaba APPOfflineMensaje en O3.");
                return;
            }

            APPOfflineMensaje msgO3 = (APPOfflineMensaje) objO3;
            if (msgO3.getPrimitiva() != APPOfflinePrimitive.O3) {
                System.out.println("[Secundario] Error: Se esperaba O3.");
                return;
            }

            String hashEsperado = digest.generateHash(desafioSec + masterKey);
            if (!hashEsperado.equals(msgO3.getArg2())) {
                System.out.println("[Secundario] Error: Fallo de autenticación con MasterKey.");
                return;
            }

            String nombrePrim = msgO3.getArg1();

            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O4, miNombre));
            salida.flush();

            // =========================================================
            // FASE 2: OPERACIÓN OFFLINE (O5/O7 -> O6/O8)
            // =========================================================

            Object objTx = entrada.readObject();
            if (!(objTx instanceof APPOfflineMensaje)) {
                System.out.println("[Secundario] Error: Se esperaba APPOfflineMensaje en fase de operación.");
                return;
            }

            APPOfflineMensaje msgTx = (APPOfflineMensaje) objTx;

            if (msgTx.getPrimitiva() == APPOfflinePrimitive.O5) {
                // -----------------------------------------------------
                // CASO O5: INGRESO -> O6 RECIBO
                // -----------------------------------------------------
                String jsonIngreso = msgTx.getArg1();
                String macIngreso = msgTx.getArg2();

                /*
                 * Nota de diseño:
                 * El receptor no dispone del secreto privado del emisor, por lo que
                 * almacena (documento, firma) como evidencia para posterior consolidación.
                 */
                movimientos.guardarMovimiento(jsonIngreso, macIngreso);

                Map<String, String> datos = gson.fromJson(
                        jsonIngreso,
                        new TypeToken<Map<String, String>>() {}.getType());

                String cantidad = datos.get("cantidad");

                String jsonRecibo = "{ \"op\": \"recibo\", \"de\": \"" + nombrePrim
                        + "\", \"a\": \"" + miNombre
                        + "\", \"cantidad\": \"" + cantidad + "\"}";
                String macRecibo = digest.generateHash(jsonRecibo + secWallet);

                movimientos.guardarMovimiento(jsonRecibo, macRecibo);
                salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O6, jsonRecibo, macRecibo));
                salida.flush();

                String ingresosStr = registro.getValor("IngresosEC");
                double ingresosActuales = (ingresosStr == null || ingresosStr.trim().isEmpty())
                        ? 0.0
                        : Double.parseDouble(ingresosStr);

                double total = ingresosActuales + Double.parseDouble(cantidad);
                registro.setValor("IngresosEC", String.valueOf(total));

            } else if (msgTx.getPrimitiva() == APPOfflinePrimitive.O7) {
                // -----------------------------------------------------
                // CASO O7: FACTURA -> O8 PAGO
                // -----------------------------------------------------
                String jsonFactura = msgTx.getArg1();
                String macFactura = msgTx.getArg2();

                /*
                 * Igual que en O5, se conserva la evidencia (documento, firma)
                 * para consolidación posterior.
                 */
                movimientos.guardarMovimiento(jsonFactura, macFactura);

                Map<String, String> datos = gson.fromJson(
                        jsonFactura,
                        new TypeToken<Map<String, String>>() {}.getType());

                String cantidadStr = datos.get("cantidad");
                String idFact = datos.get("idFactura");

                double cantidad = Double.parseDouble(cantidadStr);

                String fondosStr = registro.getValor("FondosEC");
                double saldoActual = (fondosStr == null || fondosStr.trim().isEmpty())
                        ? 0.0
                        : Double.parseDouble(fondosStr);

                if (saldoActual < cantidad) {
                    System.out.println("[Secundario] Error: Saldo insuficiente.");
                    return;
                }

                String jsonPago = "{ \"op\": \"pago\", \"de\": \"" + miNombre
                        + "\", \"a\": \"" + nombrePrim
                        + "\", \"cantidad\": \"" + cantidadStr
                        + "\", \"idFactura\": \"" + idFact + "\"}";
                String macPago = digest.generateHash(jsonPago + secWallet);

                movimientos.guardarMovimiento(jsonPago, macPago);
                salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O8, jsonPago, macPago));
                salida.flush();

                registro.setValor("FondosEC", String.valueOf(saldoActual - cantidad));

            } else {
                System.out.println("[Secundario] Error: Se esperaba O5 u O7, se recibió "
                        + msgTx.getPrimitiva());
                return;
            }

            // =========================================================
            // FASE 3: CIERRE NORMAL (O9-O10)
            // =========================================================

            Object objO9 = entrada.readObject();
            if (!(objO9 instanceof APPOfflineMensaje)) {
                System.out.println("[Secundario] Error: Se esperaba APPOfflineMensaje en cierre.");
                return;
            }

            APPOfflineMensaje msgO9 = (APPOfflineMensaje) objO9;
            if (msgO9.getPrimitiva() == APPOfflinePrimitive.O9) {
                salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O10));
                salida.flush();
                System.out.println("[Secundario] Sesión cerrada correctamente.");
            } else {
                System.out.println("[Secundario] Error: Se esperaba O9.");
            }

        } catch (Exception e) {
            System.err.println("[Secundario] Error: " + e.getMessage());
        } finally {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (Exception e) { }
        }
    }
}