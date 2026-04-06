package utiles.movimientos;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

/**
 * Clase encargada de persistir las operaciones offline en Movimientos.txt
 */
public class Movimientos {

    private final String nombreArchivo;

    public Movimientos(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    /**
     * Guarda el apunte literal y su MAC en el archivo de texto.
     * Es vital guardar los Strings EXACTAMENTE como se envían/reciben.
     * * @param jsonLiteral El String con formato JSON de la operación.
     * @param mac La firma criptográfica de ese String.
     * @throws IOException Si hay problemas de escritura.
     */
    public void guardarMovimiento(String jsonLiteral, String mac) throws IOException {
        // El 'true' en FileWriter indica modo "append" (añadir al final sin borrar)
        try (PrintWriter salida = new PrintWriter(new FileWriter(nombreArchivo, true))) {
            salida.println(jsonLiteral);
            salida.println(mac);
        }
    }
}