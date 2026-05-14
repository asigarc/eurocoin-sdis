package eurocoin.bancocentral;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class ServidorBancoCentral {

    public static void main(String[] args) {

        try {

            // Crear el registro RMI
            LocateRegistry.createRegistry(1099);

            System.out.println("[BancoCentral] Registry RMI creado.");

            // Crear el objeto remoto
            GrantServerObject objetoRemoto =new GrantServerObject();

            // Publicar el objeto remoto
            Naming.rebind("rmi://localhost/GrantServerObject", objetoRemoto);

            System.out.println(
                    "[BancoCentral] GrantServerObject publicado."
            );

            System.out.println(
                    "[BancoCentral] Servidor listo."
            );

        } catch (Exception e) {

            System.out.println(
                    "[BancoCentral] Error: " + e.getMessage()
            );

            e.printStackTrace();
        }
    }
}