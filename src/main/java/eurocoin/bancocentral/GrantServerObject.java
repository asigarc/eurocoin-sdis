package eurocoin.bancocentral;

import eurocoin.common.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class GrantServerObject extends UnicastRemoteObject implements GrantServer {
    private static final long serialVersionUID = 1L;
    private static final String ARCHIVO = "data/bancoCentral/RegistroBancoCentral.json";
    
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
            Map<String, Map<String, String>> reg = gson.fromJson(reader, 
                new TypeToken<Map<String, Map<String, String>>>() {}.getType());
            return reg != null ? reg : new HashMap<>();
        } catch (Exception e) {
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
            System.err.println("[BancoCentral] Error al guardar persistencia JSON: " + e.getMessage());
        }
    }

    public synchronized Parametros nuevoBanco(String nombreBanco, Integer activos) throws RemoteException {
        Map<String, Map<String, String>> registro = cargarRegistro();
        
        int lw = 10000;
        int hw = 100000;

        Map<String, String> datosBanco = registro.computeIfAbsent(nombreBanco, k -> new HashMap<>());
        datosBanco.put("FondosEC", activos.toString());
        datosBanco.put("LWEurocoins", String.valueOf(lw));
        datosBanco.put("HWEurocoins", String.valueOf(hw));

        guardarRegistro(registro);
        System.out.println("[BancoCentral] Banco '" + nombreBanco + "' registrado con " + activos + " FondosEC.");
        
        return new Parametros(lw, hw, activos);
    }

    public synchronized Parametros solicitaAumento(String nombreBanco, Integer fondoECPrevisto) throws RemoteException {
        Map<String, Map<String, String>> registro = cargarRegistro();
        Map<String, String> datosBanco = registro.get(nombreBanco);
        
        int lw = (datosBanco != null) ? Integer.parseInt(datosBanco.get("LWEurocoins")) : 10000;
        int hw = (datosBanco != null) ? Integer.parseInt(datosBanco.get("HWEurocoins")) : 100000;
        
        // Inyección de liquidez estándar (+50,000 unidades)
        int nuevoFondo = fondoECPrevisto + 50000;
        
        if (datosBanco == null) {
            datosBanco = new HashMap<>();
            registro.put(nombreBanco, datosBanco);
        }
        datosBanco.put("FondosEC", String.valueOf(nuevoFondo));
        datosBanco.put("LWEurocoins", String.valueOf(lw));
        datosBanco.put("HWEurocoins", String.valueOf(hw));
        
        guardarRegistro(registro);
        System.out.println("[BancoCentral] INYECCIÓN de liquidez a '" + nombreBanco + "'. Nuevos fondos: " + nuevoFondo);
        
        return new Parametros(lw, hw, nuevoFondo);
    }

    public synchronized Parametros solicitaDecremento(String nombreBanco, Integer fondoECPrevisto) throws RemoteException {
        Map<String, Map<String, String>> registro = cargarRegistro();
        Map<String, String> datosBanco = registro.get(nombreBanco);
        
        int lw = (datosBanco != null) ? Integer.parseInt(datosBanco.get("LWEurocoins")) : 10000;
        int hw = (datosBanco != null) ? Integer.parseInt(datosBanco.get("HWEurocoins")) : 100000;
        
        // Drenaje de liquidez excesiva (-50,000 unidades o equilibrado)
        int nuevoFondo = Math.max(lw + 5000, fondoECPrevisto - 50000);
        
        if (datosBanco == null) {
            datosBanco = new HashMap<>();
            registro.put(nombreBanco, datosBanco);
        }
        datosBanco.put("FondosEC", String.valueOf(nuevoFondo));
        datosBanco.put("LWEurocoins", String.valueOf(lw));
        datosBanco.put("HWEurocoins", String.valueOf(hw));
        
        guardarRegistro(registro);
        System.out.println("[BancoCentral] DRENAJE de fondos excedentes en '" + nombreBanco + "'. Nuevos fondos: " + nuevoFondo);
        
        return new Parametros(lw, hw, nuevoFondo);
    }
}