package eurocoin.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import eurocoin.common.APPOfflineMensaje;
import eurocoin.common.APPOfflinePrimitiva;
import utiles.digest.Digest;
import utiles.registro.Registro;

public class WalletSecundario {

    public static void main(String[] args) {
        int puerto = 3000; // El puerto offline que pide la práctica

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("[WalletSecundario] Iniciado. Esperando conexiones en el puerto " + puerto + "...");

            while (true) {
                // Esperamos a que un Primario se conecte (¡sin hilos, uno a uno!)
                Socket socketPrimario = serverSocket.accept();
                System.out.println("[WalletSecundario] ¡Primario conectado! IP: " + socketPrimario.getInetAddress());

                atenderPrimario(socketPrimario);
            }
        } catch (Exception e) {
            System.err.println("[WalletSecundario] Error crítico: " + e.getMessage());
        }
    }

    private static void atenderPrimario(Socket socket) {
        try {
            ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

            // --- FASE 2: EL PROTOCOLO OFFLINE ---
            System.out.println("[WalletSecundario] Listo para iniciar la autenticación offline...");

            Registro registro = new Registro("Registro.json");
            Digest digest = new Digest(registro.getValor("FuncionHash"));
            String masterKey = registro.getValor("Masterkey");
            
            // Como estamos probando en el mismo PC con el mismo JSON, le añado "-TPV" para distinguir los nombres en consola
            String miNombre = registro.getValor("usuario") + "-TPV"; 

            // 1. Esperar O1 del Primario
            APPOfflineMensaje msgO1 = (APPOfflineMensaje) entrada.readObject();
            if (msgO1.getPrimitiva() == APPOfflinePrimitiva.O1) {
                String desafioPrim = msgO1.getArg1();
                System.out.println("[WalletSecundario] Recibido O1. Desafío del primario: " + desafioPrim);

                // 2. Enviar O2 (Resolución y nuestro desafío)
                String desafioSec = Long.toHexString(System.currentTimeMillis() + 1000); // +1000 para que sea distinto al suyo
                String hashParaPrim = digest.generateHash(desafioPrim + masterKey);
                
                System.out.println("[WalletSecundario] Enviando O2...");
                salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O2, desafioSec, hashParaPrim));
                salida.flush();

                // 3. Esperar O3 (Resolución del Primario)
                APPOfflineMensaje msgO3 = (APPOfflineMensaje) entrada.readObject();
                if (msgO3.getPrimitiva() == APPOfflinePrimitiva.O3) {
                    String nombrePrim = msgO3.getArg1();
                    String hashRecibido = msgO3.getArg2();
                    
                    String hashEsperado = digest.generateHash(desafioSec + masterKey);
                    
                    if (!hashEsperado.equals(hashRecibido)) {
                        System.out.println("[WalletSecundario] Error grave de autenticación: el Hash del Primario es falso.");
                        return; // Abortamos
                    }

                    System.out.println("[WalletSecundario] Primario verificado (" + nombrePrim + "). Enviando O4...");
                    
                    // 4. Enviar O4 (Nuestra identidad)
                    salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O4, miNombre));
                    salida.flush();
                    System.out.println("[WalletSecundario] ¡Autenticación completada con éxito!");
                    
                    // Aquí irá la FASE 3 (El pago)
                    
                } else {
                    System.out.println("[WalletSecundario] Se esperaba O3, pero llegó: " + msgO3.getPrimitiva());
                }
            } else {
                System.out.println("[WalletSecundario] Se esperaba O1, pero llegó: " + msgO1.getPrimitiva());
            }

        } catch (Exception e) {
            System.err.println("[WalletSecundario] Error de comunicación: " + e.getMessage());
        } finally {
            try {
                if (socket != null) socket.close();
                System.out.println("[WalletSecundario] Conexión cerrada.");
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
}