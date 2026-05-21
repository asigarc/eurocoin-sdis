package eurocoin.bancocentral;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServidorBancoCentral {

    public static void main(String[] args) {
        try {
            // Crear el registro RMI interno en el puerto estándar 1099
            Registry registry = LocateRegistry.createRegistry(1099);
            System.out.println("[BancoCentral] Registry RMI creado en el puerto 1099.");

            // Crear el objeto remoto sirviente
            GrantServerObject objetoRemoto = new GrantServerObject();

            // Publicar bajo el identificador público obligatorio especificado
            registry.rebind("eurocoin.GrantServerObject", objetoRemoto);

            System.out.println("[BancoCentral] GrantServerObject publicado exitosamente como 'eurocoin.GrantServerObject'.");
            System.out.println("[BancoCentral] Servidor Central listo y esperando peticiones...");

        } catch (Exception e) {
            System.err.println("[BancoCentral] Error crítico: " + e.getMessage());
            e.printStackTrace();
        }
    }
}