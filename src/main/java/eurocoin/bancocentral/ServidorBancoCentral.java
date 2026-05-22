package eurocoin.bancocentral;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

public class ServidorBancoCentral {

    public static void main(String[] args) {
        try {
            // AQUÍ ES DONDE SE CREAN LAS VARIABLES QUE TE DABAN ERROR:
            SslRMIClientSocketFactory clientFactory = new SslRMIClientSocketFactory();
            SslRMIServerSocketFactory serverFactory = new SslRMIServerSocketFactory();

            Registry registry = LocateRegistry.createRegistry(
                    1099,
                    clientFactory,
                    serverFactory
            );

            System.out.println("[BancoCentral] Registry RMI SSL creado en el puerto 1099.");

            // AHORA SÍ PUEDES USARLAS AQUÍ:
            GrantServerObject objetoRemoto = new GrantServerObject(
                    clientFactory,
                    serverFactory
            );

            registry.rebind("eurocoin.GrantServerObject", objetoRemoto);

            System.out.println("[BancoCentral] GrantServerObject publicado como 'eurocoin.GrantServerObject'.");
            System.out.println("[BancoCentral] Servidor central RMI SSL listo.");

        } catch (Exception e) {
            System.err.println("[BancoCentral] Error crítico: " + e.getMessage());
            e.printStackTrace();
        }
    }
}