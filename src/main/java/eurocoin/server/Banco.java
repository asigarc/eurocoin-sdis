package eurocoin.server;

import java.net.ServerSocket;
import java.net.Socket;

public class Banco {

    public static void main(String[] args) {
        // El puerto donde el servidor va a escuchar (comprueba si el profesor os pide uno específico)
        int puerto = 2000; 

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("[Banco] Servidor iniciado en el puerto " + puerto + ".");
            System.out.println("[Banco] Esperando conexiones de clientes APP...");

            // Bucle infinito: el banco siempre está abierto esperando clientes
            while (true) {
                // Se queda bloqueado aquí hasta que un ClienteBanco se conecta
                Socket socketCliente = serverSocket.accept();
                
                System.out.println("[Banco] ¡Nuevo cliente detectado! IP: " + socketCliente.getInetAddress());

                // Creamos un nuevo Sirviente, le pasamos el socket del cliente y lo ponemos a trabajar (start)
                Sirviente sirviente = new Sirviente(socketCliente);
                sirviente.start();
            }

        } catch (Exception e) {
            System.err.println("[Banco] Error crítico en el servidor: " + e.getMessage());
        }
    }
}
