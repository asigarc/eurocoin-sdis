package eurocoin.banco;

import eurocoin.common.BancoComercial;
import eurocoin.common.Contacto;

import java.io.FileReader;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;

import com.google.gson.Gson;
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
        this.archivoRegistro = archivoRegistro;
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
        try (FileReader reader = new FileReader(archivoRegistro)) {
            Map<String, Map<String, String>> registro = gson.fromJson(
                    reader,
                    new TypeToken<Map<String, Map<String, String>>>() {}.getType()
            );

            if (registro == null) {
                throw new IllegalStateException("RegistroBanco.json vacío o mal formado.");
            }

            return registro;
        }
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