package eurocoin.util;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

// Clase para guardar las operaciones offline en Movimientos.txt

public class Movimientos {

    private final String nombreArchivo;

    public Movimientos(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    // Guarda el apunte literal y su MAC en el archivo de texto
    public void guardarMovimiento(String jsonLiteral, String mac) throws IOException {
        // El 'true' en FileWriter indica que está activado el modo append
        try (PrintWriter salida = new PrintWriter(new FileWriter(nombreArchivo, true))) {
            salida.println(jsonLiteral);
            salida.println(mac);
        }
    }
}