package eurocoin.server;

import eurocoin.common.APPBancoPrimitive;
import eurocoin.common.BancoInterface;
import java.io.*;
import java.net.Socket;
import utiles.digest.Digest;
import utiles.registro.Registro;

public class Sirviente extends Thread implements BancoInterface {

    private Socket socketCliente;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private Registro registroBanco;
    private Digest digest;

    // Guardamos el desafío del banco a nivel de clase para poder usarlo en los métodos de la interfaz
    private String desafioBanco;

    public Sirviente(Socket socketCliente) {
        this.socketCliente = socketCliente;
    }

    @Override
    public void run() {
        try {
            // 1. Preparar conexiones
            salida = new ObjectOutputStream(socketCliente.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socketCliente.getInputStream());

            // 2. Cargar utilidades (usamos la nueva clase RegistroBanco)
            registroBanco = new Registro("RegistroBanco.json");
            digest = new Digest("SHA-256"); // Usamos el mismo algoritmo que la APP

            // ---- INICIO DEL PROTOCOLO ----
            
            // A1: Banco -> APP saludo(desafio_banco)
            desafioBanco = Long.toHexString(System.currentTimeMillis());
            System.out.println("[Sirviente] Enviando A1 y desafioBanco: " + desafioBanco);
            salida.writeObject(APPBancoPrimitive.A1);
            salida.writeObject(desafioBanco);
            salida.flush();

            // A2: APP -> Banco saludo(desafio_app, id_cliente, hash)
            APPBancoPrimitive prim = (APPBancoPrimitive) entrada.readObject();
            
            if (prim == APPBancoPrimitive.A2) {
                String desafioApp = (String) entrada.readObject();
                String idCliente = (String) entrada.readObject();
                String hashCliente = (String) entrada.readObject();
                
                System.out.println("[Sirviente] Recibido A2 del cliente: " + idCliente);

                // --- ¡MAGIA DE LA INTERFAZ! ---
                // Delegamos la verificación al método obligatorio de BancoInterface
                saludo(idCliente, desafioApp, hashCliente);

                // Esperamos la respuesta de la APP a nuestra autenticación (A5 o A6)
                APPBancoPrimitive respuestaApp = (APPBancoPrimitive) entrada.readObject();
                
                if (respuestaApp == APPBancoPrimitive.A5) {
                    System.out.println("[Sirviente] ¡Autenticación mutua OK (A5)!");
                    
                    // Esperamos a que la APP quiera desconectarse (A7)
                    APPBancoPrimitive operacion = (APPBancoPrimitive) entrada.readObject();
                    if (operacion == APPBancoPrimitive.A7) {
                        m_final(); // Llamamos al método de la interfaz para cerrar
                    }
                } else if (respuestaApp == APPBancoPrimitive.A6) {
                    System.out.println("[Sirviente] La APP rechazó nuestra autenticación (A6).");
                }
            } else {
                System.out.println("[Sirviente] Se esperaba A2, llegó: " + prim);
            }

        } catch (Exception e) {
            System.err.println("[Sirviente] Fin de conexión o error: " + e.getMessage());
        } finally {
            // Limpieza de recursos
            try {
                if (salida != null) salida.close();
                if (entrada != null) entrada.close();
                if (socketCliente != null) socketCliente.close();
                System.out.println("[Sirviente] Socket cerrado.");
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    // =========================================================================
    // IMPLEMENTACIÓN DE BancoInterface
    // =========================================================================

    @Override
    public void saludo(String id_cliente, String desafioApp, String hashCliente) {
        try {
            // Juntamos el ID con lo que buscamos (ej: "PedroLuis_secwallet")
            String secretoApp = registroBanco.getValor(id_cliente + "_secwallet");
            
            // Si el cliente no existe, abortamos
            if (secretoApp == null) {
                System.out.println("[Sirviente] Usuario desconocido.");
                saludo_err();
                return;
            }

            // Calculamos el hash esperado
            String hashCalculado = digest.generateHash(desafioBanco + secretoApp);

            // Comparamos
            if (!hashCalculado.equals(hashCliente)) {
                System.out.println("[Sirviente] El hash no coincide.");
                saludo_err(); // A3
            } else {
                // Todo correcto: calculamos la respuesta del banco
                // Buscamos el secreto del banco para este usuario (ej: "PedroLuis_SecBanco")
                String secretoBanco = registroBanco.getValor(id_cliente + "_SecBanco");
                String hashParaApp = digest.generateHash(desafioApp + secretoBanco);

                System.out.println("[Sirviente] Cliente verificado. Enviando A4.");
                salida.writeObject(APPBancoPrimitive.A4);
                salida.writeObject(hashParaApp);
                salida.flush();
                saludo_ok();
            }
        } catch (Exception e) {
            System.out.println("[Sirviente] Error verificando al cliente.");
            saludo_err();
        }
    }

    @Override
    public void saludo_err() {
        try {
            System.out.println("[Sirviente] Enviando A3 (Error de autenticación).");
            salida.writeObject(APPBancoPrimitive.A3);
            salida.flush();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void saludo_ok() {
        // Este método simplemente confirma internamente que el banco aceptó al cliente
        System.out.println("[Sirviente] Registro interno: saludo_ok procesado.");
    }

    @Override
    public void m_final() {
        try {
            System.out.println("[Sirviente] Recibido A7. Enviando A8 (Final OK).");
            salida.writeObject(APPBancoPrimitive.A8);
            salida.flush();
        } catch (IOException e) { e.printStackTrace(); }
    }
}