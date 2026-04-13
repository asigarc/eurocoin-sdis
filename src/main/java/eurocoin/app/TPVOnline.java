package eurocoin.app;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;

import eurocoin.common.APPBancoMensaje;
import eurocoin.common.APPBancoPrimitiva;
import eurocoin.common.MalMensajeProtocoloException;
import eurocoin.util.Digest;
import eurocoin.util.Registro;

public class TPVOnline {

    private static final String HOST_BANCO = "localhost";
    private static final int PUERTO_BANCO = 2000;

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private final Registro registro;
    private final Digest digest;

    public TPVOnline(String archivoRegistro) throws IOException {
        this.registro = new Registro(archivoRegistro);
        this.digest = new Digest(registro.getValor("FuncionHash"));
    }

    public void iniciar() {
        if (!conectar())
            return;
        try {
            if (autenticar()) {
                System.out.println("[TPVOnline] Autenticación completada con éxito.");
                enviarDescarga();
                despedirse();
            }
        } catch (Exception e) {
            System.out.println("[TPVOnline] Error: " + e.getMessage());
        } finally {
            cerrar();
        }
    }

    private boolean conectar() {
        try {
            socket = new Socket(HOST_BANCO, PUERTO_BANCO);
            salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socket.getInputStream());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean autenticar() throws IOException, ClassNotFoundException {
        // Flujo A1 a A6 idéntico al ClienteBanco
        APPBancoPrimitiva prim = (APPBancoPrimitiva) entrada.readObject();
        if (prim != APPBancoPrimitiva.A1)
            return false;
        String desafioBanco = (String) entrada.readObject();

        String idCliente = registro.getValor("usuario");
        String secretoApp = registro.getValor("SecWallet");
        String desafioApp = Long.toHexString(System.currentTimeMillis());
        String hashParaBanco = digest.generateHash(desafioBanco + secretoApp);

        salida.writeObject(APPBancoPrimitiva.A2);
        salida.writeObject(desafioApp);
        salida.writeObject(idCliente);
        salida.writeObject(hashParaBanco);
        salida.flush();

        APPBancoPrimitiva respBanco = (APPBancoPrimitiva) entrada.readObject();
        if (respBanco == APPBancoPrimitiva.A3)
            return false;

        String respuestaBanco = (String) entrada.readObject();
        String secretoBanco = registro.getValor("SecBanco");

        if (digest.generateHash(desafioApp + secretoBanco).equals(respuestaBanco)) {
            salida.writeObject(APPBancoPrimitiva.A5);
            salida.flush();
            return true;
        } else {
            salida.writeObject(APPBancoPrimitiva.A6);
            salida.flush();
            return false;
        }
    }

    private void enviarDescarga() throws IOException, ClassNotFoundException, MalMensajeProtocoloException {
        System.out.println("--------------------------------------------------");
        System.out.println(" Iniciando descarga de ingresos al Banco...");

        String idCliente = registro.getValor("usuario");
        String ingresosOffline = registro.getValor("IngresosEC");

        if (ingresosOffline == null || ingresosOffline.equals("0")) {
            System.out.println("[TPVOnline] No hay ingresos offline que descargar.");
            return;
        }

        // 1. Construir JSON A11
        String jsonDescarga = "{\"op\": \"descarga\", \"titular\": \"" + idCliente + "\", \"cantidad\": \""
                + ingresosOffline + "\"}";
        String secWallet = registro.getValor("SecWallet");
        String macDescarga = digest.generateHash(jsonDescarga + secWallet);

        // 2. Enviar al Banco
        System.out.println("[TPVOnline] Enviando A11: Descargando " + ingresosOffline + " EC...");
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitiva.A11, jsonDescarga, macDescarga));
        salida.flush();

        // 3. Esperar confirmación A12
        APPBancoMensaje respuesta = (APPBancoMensaje) entrada.readObject();

        if (respuesta.getPrimitiva() == APPBancoPrimitiva.A12) {
            System.out.println("[TPVOnline] ¡El Banco ha aceptado la descarga!");

            // 4. Resetear los IngresosEC a "0"
            registro.setValor("IngresosEC", "0");

            // 5. Vaciar el archivo Movimientos.txt (consolidación)
            new PrintWriter("Movimientos.txt").close();

            System.out.println("[TPVOnline] Movimientos offline limpiados y consolidados correctamente.");
        } else {
            System.out.println("[TPVOnline] Error en la descarga (No llegó A12).");
        }
    }

    private void despedirse() throws IOException, ClassNotFoundException {
        salida.writeObject(APPBancoPrimitiva.A7);
        salida.flush();
        entrada.readObject(); // Esperar A8
    }

    private void cerrar() {
        try {
            if (salida != null)
                salida.close();
            if (entrada != null)
                entrada.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {
        }
    }

    public static void main(String[] args) {
        try {
            new TPVOnline("Registro.json").iniciar();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}