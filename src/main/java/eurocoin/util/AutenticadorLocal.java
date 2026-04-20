package eurocoin.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class AutenticadorLocal {

    private final Digest hDigest;

    AutenticadorLocal(String algoritmo) {
        this.hDigest = new Digest(algoritmo);
    }

    static String getRegistro(String propiedad) throws IOException {
        InputStreamReader archivo =
            new InputStreamReader(new FileInputStream("Registro.json"));
        HashMap<String,String> registro =
            (new Gson()).fromJson(archivo,
                new TypeToken<HashMap<String, String>>() {}.getType());
        
        archivo.close();
        return (String)registro.get(propiedad);
    }

    public static boolean verificarContrasena(Scanner scanner) {
        int intentos = 3;

        while (intentos > 0) {
            try {
                String hashfunc = getRegistro("FuncionHash");
                AutenticadorLocal autenticador = new AutenticadorLocal(hashfunc);
                
                System.out.print("\n>>> Introduce tu contrasena local: ");

                String passwd_pt = scanner.nextLine(); 
                
                String salt = getRegistro("PasswordSalt");
                String hash_calc = autenticador.hDigest.generateHash(passwd_pt + salt);
                String hash_almac = getRegistro("PasswordHash");
                

                if (0 == hash_almac.compareTo(hash_calc)) {
                    System.out.println("Las claves coinciden\n"); 
                    return true;
                } else {
                    intentos--;
                    System.out.println("Las claves no coinciden. Intentos restantes: " + intentos);
                }

            } catch (java.io.IOException ioe) {

                System.out.println("Hemos tenido problemas para leer Registro.");
                return false; 
            }
        }
        
        return false; 
    }
}
