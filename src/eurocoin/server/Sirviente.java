package eurocoin.server;

import eurocoin.common.APPBancoPrimitive;
import java.io.*;
import java.net.Socket;
import utiles.digest.Digest;
import utiles.registro.Registro;

public class Sirviente extends Thread {

    private Socket socketCliente;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private Registro registroBanco;
    private Digest digest;

    public Sirviente(Socket socketCliente) {
        this.socketCliente = socketCliente;
    }

    @Override
    public void run() {
        try {
            // 1. CONFIGURACIÓN INICIAL
            // Abrimos los canales de comunicación. Siempre primero el de salida para evitar bloqueos.
            salida = new ObjectOutputStream(socketCliente.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socketCliente.getInputStream());
            
            // Cargamos el registro del banco (suponiendo que lo hemos adaptado para leer el JSON anidado)
            registroBanco = new Registro("RegistroBanco.json");
            String hashFunc = registroBanco.getValor("FuncionHash"); 
            digest = new Digest(hashFunc);

            // ---------------------------------------------------------
            // FASE 1: EL BANCO DESAFÍA AL CLIENTE
            // ---------------------------------------------------------
            
            // Generamos un desafío aleatorio para evitar que un atacante repita mensajes antiguos
            String desafioBanco = Long.toHexString(System.currentTimeMillis());
            System.out.println("[Sirviente] Enviando A1 y desafioBanco: " + desafioBanco);
            
            // Enviamos la primitiva A1 seguida del texto del desafío
            salida.writeObject(APPBancoPrimitive.A1);
            salida.writeObject(desafioBanco);
            salida.flush();

            // ---------------------------------------------------------
            // FASE 2: EL CLIENTE RESPONDE AL DESAFÍO
            // ---------------------------------------------------------
            
            // Nos quedamos esperando a que el cliente nos envíe un A2 con sus datos
            APPBancoPrimitive prim = (APPBancoPrimitive) entrada.readObject();
            
            if (prim == APPBancoPrimitive.A2) {
                // Leemos los 3 datos que el cliente nos envía en orden
                String desafioApp = (String) entrada.readObject();
                String idCliente = (String) entrada.readObject();
                String hashCliente = (String) entrada.readObject();
                
                System.out.println("[Sirviente] Recibido A2 de " + idCliente);

                // ---------------------------------------------------------
                // FASE 3: VERIFICACIÓN DEL CLIENTE Y RESPUESTA DEL BANCO
                // ---------------------------------------------------------
                
                // Buscamos el secreto de este cliente específico en el registro del Banco
                // (Nota: aquí asumimos que podemos acceder al secreto del cliente con su ID)
                String secretoApp = registroBanco.getValor(idCliente + "_secwallet"); 
                
                // Calculamos el hash nosotros mismos usando el desafío que enviamos antes y el secreto
                String hashCalculado = digest.generateHash(desafioBanco + secretoApp);

                // Comparamos si el hash que calculamos es igual al que nos envió el cliente
                if (!hashCalculado.equals(hashCliente)) {
                    // Si no coinciden, es un atacante o la clave está mal. Enviamos A3 (Error) y cortamos.
                    System.out.println("[Sirviente] Error de autenticación. Enviando A3.");
                    salida.writeObject(APPBancoPrimitive.A3);
                    salida.flush();
                    return; // Terminamos la ejecución del hilo
                }

                // Si coinciden, el cliente es legítimo. Ahora el Banco tiene que demostrar su identidad.
                // Buscamos el secreto del banco para este cliente
                String secretoBanco = registroBanco.getValor(idCliente + "_SecBanco");
                
                // Calculamos el hash usando el desafío que nos mandó la APP y el secreto del banco
                String hashParaApp = digest.generateHash(desafioApp + secretoBanco);

                // Enviamos A4 (Todo OK) junto con nuestro hash para que la APP nos verifique
                System.out.println("[Sirviente] Cliente verificado. Enviando A4 y hash del Banco.");
                salida.writeObject(APPBancoPrimitive.A4);
                salida.writeObject(hashParaApp);
                salida.flush();

                // ---------------------------------------------------------
                // FASE 4: EL CLIENTE VERIFICA AL BANCO
                // ---------------------------------------------------------
                
                // Esperamos el veredicto del cliente (A5 si le gustamos, A6 si no se fía)
                APPBancoPrimitive respuestaApp = (APPBancoPrimitive) entrada.readObject();
                
                if (respuestaApp == APPBancoPrimitive.A6) {
                    System.out.println("[Sirviente] El cliente rechazó nuestra autenticación (A6).");
                    return; // Cortamos la conexión
                } else if (respuestaApp == APPBancoPrimitive.A5) {
                    System.out.println("[Sirviente] ¡Autenticación mutua completada con éxito (A5)!");
                    
                    // ---------------------------------------------------------
                    // FASE 5: OPERACIÓN Y DESPEDIDA
                    // ---------------------------------------------------------
                    
                    // En esta primera entrega solo simulamos la operación esperando a que el cliente se despida
                    System.out.println("[Sirviente] Esperando operaciones del cliente...");
                    
                    // Esperamos la primitiva A7 (Final)
                    APPBancoPrimitive operacion = (APPBancoPrimitive) entrada.readObject();
                    
                    if (operacion == APPBancoPrimitive.A7) {
                        System.out.println("[Sirviente] Recibido A7 (Final). Enviando A8 (Final OK).");
                        
                        // Respondemos educadamente con A8 para cerrar el protocolo
                        salida.writeObject(APPBancoPrimitive.A8);
                        salida.flush();
                    }
                }
            } else {
                System.out.println("[Sirviente] Se esperaba A2, pero se recibió: " + prim);
            }

        } catch (Exception e) {
            System.err.println("[Sirviente] Error en la comunicación con el cliente: " + e.getMessage());
        } finally {
            // FASE 6: LIMPIEZA
            // Pase lo que pase (éxito o fallo), siempre hay que cerrar los recursos para no dejar puertos colgados.
            try {
                if (salida != null) salida.close();
                if (entrada != null) entrada.close();
                if (socketCliente != null) socketCliente.close();
                System.out.println("[Sirviente] Conexión cerrada con el cliente.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
