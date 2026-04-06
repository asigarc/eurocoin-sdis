package eurocoin.client;

import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
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

            // --- PREPARACIÓN ---
            Registro registro = new Registro("Registro.json");
            Digest digest = new Digest(registro.getValor("FuncionHash"));
            String masterKey = registro.getValor("Masterkey");
            String miNombre = registro.getValor("usuario") + "-TPV"; 
            String miSecreto = registro.getValor("Secwallet");

            // --- FASE 2: AUTENTICACIÓN OFFLINE ---
            System.out.println("[WalletSecundario] Listo para iniciar la autenticación offline...");
            
            APPOfflineMensaje msgO1 = (APPOfflineMensaje) entrada.readObject();
            if (msgO1.getPrimitiva() == APPOfflinePrimitiva.O1) {
                String desafioPrim = msgO1.getArg1();
                
                String desafioSec = Long.toHexString(System.currentTimeMillis() + 1000);
                String hashParaPrim = digest.generateHash(desafioPrim + masterKey);
                
                salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O2, desafioSec, hashParaPrim));
                salida.flush();

                APPOfflineMensaje msgO3 = (APPOfflineMensaje) entrada.readObject();
                if (msgO3.getPrimitiva() == APPOfflinePrimitiva.O3) {
                    String nombrePrim = msgO3.getArg1();
                    String hashRecibido = msgO3.getArg2();
                    String hashEsperado = digest.generateHash(desafioSec + masterKey);
                    
                    if (!hashEsperado.equals(hashRecibido)) {
                        System.out.println("[WalletSecundario] Error grave de autenticación.");
                        return;
                    }

                    salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O4, miNombre));
                    salida.flush();
                    System.out.println("[WalletSecundario] ¡Autenticación completada con éxito!");

                    // --- FASE 3: EL COBRO ---
                    System.out.println("[WalletSecundario] Esperando transacción (O5)...");
                    APPOfflineMensaje msgO5 = (APPOfflineMensaje) entrada.readObject();
                    
                    if (msgO5.getPrimitiva() == APPOfflinePrimitiva.O5) {
                        System.out.println("[WalletSecundario] Ingreso recibido (O5). Guardando...");
                        guardarMovimiento(msgO5.getArg1(), msgO5.getArg2());
                        
                        String jsonRecibo = "{ \"op\": \"recibo\", \"de\": \"" + nombrePrim + "\", \"a\": \"" + miNombre + "\", \"cantidad\": \"20\"}";
                        String macRecibo = digest.generateHash(jsonRecibo + miSecreto);
                        
                        guardarMovimiento(jsonRecibo, macRecibo);
                        
                        System.out.println("[WalletSecundario] Enviando recibo (O6)...");
                        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O6, jsonRecibo, macRecibo));
                        salida.flush();
                        
                        // --- FASE 4: ACTUALIZACIÓN DE SALDO Y DESPEDIDA ---
                        System.out.println("[WalletSecundario] Sumando 20 EuroCoins a IngresosEC...");
                        int ingresosActuales = Integer.parseInt(registro.getValor("IngresosEC"));
                        registro.setValor("IngresosEC", String.valueOf(ingresosActuales + 20));
                        
                        APPOfflineMensaje msgO9 = (APPOfflineMensaje) entrada.readObject();
                        if (msgO9.getPrimitiva() == APPOfflinePrimitiva.O9) {
                            System.out.println("[WalletSecundario] Recibido O9 (final). Enviando O10 (final_ok)...");
                            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O10));
                            salida.flush();
                            System.out.println("[WalletSecundario] Desconexión segura completada.");
                        }
                        
                    } else {
                        System.out.println("[WalletSecundario] Se esperaba O5.");
                    }
                } else {
                    System.out.println("[WalletSecundario] Se esperaba O3.");
                }
            } else {
                System.out.println("[WalletSecundario] Se esperaba O1.");
            }

        } catch (Exception e) {
            System.err.println("[WalletSecundario] Error: " + e.getMessage());
        } finally {
            try {
                if (socket != null) socket.close();
                System.out.println("[WalletSecundario] Conexión cerrada.");
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private static void guardarMovimiento(String json, String mac) {
        try (PrintWriter out = new PrintWriter(new FileWriter("Movimientos.txt", true))) {
            out.println(json);
            out.println(mac);
        } catch (IOException e) {
            System.err.println("Error al guardar en Movimientos.txt: " + e.getMessage());
        }
    }
}