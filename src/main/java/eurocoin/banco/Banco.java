package eurocoin.banco;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Banco {

    // Conjunto para gestionar sesiones (P1_primerModulo.pdf)
    public static Set<String> usuariosActivos = ConcurrentHashMap.newKeySet();
    
    // Configuración del Pool de Hilos (ml2.2_Notas_2024_v3.pdf pág 2)
    private static final int N_THREADS = 10;

    public static void main(String[] args) {
        int puerto = 2000;
        
        // Crear el Pool de hilos
        ExecutorService pool = Executors.newFixedThreadPool(N_THREADS);

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("[Banco] Servidor EuroCoin iniciado en el puerto " + puerto + ".");
            System.out.println("[Banco] Capacidad del Pool: " + N_THREADS + " hilos.");

            while (true) {
                // Mensaje de log obligatorio según P1_primerModulo.pdf pág. 10
                System.out.println("----Server waiting For Client----");

                Socket socketCliente = serverSocket.accept();
                System.out.println("[Banco] Nueva conexión detectada desde: " + socketCliente.getInetAddress());

                // Lanzar el sirviente usando el Pool en lugar de 'new Thread().start()'
                pool.execute(new BancoSirviente(socketCliente));
            }
        } catch (Exception e) {
            System.err.println("[Banco] Error crítico en el servidor: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }
}