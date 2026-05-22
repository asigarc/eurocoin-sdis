package eurocoin.banco;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

        /*
         * Usamos rutas absolutas para evitar problemas con RMI y rutas relativas.
         */
        String archivoRegistro = new File(args[1]).getAbsolutePath();
        String archivoMovimientos = new File(args[2]).getAbsolutePath();
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

            File file = new File(archivoRegistro).getAbsoluteFile();

            Map<String, Map<String, String>> registroLocal = cargarRegistroLocal(file, gson);

            asegurarEntradaBanco(registroLocal, nombreBanco);

            BancoComercial bancoComercialRemoto = new BancoComercialImpl(
                    nombreBanco,
                    file.getAbsolutePath(),
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

    private static Map<String, Map<String, String>> cargarRegistroLocal(File file, Gson gson) throws Exception {
        Map<String, Map<String, String>> registroLocal = new HashMap<>();

        if (!file.exists()) {
            System.out.println("[Banco] Registro local no existe. Se creará uno nuevo: " + file.getAbsolutePath());
            return registroLocal;
        }

        String contenido = new String(
        Files.readAllBytes(file.toPath()),
        StandardCharsets.UTF_8
).trim();

if (!contenido.isEmpty() && contenido.charAt(0) == '\uFEFF') {
    contenido = contenido.substring(1).trim();
}

        System.out.println("[Banco] Leyendo registro local desde: " + file.getAbsolutePath());
        System.out.println("[Banco] Primer carácter del registro: "
                + (contenido.isEmpty() ? "<vacío>" : contenido.substring(0, 1)));

        if (contenido.isEmpty()) {
            return registroLocal;
        }

        JsonElement elemento = JsonParser.parseString(contenido);

        if (elemento.isJsonObject()) {
            registroLocal = gson.fromJson(
                    elemento,
                    new TypeToken<Map<String, Map<String, String>>>() {}.getType()
            );
        } else if (elemento.isJsonPrimitive() && elemento.getAsJsonPrimitive().isString()) {
            /*
             * Defensa extra:
             * Si por error el archivo se guardó como:
             * "{ \"BancoBob\": { ... } }"
             * lo desempaquetamos y lo volvemos a parsear.
             */
            String contenidoInterno = elemento.getAsString().trim();

            System.out.println("[Banco] Aviso: el registro venía envuelto como String. Reintentando parseo interno.");

            registroLocal = gson.fromJson(
                    contenidoInterno,
                    new TypeToken<Map<String, Map<String, String>>>() {}.getType()
            );
        } else {
            throw new IllegalStateException("Formato inesperado en " + file.getAbsolutePath());
        }

        return registroLocal != null ? registroLocal : new HashMap<>();
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