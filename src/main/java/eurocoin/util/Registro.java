package eurocoin.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class Registro {

    private final String nombreArchivo;
    private final Gson gson;

    public Registro(String nArch) throws IOException {
        this.nombreArchivo = nArch;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        File f = new File(nombreArchivo);
        if (!f.exists()) {
            throw new IOException("No existe el archivo: " + nArch);
        }
    }

    public String getValor(String propiedad) throws IOException {
        HashMap<String, String> registro = cargarRegistroCompleto();
        return registro.get(propiedad);
    }

    public void setValor(String propiedad, String valor) throws IOException {
        HashMap<String, String> registro = cargarRegistroCompleto();

        registro.put(propiedad, valor);

        guardarRegistro(registro);
    }

    private HashMap<String, String> cargarRegistroCompleto() throws IOException {
        File file = new File(nombreArchivo).getAbsoluteFile();

        if (!file.exists()) {
            throw new IOException("No existe el archivo: " + nombreArchivo);
        }

        String contenido = new String(
                Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8
        ).trim();

        /*
         * PowerShell puede guardar UTF-8 con BOM.
         * Si aparece, lo eliminamos para que Gson no falle.
         */
        if (!contenido.isEmpty() && contenido.charAt(0) == '\uFEFF') {
            contenido = contenido.substring(1).trim();
        }

        if (contenido.isEmpty()) {
            return new HashMap<>();
        }

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
             * Si por error el fichero estuviera guardado como String JSON:
             * "{ \"MasterKey\": \"...\" }"
             * lo desempaquetamos y lo volvemos a parsear.
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
            throw new IOException("Formato inesperado en " + file.getAbsolutePath());
        }

        if (registro == null) {
            return new HashMap<>();
        }

        return new HashMap<>(registro);
    }

    private void guardarRegistro(HashMap<String, String> registro) throws IOException {
        File file = new File(nombreArchivo).getAbsoluteFile();

        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(registro, writer);
        }
    }
}