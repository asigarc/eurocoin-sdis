package eurocoin.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class AutenticadorLocal {

    private final Digest hDigest;

    AutenticadorLocal(String algoritmo) {
        this.hDigest = new Digest(algoritmo);
    }

    private static HashMap<String, String> cargarRegistroCompleto() throws IOException {
        File file = new File("Registro.json").getAbsoluteFile();

        if (!file.exists()) {
            throw new IOException("No existe Registro.json en: " + file.getAbsolutePath());
        }

        String contenido = new String(
                Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8
        ).trim();

        /*
         * PowerShell puede guardar UTF-8 con BOM.
         * Si aparece, lo quitamos antes de parsear.
         */
        if (!contenido.isEmpty() && contenido.charAt(0) == '\uFEFF') {
            contenido = contenido.substring(1).trim();
        }

        if (contenido.isEmpty()) {
            return new HashMap<>();
        }

        Gson gson = new Gson();
        JsonElement elemento = JsonParser.parseString(contenido);

        Map<String, String> registro;

        if (elemento.isJsonObject()) {
            registro = gson.fromJson(
                    elemento,
                    new TypeToken<HashMap<String, String>>() {}.getType()
            );
        } else if (elemento.isJsonPrimitive() && elemento.getAsJsonPrimitive().isString()) {
            /*
             * Defensa extra:
             * Si Registro.json estuviera guardado como:
             * "{ \"MasterKey\": \"...\" }"
             * lo desempaquetamos y parseamos el contenido interno.
             */
            String contenidoInterno = elemento.getAsString().trim();

            if (!contenidoInterno.isEmpty() && contenidoInterno.charAt(0) == '\uFEFF') {
                contenidoInterno = contenidoInterno.substring(1).trim();
            }

            registro = gson.fromJson(
                    contenidoInterno,
                    new TypeToken<HashMap<String, String>>() {}.getType()
            );
        } else {
            throw new IOException("Formato inesperado en Registro.json");
        }

        if (registro == null) {
            return new HashMap<>();
        }

        return new HashMap<>(registro);
    }

    static String getRegistro(String propiedad) throws IOException {
        HashMap<String, String> registro = cargarRegistroCompleto();
        return registro.get(propiedad);
    }

    public static boolean verificarContrasena(Scanner scanner) {
        int intentos = 3;

        while (intentos > 0) {
            try {
                String hashfunc = getRegistro("FuncionHash");
                String salt = getRegistro("PasswordSalt");
                String hash_almac = getRegistro("PasswordHash");

                if (hashfunc == null || salt == null || hash_almac == null) {
                    System.out.println("Registro.json incompleto. Faltan FuncionHash, PasswordSalt o PasswordHash.");
                    return false;
                }

                AutenticadorLocal autenticador = new AutenticadorLocal(hashfunc);

                System.out.print("\n>>> Introduce tu contrasena local: ");

                String passwd_pt = scanner.nextLine();

                String hash_calc = autenticador.hDigest.generateHash(passwd_pt + salt);

                if (hash_almac.compareTo(hash_calc) == 0) {
                    System.out.println("Las claves coinciden\n");
                    return true;
                } else {
                    intentos--;
                    System.out.println("Las claves no coinciden. Intentos restantes: " + intentos);
                }

            } catch (IOException ioe) {
                System.out.println("Hemos tenido problemas para leer Registro.");
                System.out.println("[AutenticadorLocal] " + ioe.getMessage());
                return false;

            } catch (Exception e) {
                System.out.println("Error durante la autenticación local.");
                System.out.println("[AutenticadorLocal] " + e.getMessage());
                return false;
            }
        }

        return false;
    }
}