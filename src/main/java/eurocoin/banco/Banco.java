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
    private static final String RMI_HOST = "localhost";
    private static final int RMI_PORT = 1099;
    private static final String RMI_OBJECT_NAME = "eurocoin.GrantServerObject";

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Uso:");
            System.out.println("java eurocoin.banco.Banco <puerto> <ruta_RegistroBanco.json> <ruta_MovimientosBanco.txt> <nombreBanco>");
            System.out.println();
            System.out.println("Ejemplo:");
            System.out.println("java eurocoin.banco.Banco 2000 data/bancoBob/RegistroBanco.json data/bancoBob/MovimientosBanco.txt BancoBob");
            return;
        }

        int puerto;
        try {
            puerto = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("[Banco] El puerto debe ser numérico.");
            return;
        }

        String archivoRegistro = args[1];
        String archivoMovimientos = args[2];
        String nombreBanco = args[3];

        GrantServer centralStub = conectarYRegistrarEnBancoCentral(
                archivoRegistro,
                nombreBanco
        );

        /*
         * Para el primer módulo de P2, el BancoCentral es obligatorio.
         * Si no existe, no seguimos en modo aislado, porque entonces no se probaría
         * la funcionalidad principal del módulo.
         */
        if (centralStub == null) {
            System.err.println("[Banco] No se puede iniciar el banco comercial sin BancoCentral.");
            System.err.println("[Banco] Arranca primero eurocoin.bancocentral.ServidorBancoCentral.");
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(N_THREADS);

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("[Banco] Servidor Comercial [" + nombreBanco + "] iniciado en puerto " + puerto + ".");
            System.out.println("[Banco] Pool de hilos: " + N_THREADS);

            while (true) {
                System.out.println("----Server waiting For Client----");

                Socket socketCliente = serverSocket.accept();

                System.out.println("[Banco] Nueva conexión desde: " + socketCliente.getInetAddress());

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

    private static GrantServer conectarYRegistrarEnBancoCentral(String archivoRegistro, String nombreBanco) {
        try {
            Gson gson = new Gson();
            Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();

            File file = new File(archivoRegistro);

            Map<String, Map<String, String>> registroLocal = new HashMap<>();

            if (file.exists()) {
                try (Reader reader = new FileReader(file)) {
                    registroLocal = gson.fromJson(
                            reader,
                            new TypeToken<Map<String, Map<String, String>>>() {}.getType()
                    );

                    if (registroLocal == null) {
                        registroLocal = new HashMap<>();
                    }
                }
            }

            int fondosIniciales = 50000;

            if (registroLocal.containsKey(nombreBanco)) {
                String fondosStr = registroLocal.get(nombreBanco).get("FondosEC");
                fondosIniciales = obtenerEntero(fondosStr, 50000);
            }

            Registry registry = LocateRegistry.getRegistry(RMI_HOST, RMI_PORT);
            GrantServer centralStub = (GrantServer) registry.lookup(RMI_OBJECT_NAME);

            System.out.println("[Banco] Conexión RMI establecida con BancoCentral.");

            Parametros parametros = centralStub.nuevoBanco(nombreBanco, fondosIniciales);

            Map<String, String> datosBanco = registroLocal.computeIfAbsent(
                    nombreBanco,
                    k -> new HashMap<>()
            );

            datosBanco.put("FondosEC", String.valueOf(parametros.fondosEC));
            datosBanco.put("LWEurocoins", String.valueOf(parametros.lowWatermark));
            datosBanco.put("HWEurocoins", String.valueOf(parametros.highWatermark));

            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }

            try (Writer writer = new FileWriter(file)) {
                gsonPretty.toJson(registroLocal, writer);
            }

            System.out.println("[Banco] Banco registrado en BancoCentral: " + nombreBanco);
            System.out.println("[Banco] Parámetros recibidos: " + parametros);

            return centralStub;

        } catch (Exception e) {
            System.err.println("[Banco] Error conectando con BancoCentral: " + e.getMessage());
            return null;
        }
    }

    private static int obtenerEntero(String valor, int defecto) {
        if (valor == null || valor.trim().isEmpty()) {
            return defecto;
        }

        try {
            return (int) Math.round(Double.parseDouble(valor));
        } catch (NumberFormatException e) {
            return defecto;
        }
    }
}