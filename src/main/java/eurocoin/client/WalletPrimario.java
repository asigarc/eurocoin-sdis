package eurocoin.client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import eurocoin.common.APPOfflineMensaje;
import eurocoin.common.APPOfflinePrimitiva;
import utiles.digest.Digest;
import utiles.registro.Registro;

public class WalletPrimario {

    public static void main(String[] args) {
        String host = "localhost";
        int puerto = 3000;

        try (Socket socket = new Socket(host, puerto)) {
            System.out.println("[WalletPrimario] Conectado al Secundario en " + host + ":" + puerto);

            ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

            // --- FASE 2: AUTENTICACIÓN OFFLINE ---
            System.out.println("[WalletPrimario] Iniciando autenticación offline...");

            Registro registro = new Registro("Registro.json");
            Digest digest = new Digest(registro.getValor("FuncionHash"));
            String masterKey = registro.getValor("Masterkey");
            String miNombre = registro.getValor("usuario");

            // 1. Enviar O1 (Desafío Primario)
            String desafioPrim = Long.toHexString(System.currentTimeMillis());
            System.out.println("[WalletPrimario] Enviando O1 con desafío: " + desafioPrim);
            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O1, desafioPrim));
            salida.flush();

            // 2. Esperar O2 del Secundario
            APPOfflineMensaje msgO2 = (APPOfflineMensaje) entrada.readObject();
            if (msgO2.getPrimitiva() == APPOfflinePrimitiva.O2) {
                String desafioSec = msgO2.getArg1();
                String hashRecibido = msgO2.getArg2();
                
                // Comprobamos si resolvió bien nuestro desafío
                String hashEsperado = digest.generateHash(desafioPrim + masterKey);
                
                if (!hashEsperado.equals(hashRecibido)) {
                    System.out.println("[WalletPrimario] Error grave de autenticación: el Hash del Secundario es falso.");
                    return; // Abortamos
                }

                System.out.println("[WalletPrimario] Secundario verificado. Resolviendo su desafío...");

                // 3. Enviar O3 (Nuestra identidad y su desafío resuelto)
                String hashParaSec = digest.generateHash(desafioSec + masterKey);
                salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O3, miNombre, hashParaSec));
                salida.flush();

                // 4. Esperar O4 (Identidad del secundario)
                APPOfflineMensaje msgO4 = (APPOfflineMensaje) entrada.readObject();
                if (msgO4.getPrimitiva() == APPOfflinePrimitiva.O4) {
                    System.out.println("[WalletPrimario] ¡Autenticación completada! Secundario es: " + msgO4.getArg1());
                    
                    // Aquí irá la FASE 3 (El pago)
                    
                } else {
                    System.out.println("[WalletPrimario] Se esperaba O4, pero llegó: " + msgO4.getPrimitiva());
                }
            } else {
                System.out.println("[WalletPrimario] Se esperaba O2, pero llegó: " + msgO2.getPrimitiva());
            }

        } catch (Exception e) {
            System.err.println("[WalletPrimario] No se pudo conectar al Secundario o hubo un error: " + e.getMessage());
        }
    }
}