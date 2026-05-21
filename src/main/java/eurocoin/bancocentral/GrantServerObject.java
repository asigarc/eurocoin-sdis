package eurocoin.bancocentral;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import eurocoin.common.BancoComercial;
import eurocoin.common.Contacto;
import eurocoin.common.GrantServer;
import eurocoin.common.Parametros;

public class GrantServerObject extends UnicastRemoteObject implements GrantServer {

    private static final long serialVersionUID = 1L;

    private static final String ARCHIVO = "data/bancoCentral/RegistroBancoCentral.json";

    private static final int DEFAULT_LW = 10000;
    private static final int DEFAULT_HW = 100000;

    private final Map<String, BancoComercial> bancosRemotos;

    private final Gson gson = new Gson();
    private final Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();

    public GrantServerObject(
            RMIClientSocketFactory clientFactory,
            RMIServerSocketFactory serverFactory)
            throws RemoteException {

        super(0, clientFactory, serverFactory);

        this.bancosRemotos = new ConcurrentHashMap<>();
    }

    private synchronized Map<String, Map<String, String>> cargarRegistro() {
        File file = new File(ARCHIVO);

        if (!file.exists()) {
            return new HashMap<>();
        }

        try (FileReader reader = new FileReader(file)) {
            Map<String, Map<String, String>> registro = gson.fromJson(
                    reader,
                    new TypeToken<Map<String, Map<String, String>>>() {}.getType()
            );

            return registro != null ? registro : new HashMap<>();

        } catch (Exception e) {
            System.err.println("[BancoCentral] No se pudo leer RegistroBancoCentral.json. Se usará registro vacío.");
            return new HashMap<>();
        }
    }

    private synchronized void guardarRegistro(Map<String, Map<String, String>> registro) {
        try {
            File file = new File(ARCHIVO);

            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }

            try (FileWriter writer = new FileWriter(file)) {
                gsonPretty.toJson(registro, writer);
            }

        } catch (Exception e) {
            System.err.println("[BancoCentral] Error guardando RegistroBancoCentral.json: " + e.getMessage());
        }
    }

    @Override
    public synchronized Parametros nuevoBanco(String nombreBanco, BancoComercial bc)
            throws RemoteException {

        bancosRemotos.put(nombreBanco, bc);

        int fondosActuales = bc.dameFondosEC();

        Map<String, Map<String, String>> registro = cargarRegistro();

        Map<String, String> datosBanco = registro.computeIfAbsent(
                nombreBanco,
                k -> new HashMap<>()
        );

        int lw = obtenerEntero(datosBanco.get("LWEurocoins"), DEFAULT_LW);
        int hw = obtenerEntero(datosBanco.get("HWEurocoins"), DEFAULT_HW);

        datosBanco.put("FondosEC", String.valueOf(fondosActuales));
        datosBanco.put("LWEurocoins", String.valueOf(lw));
        datosBanco.put("HWEurocoins", String.valueOf(hw));

        guardarRegistro(registro);

        System.out.println("[BancoCentral] Banco comercial registrado: " + nombreBanco);
        System.out.println("[BancoCentral] Referencia remota BancoComercial almacenada.");
        System.out.println("[BancoCentral] Sondeo inicial dameFondosEC()=" + fondosActuales);

        return new Parametros(lw, hw, fondosActuales);
    }

    @Override
    public synchronized Parametros solicitaAumento(String nombreBanco, Integer fondoECPrevisto)
            throws RemoteException {

        Map<String, Map<String, String>> registro = cargarRegistro();

        Map<String, String> datosBanco = registro.computeIfAbsent(
                nombreBanco,
                k -> new HashMap<>()
        );

        int lw = obtenerEntero(datosBanco.get("LWEurocoins"), DEFAULT_LW);
        int hw = obtenerEntero(datosBanco.get("HWEurocoins"), DEFAULT_HW);

        int nuevoFondo = puntoMedio(lw, hw);

        datosBanco.put("FondosEC", String.valueOf(nuevoFondo));
        datosBanco.put("LWEurocoins", String.valueOf(lw));
        datosBanco.put("HWEurocoins", String.valueOf(hw));

        guardarRegistro(registro);

        System.out.println("[BancoCentral] solicitaAumento(" + nombreBanco + ", "
                + fondoECPrevisto + ") -> " + nuevoFondo);

        return new Parametros(lw, hw, nuevoFondo);
    }

    @Override
    public synchronized Parametros solicitaDecremento(String nombreBanco, Integer fondoECPrevisto)
            throws RemoteException {

        Map<String, Map<String, String>> registro = cargarRegistro();

        Map<String, String> datosBanco = registro.computeIfAbsent(
                nombreBanco,
                k -> new HashMap<>()
        );

        int lw = obtenerEntero(datosBanco.get("LWEurocoins"), DEFAULT_LW);
        int hw = obtenerEntero(datosBanco.get("HWEurocoins"), DEFAULT_HW);

        int nuevoFondo = puntoMedio(lw, hw);

        datosBanco.put("FondosEC", String.valueOf(nuevoFondo));
        datosBanco.put("LWEurocoins", String.valueOf(lw));
        datosBanco.put("HWEurocoins", String.valueOf(hw));

        guardarRegistro(registro);

        System.out.println("[BancoCentral] solicitaDecremento(" + nombreBanco + ", "
                + fondoECPrevisto + ") -> " + nuevoFondo);

        return new Parametros(lw, hw, nuevoFondo);
    }

    @Override
    public synchronized Contacto dameContacto(String nombreBanco, String nombreBancoDestino) throws RemoteException {

        System.out.println("[BancoCentral] " + nombreBanco
                + " solicita contacto con " + nombreBancoDestino);

        BancoComercial bancoDestino = bancosRemotos.get(nombreBancoDestino);

        if (bancoDestino == null) {
            throw new RemoteException("Banco destino no registrado en BancoCentral: " + nombreBancoDestino);
        }

        Contacto contacto = bancoDestino.dameContacto(nombreBanco);

        System.out.println("[BancoCentral] Contacto remoto obtenido de "
                + nombreBancoDestino + " y devuelto a " + nombreBanco);

        return contacto;
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

    private int puntoMedio(int lw, int hw) {
        return lw + ((hw - lw) / 2);
    }
}