package eurocoin.banco;

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
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import eurocoin.common.BancoComercial;
import eurocoin.common.Contacto;
import eurocoin.common.GrantServer;
import eurocoin.common.Parametros;

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

        SslRMIClientSocketFactory clientFactory = new SslRMIClientSocketFactory();
        SslRMIServerSocketFactory serverFactory = new SslRMIServerSocketFactory();

        GrantServer centralStub = conectarYRegistrarEnBancoCentral(
                archivoRegistro,
                nombreBanco,
                clientFactory,
                serverFactory
        );

        if (centralStub == null) {
            System.err.println("[Banco] No se puede iniciar el banco comercial sin BancoCentral.");
            System.err.println("[Banco] Arranca primero eurocoin.bancocentral.ServidorBancoCentral.");
            return;
        }

        iniciarConsolaBanco(nombreBanco, centralStub);

        ExecutorService pool = Executors.newFixedThreadPool(N_THREADS);

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("[Banco] Servidor Comercial [" + nombreBanco + "] iniciado en puerto " + puerto + ".");
            System.out.println("[Banco] Pool de hilos TCP: " + N_THREADS);
            System.out.println("[Banco] Comando RMI disponible: contactar <NombreBancoDestino>");

            while (true) {
                System.out.println("----Server waiting For Client----");

                Socket socketCliente = serverSocket.accept();

                System.out.println("[Banco] Nueva conexión TCP desde: " + socketCliente.getInetAddress());

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

    private static GrantServer conectarYRegistrarEnBancoCentral(
            String archivoRegistro,
            String nombreBanco,
            SslRMIClientSocketFactory clientFactory,
            SslRMIServerSocketFactory serverFactory) {

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

            asegurarEntradaBanco(registroLocal, nombreBanco);

            /*
             * MÓDULO 2:
             * El Banco Comercial crea su propio objeto remoto.
             * Ya no se registra en el BancoCentral pasando solo sus fondos,
             * sino pasando una referencia remota BancoComercial.
             */
            BancoComercial bancoComercialRemoto = new BancoComercialImpl(
                    nombreBanco,
                    archivoRegistro,
                    clientFactory,
                    serverFactory
            );

            Registry registry = LocateRegistry.getRegistry(
                    RMI_HOST,
                    RMI_PORT,
                    clientFactory
            );

            GrantServer centralStub = (GrantServer) registry.lookup(RMI_OBJECT_NAME);

            System.out.println("[Banco] Conexión RMI SSL establecida con BancoCentral.");

            /*
             * Esta es la línea corregida:
             * Antes tenías:
             *     centralStub.nuevoBanco(nombreBanco, fondosIniciales);
             *
             * Ahora, según el Módulo 2:
             *     nuevoBanco(String nombreBanco, BancoComercial bc)
             */
            Parametros parametros = centralStub.nuevoBanco(
                    nombreBanco,
                    bancoComercialRemoto
            );

            Map<String, String> datosBanco = registroLocal.get(nombreBanco);

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
            e.printStackTrace();
            return null;
        }
    }

    private static void asegurarEntradaBanco(
            Map<String, Map<String, String>> registroLocal,
            String nombreBanco) {

        Map<String, String> datosBanco = registroLocal.computeIfAbsent(
                nombreBanco,
                k -> new HashMap<>()
        );

        datosBanco.putIfAbsent("FondosEC", "50000");
        datosBanco.putIfAbsent("LWEurocoins", "10000");
        datosBanco.putIfAbsent("HWEurocoins", "100000");
    }

    private static void iniciarConsolaBanco(String nombreBanco, GrantServer centralStub) {
        Thread consola = new Thread(() -> {
            Scanner sc = new Scanner(System.in);

            while (true) {
                try {
                    System.out.print("[Banco-" + nombreBanco + "] comando> ");

                    String linea = sc.nextLine();

                    if (linea == null) {
                        continue;
                    }

                    linea = linea.trim();

                    if (linea.isEmpty()) {
                        continue;
                    }

                    if (linea.equalsIgnoreCase("ayuda")) {
                        imprimirAyuda(nombreBanco);

                    } else if (linea.startsWith("contactar ")) {
                        String destino = linea.substring("contactar ".length()).trim();

                        if (destino.isEmpty()) {
                            System.out.println("[Banco-" + nombreBanco + "] Uso: contactar <NombreBancoDestino>");
                            continue;
                        }

                        Contacto contacto = centralStub.dameContacto(nombreBanco, destino);

                        String respuesta = contacto.hello(
                                "Hola " + destino + ", soy " + nombreBanco
                        );

                        System.out.println("[Banco-" + nombreBanco
                                + "] Respuesta remota recibida: " + respuesta);

                    } else {
                        System.out.println("[Banco-" + nombreBanco
                                + "] Comando no reconocido. Escribe: ayuda");
                    }

                } catch (Exception e) {
                    System.out.println("[Banco-" + nombreBanco
                            + "] Error en consola RMI: " + e.getMessage());
                }
            }
        });

        consola.setDaemon(true);
        consola.start();
    }

    private static void imprimirAyuda(String nombreBanco) {
        System.out.println("[Banco-" + nombreBanco + "] Comandos disponibles:");
        System.out.println("  ayuda");
        System.out.println("  contactar <NombreBancoDestino>");
        System.out.println();
        System.out.println("Ejemplo:");
        System.out.println("  contactar BancoBob");
    }
}