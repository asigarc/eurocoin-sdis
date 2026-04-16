package eurocoin.app;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

import eurocoin.common.APPBancoMensaje;
import eurocoin.common.APPBancoPrimitive;
import eurocoin.common.MalMensajeProtocoloException;
import eurocoin.util.Digest;
import eurocoin.util.Movimientos;
import eurocoin.util.Registro;

public class Wallet {

    private static final String HOST_BANCO = "localhost";
    private static final int PUERTO_BANCO = 2000;

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private final Registro registro;
    private final Digest digest;
    private final Movimientos movimientos;
    private final Scanner teclado;

    public Wallet(String archivoRegistro) throws IOException {
        this.registro = new Registro(archivoRegistro);
        this.digest = new Digest(registro.getValor("FuncionHash"));
        this.movimientos = new Movimientos("Movimientos.txt");
        this.teclado = new Scanner(System.in);
    }

    public void iniciar() {
        System.out.println("=== EUROCOIN WALLET ===");
        System.out.println("1. Modo ONLINE (Conexión con Banco)");
        System.out.println("2. Modo OFFLINE (Pago/Cobro directo)");
        System.out.print("Seleccione opción: ");

        String opcion = teclado.nextLine();

        if ("1".equals(opcion)) {
            iniciarOnline();
        } else if ("2".equals(opcion)) {
            iniciarOffline();
        } else {
            System.out.println("[Wallet] Opción no válida.");
        }
    }

    private void iniciarOnline() {
        try {
            if (!conectar()) {
                return;
            }

            if (!autenticar()) {
                System.out.println("[Wallet] Error en la autenticación mutua.");
                return;
            }

            System.out.println("[Wallet] Autenticación completada con éxito.");

            // El módulo 3 exige operación simple:
            // una conexión, una autenticación, una operación y una despedida.
            realizarOperacionOnline();
            despedirse();

        } catch (Exception e) {
            System.out.println("[Wallet] Error en el flujo online: " + e.getMessage());
        } finally {
            cerrar();
        }
    }

    private void iniciarOffline() {
        System.out.println("[Wallet] Iniciando modo OFFLINE...");
        System.out.println("1. Actuar como PAGADOR (Lanzar WalletOfflinePrimario)");
        System.out.println("2. Actuar como COBRADOR (Lanzar WalletOfflineSecundario)");
        System.out.print("Seleccione rol: ");

        String rol = teclado.nextLine();

        if ("1".equals(rol)) {
            WalletOfflinePrimario.main(null);
        } else if ("2".equals(rol)) {
            WalletOfflineSecundario.main(null);
        } else {
            System.out.println("[Wallet] Rol no válido.");
        }
    }

    private boolean conectar() {
        try {
            this.socket = new Socket(HOST_BANCO, PUERTO_BANCO);
            this.salida = new ObjectOutputStream(socket.getOutputStream());
            this.salida.flush();
            this.entrada = new ObjectInputStream(socket.getInputStream());
            System.out.println("[Wallet] Conectado al banco.");
            return true;
        } catch (IOException e) {
            System.out.println("[Wallet] Error al conectar: " + e.getMessage());
            return false;
        }
    }

