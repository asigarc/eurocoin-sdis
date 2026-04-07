package eurocoin.banco;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Banco {

    public static Set<String> usuariosActivos = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        int puerto = 2000;

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("[Banco] Servidor iniciado en el puerto " + puerto + ".");

            while (true) {
                System.out.println("----Server waiting For Client----");

                Socket socketCliente = serverSocket.accept();
                System.out.println("[Banco] ¡Nuevo cliente detectado! IP: " + socketCliente.getInetAddress());

                new BancoSirviente(socketCliente).start();
            }
        } catch (Exception e) {
            System.err.println("[Banco] Error crítico en el servidor: " + e.getMessage());
        }
    }
}