package eurocoin.banco;

import eurocoin.common.GrantServer;
import eurocoin.common.Parametros;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class Banco {

    public static Set<String> usuariosActivos = ConcurrentHashMap.newKeySet();
    private static final int N_THREADS = 10;

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Uso: java Banco <puerto> <ruta_Registro.json> <ruta_Movimientos.txt> <nombreBanco>");
            return;
        }

        int puerto = Integer.parseInt(args[0]);
        String archivoRegistro = args[1];
        String archivoMovimientos = args[2];
        String nombreBanco = args[3];
        
        ExecutorService pool = Executors.newFixedThreadPool(N_THREADS);
        GrantServer centralStub = null;

        // Fase de Registro e Sincronización Remota Inicial RMI
        try {
            // Cargar fondos iniciales desde el JSON local si ya existe
            int fondosIniciales = 50000; 
            Gson gson = new Gson();
            Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();
            Map<String, Map<String, String>> registroLocal = new HashMap<>();
            File file = new File(archivoRegistro);
            
            if (file.exists()) {
                try (Reader reader = new FileReader(file)) {
                    registroLocal = gson.fromJson(reader, new TypeToken<Map<String, Map<String, String>>>() {}.getType());
                    if (registroLocal != null && registroLocal.containsKey(nombreBanco)) {
                        String fStr = registroLocal.get(nombreBanco).get("FondosEC");
                        if (fStr != null) {
                            fondosIniciales = (int) Double.parseDouble(fStr);
                        }
                    }
                }
            }

            // Localizar registro RMI central y sincronizar
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            centralStub = (GrantServer) registry.lookup("eurocoin.GrantServerObject");
            System.out.println("[Banco] Conexión RMI establecida con el Banco Central.");
            
            Parametros p = centralStub.nuevoBanco(nombreBanco, fondosIniciales);
            
            // Actualizar/Escribir la configuración de banda oficial en el archivo local
            if (registroLocal == null) {
                registroLocal = new HashMap<>();
            }
            Map<String, String> datosBanco = registroLocal.computeIfAbsent(nombreBanco, k -> new HashMap<>());
            datosBanco.put("FondosEC", String.valueOf(p.fondosEC));
            datosBanco.put("LWEurocoins", String.valueOf(p.lowWatermark));
            datosBanco.put("HWEurocoins", String.valueOf(p.highWatermark));
            
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (Writer writer = new FileWriter(file)) {
                gsonPretty.toJson(registroLocal, writer);
            }
            System.out.println("[Banco] Sincronización completa con Central. FondosEC=" + p.fondosEC + ", LW=" + p.lowWatermark + ", HW=" + p.highWatermark);

        } catch (Exception e) {
            System.err.println("[Banco] Advertencia: Modo aislado. No se pudo conectar al Banco Central RMI: " + e.getMessage());
        }

        // Bucle de escucha TCP del servidor comercial
        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("[Banco] Servidor Comercial [" + nombreBanco + "] listo en puerto " + puerto + ".");

            while (true) {
                System.out.println("----Server waiting For Client----");
                Socket socketCliente = serverSocket.accept();
                System.out.println("[Banco] Nueva conexión entrante desde: " + socketCliente.getInetAddress());

                // Inyectamos todas las dependencias aisladas al sirviente TCP
                pool.execute(new BancoSirviente(
                    socketCliente, 
                    archivoRegistro, 
                    archivoMovimientos, 
                    nombreBanco, 
                    centralStub
                ));
            }
        } catch (Exception e) {
            System.err.println("[Banco] Error crítico en socket de escucha: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }
}