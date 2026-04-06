package eurocoin.client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import eurocoin.common.APPOfflineMensaje;
import eurocoin.common.APPOfflinePrimitiva;
import utiles.digest.Digest;
import utiles.movimientos.Movimientos; // Importamos la clase de tu compañero
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

            // --- PREPARACIÓN ---
            Registro registro = new Registro("Registro.json");
            Digest digest = new Digest(registro.getValor("FuncionHash"));
            Movimientos movimientos = new Movimientos("Movimientos.txt"); // Instanciamos la clase Movimientos
            String masterKey = registro.getValor("Masterkey");
            String miNombre = registro.getValor("usuario");
            String miSecreto = registro.getValor("Secwallet");

            // --- FASE 2: AUTENTICACIÓN OFFLINE ---
            System.out.println("[WalletPrimario] Iniciando autenticación offline...");

            String desafioPrim = Long.toHexString(System.currentTimeMillis());
            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O1, desafioPrim));
            salida.flush();

            APPOfflineMensaje msgO2 = (APPOfflineMensaje) entrada.readObject();
            if (msgO2.getPrimitiva() == APPOfflinePrimitiva.O2) {
                String desafioSec = msgO2.getArg1();
                String hashRecibido = msgO2.getArg2();
                String hashEsperado = digest.generateHash(desafioPrim + masterKey);
                
                if (!hashEsperado.equals(hashRecibido)) {
                    System.out.println("[WalletPrimario] Error grave de autenticación.");
                    return;
                }

                String hashParaSec = digest.generateHash(desafioSec + masterKey);
                salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O3, miNombre, hashParaSec));
                salida.flush();

                APPOfflineMensaje msgO4 = (APPOfflineMensaje) entrada.readObject();
                if (msgO4.getPrimitiva() == APPOfflinePrimitiva.O4) {
                    String nombreSecundario = msgO4.getArg1();
                    System.out.println("[WalletPrimario] ¡Autenticación completada! Secundario es: " + nombreSecundario);
                    
                    // --- FASE 3: EL PAGO ---
                    System.out.println("[WalletPrimario] Iniciando transacción (O5)...");
                    String cantidad = "20"; 
                    
                    String jsonIngreso = "{ \"op\": \"ingreso\", \"de\": \"" + miNombre + "\", \"a\": \"" + nombreSecundario + "\", \"cantidad\": \"" + cantidad + "\"}";
                    String macIngreso = digest.generateHash(jsonIngreso + miSecreto);
                    
                    // Usamos el método de la clase Movimientos
                    movimientos.guardarMovimiento(jsonIngreso, macIngreso);
                    
                    salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O5, jsonIngreso, macIngreso));
                    salida.flush();
                    
                    APPOfflineMensaje msgO6 = (APPOfflineMensaje) entrada.readObject();
                    if (msgO6.getPrimitiva() == APPOfflinePrimitiva.O6) {
                        System.out.println("[WalletPrimario] Recibido recibo (O6) del Secundario. Guardando...");
                        
                        // Usamos el método de la clase Movimientos
                        movimientos.guardarMovimiento(msgO6.getArg1(), msgO6.getArg2());
                        System.out.println("[WalletPrimario] ¡Pago completado y guardado con éxito!");
                        
                        // --- FASE 4: ACTUALIZACIÓN DE SALDO Y DESPEDIDA ---
                        System.out.println("[WalletPrimario] Restando 20 EuroCoins de FondosEC...");
                        int fondosActuales = Integer.parseInt(registro.getValor("FondosEC"));
                        registro.setValor("FondosEC", String.valueOf(fondosActuales - 20));
                        
                        System.out.println("[WalletPrimario] Enviando O9 (final)...");
                        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O9));
                        salida.flush();
                        
                        APPOfflineMensaje msgO10 = (APPOfflineMensaje) entrada.readObject();
                        if (msgO10.getPrimitiva() == APPOfflinePrimitiva.O10) {
                            System.out.println("[WalletPrimario] Recibido O10 (final_ok). Desconexión segura completada.");
                        }
                        
                    } else {
                        System.out.println("[WalletPrimario] Se esperaba O6.");
                    }
                } else {
                    System.out.println("[WalletPrimario] Se esperaba O4.");
                }
            } else {
                System.out.println("[WalletPrimario] Se esperaba O2.");
            }

        } catch (Exception e) {
            System.err.println("[WalletPrimario] Error: " + e.getMessage());
        }
    }
}