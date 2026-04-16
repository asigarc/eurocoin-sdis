package eurocoin.app;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import eurocoin.common.APPOfflineMensaje;
import eurocoin.common.APPOfflinePrimitive;
import eurocoin.util.Digest;
import eurocoin.util.Movimientos;
import eurocoin.util.Registro;

public class WalletOfflinePrimario {

    public static void main(String[] args) {
        String host = "localhost";
        int puerto = 3000;

        try (Socket socket = new Socket(host, puerto);
             ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
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
            // FASE 1: HANDSHAKE OFFLINE (O1-O4) -> MASTERKEY
            // =========================================================
            //

            String desafP = "DesafP-" + System.currentTimeMillis();
            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O1, desafP));
            salida.flush();

            Object objO2 = entrada.readObject();
            if (!(objO2 instanceof APPOfflineMensaje) || ((APPOfflineMensaje)objO2).getPrimitiva() != APPOfflinePrimitive.O2) {
                System.out.println("[Primario] Error: Se esperaba O2.");
                return;
            }

            APPOfflineMensaje msgO2 = (APPOfflineMensaje) objO2;
            if (!digest.generateHash(desafP + masterKey).equals(msgO2.getArg2())) {
                System.out.println("[Primario] Seguridad: El dispositivo remoto no superó la validación MasterKey.");
                return;
            }

            String desafS = msgO2.getArg1();
            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O3, miNombre, digest.generateHash(desafS + masterKey)));
            salida.flush();

            Object objO4 = entrada.readObject();
            if (!(objO4 instanceof APPOfflineMensaje) || ((APPOfflineMensaje)objO4).getPrimitiva() != APPOfflinePrimitive.O4) {
                System.out.println("[Primario] Error: Se esperaba O4.");
                return;
            }
            String nombreSec = ((APPOfflineMensaje)objO4).getArg1();

            // =========================================================
            // FASE 2: OPERACIÓN OFFLINE (O5 -> O6) -> SECWALLET
            // =========================================================

            Scanner sc = new Scanner(System.in);
            System.out.print(">>> Cantidad a enviar a " + nombreSec + ": ");
            String cantEnviada = sc.nextLine();

            String fondosStr = registro.getValor("FondosEC");
            double saldoActual = (fondosStr == null || fondosStr.trim().isEmpty()) ? 0.0 : Double.parseDouble(fondosStr);
            double cantidadDouble = Double.parseDouble(cantEnviada);

            if (saldoActual < cantidadDouble) {
                System.out.println("[Primario] Abortando: Fondos insuficientes.");
                return;
            }

            // O5: Envío de ingreso firmado con SecWallet privado
            String jsonIng = "{ \"op\": \"ingreso\", \"de\": \"" + miNombre + "\", \"a\": \"" + nombreSec + "\", \"cantidad\": \"" + cantEnviada + "\"}";
            String macIng = digest.generateHash(jsonIng + secWallet);

            movimientos.guardarMovimiento(jsonIng, macIng);
            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O5, jsonIng, macIng));
            salida.flush();

            // Recibir O6 (Recibo)
            Object objO6 = entrada.readObject();
            if (!(objO6 instanceof APPOfflineMensaje) || ((APPOfflineMensaje)objO6).getPrimitiva() != APPOfflinePrimitive.O6) {
                System.out.println("[Primario] Error: No se recibió un recibo (O6) válido.");
                return;
            }

            APPOfflineMensaje msgO6 = (APPOfflineMensaje) objO6;
            String jsonRecibo = msgO6.getArg1();

            // VALIDACIÓN DE COHERENCIA DEL RECIBO
            Map<String, String> datosRecibo = gson.fromJson(jsonRecibo, new TypeToken<Map<String, String>>(){}.getType());
            String cantidadConfirmada = datosRecibo.get("cantidad");
            String deConfirmado = datosRecibo.get("de");
            String aConfirmado = datosRecibo.get("a");

            if (cantidadConfirmada == null || !cantEnviada.equals(cantidadConfirmada) ||
                deConfirmado == null || !miNombre.equals(deConfirmado) ||
                aConfirmado == null || !nombreSec.equals(aConfirmado)) {
                System.out.println("[Primario] ERROR: El recibo no es coherente con la transacción enviada.");
                return;
            }

            // Almacenar evidencia (notario) y consolidar saldo local
            movimientos.guardarMovimiento(jsonRecibo, msgO6.getArg2());
            registro.setValor("FondosEC", String.valueOf(saldoActual - cantidadDouble));
            System.out.println("[Primario] Transacción completada y recibo guardado.");

            // =========================================================
            // FASE 3: CIERRE (O9-O10)
            // =========================================================

            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitive.O9));
            salida.flush();

            Object objO10 = entrada.readObject();
            if (objO10 instanceof APPOfflineMensaje && ((APPOfflineMensaje)objO10).getPrimitiva() == APPOfflinePrimitive.O10) {
                System.out.println("[Primario] Sesión finalizada con éxito.");
            }

        } catch (Exception e) {
            System.err.println("[Primario] Error: " + e.getMessage());
        }
    }
}