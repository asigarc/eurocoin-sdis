package eurocoin.bancocentral;

import eurocoin.common.GrantServer;
import eurocoin.common.Parametros;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class GrantServerObject extends UnicastRemoteObject implements GrantServer {

    private static final long serialVersionUID = 1L;

    private static final String ARCHIVO = "data/bancoCentral/RegistroBancoCentral.json";

    private static final int DEFAULT_LW = 10000;
    private static final int DEFAULT_HW = 100000;

    private final Gson gson = new Gson();
    private final Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();

    public GrantServerObject() throws RemoteException {
        super();
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
            System.err.println("[BancoCentral] No se pudo leer el registro central. Se usará uno vacío.");
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
            System.err.println("[BancoCentral] Error al guardar RegistroBancoCentral.json: " + e.getMessage());
        }
    }

    @Override
    public synchronized Parametros nuevoBanco(String nombreBanco, Integer activos)
            throws RemoteException {

        Map<String, Map<String, String>> registro = cargarRegistro();

        Map<String, String> datosBanco = registro.computeIfAbsent(
                nombreBanco,
                k -> new HashMap<>()
        );

        int lw = obtenerEntero(datosBanco.get("LWEurocoins"), DEFAULT_LW);
        int hw = obtenerEntero(datosBanco.get("HWEurocoins"), DEFAULT_HW);

        datosBanco.put("FondosEC", String.valueOf(activos));
        datosBanco.put("LWEurocoins", String.valueOf(lw));
        datosBanco.put("HWEurocoins", String.valueOf(hw));

        guardarRegistro(registro);

        System.out.println("[BancoCentral] Banco registrado/actualizado: " + nombreBanco);
        System.out.println("[BancoCentral] FondosEC=" + activos + ", LW=" + lw + ", HW=" + hw);

        return new Parametros(lw, hw, activos);
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

        /*
         * Política sencilla y estable para el primer módulo:
         * si el banco baja de LW, el BancoCentral lo lleva a un punto cómodo
         * dentro de la banda [LW, HW].
         */
        int nuevoFondo = puntoMedio(lw, hw);

        datosBanco.put("FondosEC", String.valueOf(nuevoFondo));
        datosBanco.put("LWEurocoins", String.valueOf(lw));
        datosBanco.put("HWEurocoins", String.valueOf(hw));

        guardarRegistro(registro);

        System.out.println("[BancoCentral] Solicitud de AUMENTO recibida de " + nombreBanco);
        System.out.println("[BancoCentral] Fondo previsto=" + fondoECPrevisto
                + " -> nuevo FondosEC=" + nuevoFondo);

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

        /*
         * Política sencilla y estable para el primer módulo:
         * si el banco supera HW, el BancoCentral drena hasta un punto cómodo
         * dentro de la banda [LW, HW].
         */
        int nuevoFondo = puntoMedio(lw, hw);

        datosBanco.put("FondosEC", String.valueOf(nuevoFondo));
        datosBanco.put("LWEurocoins", String.valueOf(lw));
        datosBanco.put("HWEurocoins", String.valueOf(hw));

        guardarRegistro(registro);

        System.out.println("[BancoCentral] Solicitud de DECREMENTO recibida de " + nombreBanco);
        System.out.println("[BancoCentral] Fondo previsto=" + fondoECPrevisto
                + " -> nuevo FondosEC=" + nuevoFondo);

        return new Parametros(lw, hw, nuevoFondo);
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