    private boolean autenticar() throws IOException, ClassNotFoundException, MalMensajeProtocoloException {
        Object objA1 = entrada.readObject();
        if (!(objA1 instanceof APPBancoMensaje)) {
            return false;
        }

        APPBancoMensaje msgA1 = (APPBancoMensaje) objA1;
        if (msgA1.getPrimitiva() != APPBancoPrimitive.A1) {
            return false;
        }

        String desafioBanco = msgA1.getArg1();
        String secretoApp = registro.getValor("SecWallet");
        String respuestaABanco = digest.generateHash(desafioBanco + secretoApp);

        String desafioApp = "DesafApp-" + System.currentTimeMillis();
        String idCliente = registro.getValor("usuario");

        // Orden fiel al diagrama de proceso online:
        // saludo(desafio_A, id_cliente, respuesta)
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A2, desafioApp, idCliente, respuestaABanco));
        salida.flush();

        Object objA4 = entrada.readObject();
        if (!(objA4 instanceof APPBancoMensaje)) {
            return false;
        }

        APPBancoMensaje msgA4 = (APPBancoMensaje) objA4;
        if (msgA4.getPrimitiva() != APPBancoPrimitive.A4) {
            return false;
        }

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
        System.out.println("\n--- OPERACIÓN ONLINE ÚNICA ---");
        System.out.println("1. Recargar fondos (A9)");
        System.out.println("2. Salir sin operar");
        System.out.print("Seleccione: ");

        String op = teclado.nextLine();

        if ("1".equals(op)) {
            realizarRecarga();
        } else if ("2".equals(op)) {
            System.out.println("[Wallet] No se realizará ninguna operación.");
        } else {
            System.out.println("[Wallet] Opción no válida. Se cerrará la sesión.");
        }
    }

    private void realizarRecarga() throws Exception {
        System.out.print("[Wallet] Cantidad de EuroCoins a recargar: ");
        String cantidad = teclado.nextLine();

        String user = registro.getValor("usuario");
        String jsonRecarga = "{ \"op\": \"recarga\", \"titular\": \"" + user + "\", \"cantidad\": \"" + cantidad + "\"}";
        String macApp = digest.generateHash(jsonRecarga + registro.getValor("SecWallet"));

        // Guardar el documento firmado de la APP en Movimientos.txt
        movimientos.guardarMovimiento(jsonRecarga, macApp);

        System.out.println("[Wallet] Enviando A9 (petición de recarga)...");
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A9, jsonRecarga, macApp));
        salida.flush();

        Object objResp = entrada.readObject();
        if (!(objResp instanceof APPBancoMensaje)) {
            System.out.println("[Wallet] Respuesta inválida del banco.");
            return;
        }

        APPBancoMensaje respuesta = (APPBancoMensaje) objResp;

        if (respuesta.getPrimitiva() == APPBancoPrimitive.A10) {
            String jsonRecibo = respuesta.getArg1();
            String macBanco = respuesta.getArg2();

            String secretoBanco = registro.getValor("SecBanco");
            String macEsperado = digest.generateHash(jsonRecibo + secretoBanco);

            if (macEsperado.equals(macBanco)) {
                System.out.println("[Wallet] A10 recibido. Firma válida.");

                // Guardar también el recibo firmado del banco
                movimientos.guardarMovimiento(jsonRecibo, macBanco);

                String fondosStr = registro.getValor("FondosEC");
                double saldoActual = (fondosStr == null || fondosStr.trim().isEmpty())
                        ? 0.0
                        : Double.parseDouble(fondosStr);

                double nuevoSaldo = saldoActual + Double.parseDouble(cantidad);
                registro.setValor("FondosEC", String.valueOf(nuevoSaldo));

                System.out.println("[Wallet] Recarga completada. Nuevo saldo: " + nuevoSaldo);
            } else {
                System.out.println("[Wallet] ERROR: Firma del banco inválida.");
            }
        } else if (respuesta.getPrimitiva() == APPBancoPrimitive.A3) {
            System.out.println("[Wallet] El banco rechazó la operación.");
        } else {
            System.out.println("[Wallet] Respuesta inesperada del banco: " + respuesta.getPrimitiva());
        }
    }

    private void despedirse() throws IOException, ClassNotFoundException, MalMensajeProtocoloException {
        System.out.println("[Wallet] Enviando A7 (final)...");
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A7));
        salida.flush();

        Object objA8 = entrada.readObject();
        if (!(objA8 instanceof APPBancoMensaje)) {
            System.out.println("[Wallet] Respuesta final inválida.");
            return;
        }

        APPBancoMensaje msgA8 = (APPBancoMensaje) objA8;
        if (msgA8.getPrimitiva() == APPBancoPrimitive.A8) {
            System.out.println("[Wallet] A8 recibido (final_ok). Sesión cerrada.");
        } else {
            System.out.println("[Wallet] Respuesta inesperada al finalizar: " + msgA8.getPrimitiva());
        }
    }

    private void cerrar() {
        try {
            if (salida != null) {
                salida.close();
            }
            if (entrada != null) {
                entrada.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("[Wallet] Error cerrando recursos: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // Creamos el escáner para la entrada de teclado
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        
        // 1. Verificación local (Requisito del diagrama de la pág. 2)
        // Esta llamada maneja los 3 intentos y la lectura de Registro.json
        if (!eurocoin.util.AutenticadorLocal.verificarContrasena(scanner)) {
            System.out.println("\n[ERROR] Se han agotado los intentos o hubo un error.");
            System.out.println("Invalidación de la APP. Cerrando sistema...");
            scanner.close();
            System.exit(1); // Terminamos el programa aquí
        }

        // 2. Conexión y Autenticación Mutua
        // Solo si la contraseña fue correcta llegamos a este punto
        try {
<<<<<<< HEAD
            Wallet wallet = new Wallet("Registro.json");
            wallet.iniciar();
        } catch (IOException e) {
            System.out.println("[Wallet] Error fatal: " + e.getMessage());
=======
            Wallet cliente = new Wallet("Registro.json");
            cliente.iniciar(); // Aquí es donde se llama a conectar() y autenticar()
        } catch (java.io.IOException e) {
            System.err.println("[ERROR] No se pudo iniciar el Wallet: " + e.getMessage());
        } finally {
            scanner.close();
>>>>>>> 29c195b094561642db814d378ab0d6274bdb545a
        }
    }
}