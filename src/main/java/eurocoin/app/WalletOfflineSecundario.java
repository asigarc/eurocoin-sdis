package eurocoin.app;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import eurocoin.common.APPOfflineMensaje;
import eurocoin.common.APPOfflinePrimitiva;
import eurocoin.common.MalMensajeProtocoloException;
import eurocoin.util.Digest;
import eurocoin.util.Movimientos;
import eurocoin.util.Registro;

public class WalletOfflineSecundario {

    public static void main(String[] args) {
        int puerto = 3000;

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

            Registro registro = new Registro("Registro.json");
            Digest digest = new Digest(registro.getValor("FuncionHash"));
            Movimientos movimientos = new Movimientos("Movimientos.txt"); 
            String masterKey = registro.getValor("Masterkey");
            String miNombre = registro.getValor("usuario"); 
            String miSecreto = registro.getValor("SecWallet");
            Gson gson = new Gson(); // Para parsear los JSON recibidos

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

                    System.out.println("[WalletSecundario] Esperando transacción (O5 ingreso o O7 factura)...");
                    APPOfflineMensaje msgTx = (APPOfflineMensaje) entrada.readObject();

                    // CASO 1: OTRO WALLET NOS MANDA DINERO (O5)

                    if (msgTx.getPrimitiva() == APPOfflinePrimitiva.O5) {
                        // Extraemos la cantidad que nos han enviado dinámicamente
                        Map<String, String> datosIngreso = gson.fromJson(msgTx.getArg1(), Map.class);
                        String cantidadRecibida = datosIngreso.get("cantidad");
                        
                        System.out.println("[WalletSecundario] Ingreso recibido (O5) por valor de " + cantidadRecibida + " EC. Guardando...");
                        movimientos.guardarMovimiento(msgTx.getArg1(), msgTx.getArg2());
                        
                        String jsonRecibo = "{ \"op\": \"recibo\", \"de\": \"" + nombrePrim + "\", \"a\": \"" + miNombre + "\", \"cantidad\": \"" + cantidadRecibida + "\"}";
                        String macRecibo = digest.generateHash(jsonRecibo + miSecreto);
                        
                        movimientos.guardarMovimiento(jsonRecibo, macRecibo);
                        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O6, jsonRecibo, macRecibo));
                        salida.flush();
                        
                        // Actualizamos nuestros ingresos
                        double ingresosActuales = Double.parseDouble(registro.getValor("IngresosEC"));
                        registro.setValor("IngresosEC", String.valueOf(ingresosActuales + Double.parseDouble(cantidadRecibida)));
                        
                        APPOfflineMensaje msgO9 = (APPOfflineMensaje) entrada.readObject();
                        if (msgO9.getPrimitiva() == APPOfflinePrimitiva.O9) {
                            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O10));
                            salida.flush();
                            System.out.println("[WalletSecundario] Desconexión segura completada.");
                        }

                    // CASO 2: UN TPV NOS MANDA UNA FACTURA PARA QUE PAGUEMOS (O7)

                    } else if (msgTx.getPrimitiva() == APPOfflinePrimitiva.O7) {
                        // Extraemos la cantidad que nos piden y el ID de la factura
                        Map<String, String> datosFactura = gson.fromJson(msgTx.getArg1(), Map.class);
                        String cantidadAPagar = datosFactura.get("cantidad");
                        String idFactura = datosFactura.get("idFactura");
                        
                        System.out.println("[WalletSecundario] Factura " + idFactura + " recibida (O7) por valor de " + cantidadAPagar + " EC. Procesando pago...");
                        movimientos.guardarMovimiento(msgTx.getArg1(), msgTx.getArg2());
                        
                        // Generamos el pago (O8) usando los datos dinámicos
                        String jsonPago = "{ \"op\": \"pago\", \"de\": \"" + nombrePrim + "\", \"a\": \"" + miNombre + "\", \"cantidad\": \"" + cantidadAPagar + "\", \"idFactura\": \"" + idFactura + "\"}";
                        String macPago = digest.generateHash(jsonPago + miSecreto);
                        
                        movimientos.guardarMovimiento(jsonPago, macPago);
                        salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O8, jsonPago, macPago));
                        salida.flush();

                        // Descontamos el saldo
                        double fondosActuales = Double.parseDouble(registro.getValor("FondosEC"));
                        registro.setValor("FondosEC", String.valueOf(fondosActuales - Double.parseDouble(cantidadAPagar))); 
                        
                        APPOfflineMensaje msgO9 = (APPOfflineMensaje) entrada.readObject();
                        if (msgO9.getPrimitiva() == APPOfflinePrimitiva.O9) {
                            salida.writeObject(new APPOfflineMensaje(APPOfflinePrimitiva.O10));
                            salida.flush();
                            System.out.println("[WalletSecundario] Pago al TPV y desconexión completada.");
                        }
                    } else {
                        System.out.println("[WalletSecundario] Mensaje inesperado. Se esperaba O5 o O7.");
                    }
                }
            }
        } catch (JsonSyntaxException | MalMensajeProtocoloException | IOException | ClassNotFoundException | NumberFormatException e) {
            System.err.println("[WalletSecundario] Error: " + e.getMessage());
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
}