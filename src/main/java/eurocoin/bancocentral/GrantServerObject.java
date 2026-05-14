package eurocoin.bancocentral;
import eurocoin.common.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class GrantServerObject extends UnicastRemoteObject implements GrantServer { // hay que hacer algo con nombreBanco??
    private static final String ARCHIVO = "data/bancoCentral/RegistroBancoCentral.json";

        private final Gson gson = new Gson();

        private final Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();

    public GrantServerObject() throws RemoteException {
        super();
    }

    @Override
    public Parametros nuevoBanco(String nombreBanco, Integer activos) throws RemoteException {
        
        try {
            Map<String, Map<String, String>> registro;
            try (FileReader reader = new FileReader(ARCHIVO)) {
                registro = gson.fromJson(
                        reader,
                        new TypeToken<Map<String, Map<String, String>>>() {}.getType()
                );
            }

            if (registro == null) {
                registro = new HashMap<>();
            }

            Map<String, String> datosBanco =
                    new HashMap<>();

            datosBanco.put(
                    "FondosEC",
                    activos.toString()
            );

            datosBanco.put(
                    "LWEurocoins",
                    "10000"
            );

            datosBanco.put(
                    "HWEurocoins",
                    "100000"
            );

            registro.put(nombreBanco, datosBanco);

            try (FileWriter writer =
                        new FileWriter(ARCHIVO)) {

                gsonPretty.toJson(registro, writer);
            }

        } catch (Exception e) {

            System.out.println(
                    "[BancoCentral] Error guardando banco."
            );
        }

        return new Parametros(
            10000,
            100000,
            activos
        );
    }

    @Override
    public Parametros solicitaAumento(String nombreBanco, Integer fondoECPrevisto) throws RemoteException {
        return new Parametros(
            10000,
            100000,
            fondoECPrevisto + 50000
        );
    }

    @Override
    public Parametros solicitaDecremento(String nombreBanco, Integer fondoECPrevisto) throws RemoteException {
        return new Parametros(
            10000,
            100000,
            fondoECPrevisto - 50000
        );
    }
}