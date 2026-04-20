package eurocoin.app;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

import eurocoin.common.APPBancoMensaje;
import eurocoin.common.APPBancoPrimitive;
import eurocoin.common.MalMensajeProtocoloException;
import eurocoin.util.Digest;
import eurocoin.util.Movimientos;
import eurocoin.util.Registro;

public class TPVOnline {

    private static final String HOST_BANCO = "L103C6";
    private static final int PUERTO_BANCO = 2000;

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private final Registro registro;
    private final Digest digest;
    private final Movimientos movimientos;
    private final Scanner teclado; // Añadido

    public TPVOnline(String archivoRegistro) throws IOException {
        this.registro = new Registro(archivoRegistro);
        this.digest = new Digest(registro.getValor("FuncionHash"));
        this.movimientos = new Movimientos("Movimientos.txt");
        this.teclado = new Scanner(System.in);
    }

    public void iniciar() {
        try {
            if (!conectar()) {
                return;
            }

            if (!autenticar()) {
                System.out.println("[TPVOnline] Error en la autenticación mutua.");
                return;
            }

            System.out.println("[TPVOnline] Autenticación completada con éxito.");

            realizarOperacionOnline();
            despedirse();

        } catch (Exception e) {
            System.out.println("[TPVOnline] Error: " + e.getMessage());
        } finally {
            cerrar();
        }
    }

    private boolean conectar() {
        try {
            this.socket = new Socket(HOST_BANCO, PUERTO_BANCO);
            this.salida = new ObjectOutputStream(socket.getOutputStream());
            this.salida.flush();
            this.entrada = new ObjectInputStream(socket.getInputStream());
            System.out.println("[TPVOnline] Conectado al banco.");
            return true;
        } catch (IOException e) {
            System.out.println("[TPVOnline] Error de conexión: " + e.getMessage());
            return false;
        }
    }

    private boolean autenticar() throws IOException, ClassNotFoundException, MalMensajeProtocoloException {
        // A1: Recibir desafío
        Object objA1 = entrada.readObject();
        if (!(objA1 instanceof APPBancoMensaje)) return false;

        APPBancoMensaje msgA1 = (APPBancoMensaje) objA1;
        if (msgA1.getPrimitiva() != APPBancoPrimitive.A1) return false;

        String desafioBanco = msgA1.getArg1();
        String secretoApp = registro.getValor("SecWallet");
        String respuestaABanco = digest.generateHash(desafioBanco + secretoApp);

        String desafioApp = "DesafTPV-" + System.currentTimeMillis();
        String idCliente = registro.getValor("usuario");

        // A2: Orden oficial (desafio_app, id_cliente, respuesta)
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A2, desafioApp, idCliente, respuestaABanco));
        salida.flush();

        // A4: Recibir validación
        Object objA4 = entrada.readObject();
        if (!(objA4 instanceof APPBancoMensaje)) return false;

        APPBancoMensaje msgA4 = (APPBancoMensaje) objA4;
        if (msgA4.getPrimitiva() != APPBancoPrimitive.A4) return false;

        String secretoBanco = registro.getValor("SecBanco");
        String respuestaEsperada = digest.generateHash(desafioApp + secretoBanco);

        if (respuestaEsperada.equals(msgA4.getArg1())) {
            salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A5));
            salida.flush();
            return true;
        } else {
            salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A6));
            salida.flush();
            return false;
        }
    }

    private void realizarOperacionOnline() throws Exception {
        System.out.println("\n--- OPERACIÓN ONLINE ÚNICA TPV ---");
        System.out.println("1. Descargar ingresos al banco (A11)");
        System.out.println("2. Salir sin operar");
        System.out.print("Seleccione: ");

        String op = teclado.nextLine();

        if ("1".equals(op)) {
            enviarDescarga();
        } else if ("2".equals(op)) {
            System.out.println("[TPVOnline] No se realizará ninguna operación.");
        } else {
            System.out.println("[TPVOnline] Opción no válida. Se cerrará la sesión.");
        }
    }

    private void enviarDescarga() throws Exception {
        System.out.println("[TPVOnline] Iniciando descarga de ingresos al banco...");

        String user = registro.getValor("usuario");
        String ingresos = registro.getValor("IngresosEC");

        if (ingresos == null || ingresos.trim().isEmpty()) {
            ingresos = "0.0";
        }

        // JSON titular y cantidad
        String jsonDescarga = "{ \"op\": \"descarga\", \"titular\": \"" + user + "\", \"cantidad\": \"" + ingresos + "\"}";
        String macDescarga = digest.generateHash(jsonDescarga + registro.getValor("SecWallet"));

        // Guardar rastro de la transacción
        movimientos.guardarMovimiento(jsonDescarga, macDescarga);

        System.out.println("[TPVOnline] Enviando A11 (descarga)...");
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A11, jsonDescarga, macDescarga));
        salida.flush();

        Object objResp = entrada.readObject();
        if (!(objResp instanceof APPBancoMensaje)) {
            System.out.println("[TPVOnline] Respuesta inválida.");
            return;
        }

        APPBancoMensaje respuesta = (APPBancoMensaje) objResp;

        if (respuesta.getPrimitiva() == APPBancoPrimitive.A12) {
            String jsonRecibo = respuesta.getArg1();
            String macBanco = respuesta.getArg2();

            String secretoBanco = registro.getValor("SecBanco");
            if (digest.generateHash(jsonRecibo + secretoBanco).equals(macBanco)) {
                System.out.println("[TPVOnline] Descarga aceptada y firmada por el banco.");

                movimientos.guardarMovimiento(jsonRecibo, macBanco);
                registro.setValor("IngresosEC", "0.0");

                // Vaciado de Movimientos.txt
                try (PrintWriter writer = new PrintWriter("Movimientos.txt")) {
                    writer.print("");
                }
                System.out.println("[TPVOnline] Movimientos consolidados y archivo limpiado.");
            } else {
                System.out.println("[TPVOnline] ERROR: Firma del banco inválida.");
            }
        } else if (respuesta.getPrimitiva() == APPBancoPrimitive.A3) {
            System.out.println("[TPVOnline] Operación rechazada por el banco.");
        }
    }

    private void despedirse() throws IOException, ClassNotFoundException, MalMensajeProtocoloException {
        System.out.println("[TPVOnline] Enviando A7 (final)...");
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A7));
        salida.flush();

        Object objA8 = entrada.readObject();
        if (objA8 instanceof APPBancoMensaje && ((APPBancoMensaje) objA8).getPrimitiva() == APPBancoPrimitive.A8) {
            System.out.println("[TPVOnline] A8 recibido. Sesión finalizada.");
        }
    }

    private void cerrar() {
        try {
            if (entrada != null) entrada.close();
            if (salida != null) salida.close();
            if (socket != null) socket.close();
        } catch (IOException e) { }
    }

    public static void main(String[] args) {
        // --- INICIO DE SEGURIDAD LOCAL ---
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        if (!eurocoin.util.AutenticadorLocal.verificarContrasena(scanner)) {
            System.out.println("\n[ERROR] Se han agotado los intentos o hubo un error.");
            System.out.println("Invalidación de la APP. Cerrando sistema...");
            scanner.close();
            System.exit(1); 
        }
        // --- FIN DE SEGURIDAD LOCAL ---
        
        try {
            TPVOnline tpv = new TPVOnline("Registro.json");
            tpv.iniciar();
        } catch (IOException e) {
            System.out.println("[TPVOnline] Error fatal: " + e.getMessage());
        }
    }
}