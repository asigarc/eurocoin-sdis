package snippets;

// trabajo con Json en Gson
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import eurocoin.util.Digest;

public class PruebaSeguridad {
   private final Digest hDigest;
   private static final String NombreArchivoRegistro = "Registro.json";

   PruebaSeguridad(String algoritmo) {
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

   public static void main(String[] var0) {
      try {
         String hashfunc = getRegistro("FuncionHash");
         PruebaSeguridad yomismo = new PruebaSeguridad(hashfunc);
         System.out.println("Algoritmo: " + hashfunc);
         String passwd_pt = "HolaSoyPedroLuis";
         System.out.println("ClaveUsuario: " + passwd_pt);
         String salt = getRegistro("PasswordSalt");
         System.out.println("PasswordSalt: " + salt);
         String hash_calc = yomismo.hDigest.generateHash(passwd_pt + salt);
         System.out.println("PasswordHash-calculado: " + hash_calc);
         String hash_almac = getRegistro("PasswordHash");
         System.out.println("PasswordHash: " + hash_almac);
         
         if (0 == hash_almac.compareTo(hash_calc)) {
            System.out.println("Las claves coinciden");
         } else {
            System.out.println("Las claves no coinciden");
         }

      } catch (java.io.IOException ioe) {
         System.out.println("Hemos tenido problemas para leer Registro.");
      }
   }
}
