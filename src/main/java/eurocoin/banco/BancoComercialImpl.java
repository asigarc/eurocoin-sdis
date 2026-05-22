package eurocoin.banco;

import eurocoin.common.BancoComercial;
import eurocoin.common.Contacto;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class BancoComercialImpl extends UnicastRemoteObject implements BancoComercial {

    private static final long serialVersionUID = 1L;

    private final String nombreBanco;
    private final String archivoRegistro;

    private final RMIClientSocketFactory clientFactory;
    private final RMIServerSocketFactory serverFactory;

    private final Gson gson = new Gson();

    public BancoComercialImpl(
            String nombreBanco,
            String archivoRegistro,
            RMIClientSocketFactory clientFactory,
            RMIServerSocketFactory serverFactory)
            throws RemoteException {

        super(0, clientFactory, serverFactory);

        this.nombreBanco = nombreBanco;
        this.archivoRegistro = new File(archivoRegistro).getAbsolutePath();
        this.clientFactory = clientFactory;
        this.serverFactory = serverFactory;
    }

    @Override
    public Integer dameFondosEC() throws RemoteException {
        try {
            Map<String, Map<String, String>> registro = cargarRegistroBanco();

            Map<String, String> datosBanco = registro.get(nombreBanco);

            if (datosBanco == null) {
                System.out.println("[BancoComercial-" + nombreBanco
                        + "] dameFondosEC(): no existe entrada del banco en el registro.");
                return 0;
            }

            String fondosStr = datosBanco.get("FondosEC");
            int fondos = obtenerEntero(fondosStr, 0);

            System.out.println("[BancoComercial-" + nombreBanco
                    + "] BancoCentral consulta dameFondosEC() -> " + fondos);

            return fondos;

        } catch (Exception e) {
            System.out.println("[BancoComercial-" + nombreBanco
                    + "] Error en dameFondosEC(): " + e.getMessage());
            return 0;
        }
    }

    @Override
    public Contacto dameContacto(String nombreBancoOrigen) throws RemoteException {
        System.out.println("[BancoComercial-" + nombreBanco
                + "] BancoCentral solicita Contacto para origen: " + nombreBancoOrigen);

        return new ContactoImpl(
                this.nombreBanco,
                nombreBancoOrigen,
                clientFactory,
                serverFactory
        );
    }

    private Map<String, Map<String, String>> cargarRegistroBanco() throws Exception {
        File file = new File(archivoRegistro).getAbsoluteFile();

        if (!file.exists()) {
            throw new IllegalStateException("No existe el registro: " + file.getAbsolutePath());
        }

        String contenido = new String(
                Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8
        ).trim();

        /*
         * PowerShell puede guardar UTF-8 con BOM. Si aparece, Gson puede verlo
         * como un carácter extraño al principio. Lo quitamos antes de parsear.
         */
        if (!contenido.isEmpty() && contenido.charAt(0) == '\uFEFF') {
            contenido = contenido.substring(1).trim();
        }

        if (contenido.isEmpty()) {
            return new HashMap<>();
        }

        JsonElement elemento = JsonParser.parseString(contenido);

        Map<String, Map<String, String>> registro;

        if (elemento.isJsonObject()) {
            registro = gson.fromJson(
                    elemento,
                    new TypeToken<Map<String, Map<String, String>>>() {}.getType()
            );
        } else if (elemento.isJsonPrimitive() && elemento.getAsJsonPrimitive().isString()) {
            /*
             * Defensa extra:
             * Si el archivo estuviera guardado como:
             * "{ \"BancoBob\": { ... } }"
             * lo desempaquetamos y parseamos el contenido interno.
             */
            String contenidoInterno = elemento.getAsString().trim();

            if (!contenidoInterno.isEmpty() && contenidoInterno.charAt(0) == '\uFEFF') {
                contenidoInterno = contenidoInterno.substring(1).trim();
            }

            registro = gson.fromJson(
                    contenidoInterno,
                    new TypeToken<Map<String, Map<String, String>>>() {}.getType()
            );
        } else {
            throw new IllegalStateException("Formato inesperado en " + file.getAbsolutePath());
        }

        return registro != null ? registro : new HashMap<>();
    }

    private int obtenerEntero(String valor, int defecto) {
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