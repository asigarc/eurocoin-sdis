package eurocoin.banco;

import eurocoin.common.APPBancoMensaje;
import eurocoin.common.APPBancoPrimitive;
import eurocoin.common.GrantServer;
import eurocoin.common.Parametros;
import eurocoin.common.MalMensajeProtocoloException;
import eurocoin.util.Digest;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;
import java.util.Date;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class BancoSirviente implements Runnable {

    private static final Object REGISTRO_LOCK = new Object();

    private final Socket socketCliente;
    private final String archivoRegistro;
    private final String archivoMovimientos;
    private final String nombreBanco;
    private final GrantServer centralStub; // Referencia compartida del Stub RMI

    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private Digest digest;

    private Map<String, Map<String, String>> registroBanco;
    private String idClienteAutenticado;
    private final Gson gson = new Gson();
    private final Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();

    public BancoSirviente(Socket socketCliente, String archivoRegistro, String archivoMovimientos, String nombreBanco, GrantServer centralStub) {
        this.socketCliente = socketCliente;
        this.archivoRegistro = archivoRegistro;
        this.archivoMovimientos = archivoMovimientos;
        this.nombreBanco = nombreBanco;
        this.centralStub = centralStub;
        this.idClienteAutenticado = null;
    }

    @Override
    public void run() {
        try {
            salida = new ObjectOutputStream(socketCliente.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socketCliente.getInputStream());

            cargarRegistroBanco();
            digest = new Digest("SHA-256");

            if (!autenticar()) {
                System.out.println("[Sirviente] Autenticación fallida o denegada.");
                return;
            }

            System.out.println("[Sirviente] Sesión activa verificada para cliente: " + idClienteAutenticado);

            boolean conectado = true;
            while (conectado) {
                Object obj = entrada.readObject();
                if (!(obj instanceof APPBancoMensaje)) {
                    break;
                }

                APPBancoMensaje mensaje = (APPBancoMensaje) obj;
                APPBancoPrimitive primitiva = mensaje.getPrimitiva();

                switch (primitiva) {
                    case A9:
                        conectado = procesarRecarga(mensaje.getArg1(), mensaje.getArg2());
                        break;
                    case A11:
                        conectado = procesarDescarga(mensaje.getArg1(), mensaje.getArg2());
                        break;
                    case A7:
                        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A8));
                        salida.flush();
                        conectado = false;
                        break;
                    default:
                        conectado = false;
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("[Sirviente] Desconexión o excepción de canal: " + e.getMessage());
        } finally {
            cerrar();
        }
    }

    private void cargarRegistroBanco() throws IOException {
        try (Reader reader = new FileReader(archivoRegistro)) {
            this.registroBanco = gson.fromJson(reader, new TypeToken<Map<String, Map<String, String>>>() {}.getType());
        }
        if (this.registroBanco == null) {
            throw new IOException("Estructura JSON corrupta o vacía.");
        }
    }

    private void guardarRegistroBanco() throws IOException {
        try (Writer writer = new FileWriter(archivoRegistro)) {
            gsonPretty.toJson(this.registroBanco, writer);
        }
    }

    private boolean autenticar() {
        try {
            String desafioBanco = "DesafB-" + System.currentTimeMillis();
            salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A1, desafioBanco));
            salida.flush();

            Object obj = entrada.readObject();
            if (!(obj instanceof APPBancoMensaje)) return false;

            APPBancoMensaje msgA2 = (APPBancoMensaje) obj;
            if (msgA2.getPrimitiva() != APPBancoPrimitive.A2) return false;

            String desafioApp = msgA2.getArg1();
            String idCliente = msgA2.getArg2();
            String respuestaCliente = msgA2.getArg3();

            Map<String, String> datos = registroBanco.get(idCliente);
            if (datos == null) {
                salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
                salida.flush();
                return false;
            }

            String secWallet = datos.get("SecWallet");
            String secBanco = datos.get("SecBanco");

            if (!digest.generateHash(desafioBanco + secWallet).equals(respuestaCliente)) {
                salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
                salida.flush();
                return false;
            }

            synchronized (Banco.usuariosActivos) {
                if (Banco.usuariosActivos.contains(idCliente)) {
                    salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
                    salida.flush();
                    return false;
                }
                Banco.usuariosActivos.add(idCliente);
            }

            this.idClienteAutenticado = idCliente;

            salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A4, digest.generateHash(desafioApp + secBanco)));
            salida.flush();

            Object objFin = entrada.readObject();
            if (!(objFin instanceof APPBancoMensaje)) return false;

            return ((APPBancoMensaje) objFin).getPrimitiva() == APPBancoPrimitive.A5;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean procesarRecarga(String json, String mac) throws IOException, MalMensajeProtocoloException {
        Map<String, String> cliente = registroBanco.get(idClienteAutenticado);
        if (cliente == null) return false;

        String secWallet = cliente.get("SecWallet");
        String secBanco = cliente.get("SecBanco");

        if (!mac.equals(digest.generateHash(json + secWallet))) {
            salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
            salida.flush();
            return false;
        }

        Map<String, String> data = gson.fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
        double cantidad = Double.parseDouble(data.get("cantidad"));

        synchronized (REGISTRO_LOCK) {
            cargarRegistroBanco();

            Map<String, String> clienteActual = registroBanco.get(idClienteAutenticado);
            Map<String, String> banco = registroBanco.get(this.nombreBanco);

            double saldoActual = parseDoubleSeguro(clienteActual.get("Saldo"));
            int fondosBanco = Integer.parseInt(banco.get("FondosEC"));
            int lw = Integer.parseInt(banco.get("LWEurocoins"));

            // 1. Descontar saldo de cuenta corriente y restar liquidez al banco comercial
            clienteActual.put("Saldo", String.valueOf(saldoActual - cantidad));
            fondosBanco -= (int) cantidad;
            banco.put("FondosEC", String.valueOf(fondosBanco));

            // 2. Control de Banda (Regla 3: Inyección si fondos < low watermark)
            if (fondosBanco < lw && centralStub != null) {
                System.out.println("[Sirviente] Fondos por debajo de LWEurocoins (" + fondosBanco + " < " + lw + "). Solicitando inyección RMI...");
                try {
                    Parametros res = centralStub.solicitaAumento(this.nombreBanco, fondosBanco);
                    banco.put("FondosEC", String.valueOf(res.fondosEC));
                } catch (Exception rme) {
                    System.err.println("[Sirviente] Fallo de comunicación con el Banco Central: " + rme.getMessage());
                }
            }

            guardarRegistroBanco();
        }

        String jsonRecibo = "{ \"op\": \"recibo-recarga\", \"titular\": \"" + idClienteAutenticado + "\", \"cantidad\": \"" + data.get("cantidad") + "\", \"fecha\": \"" + new Date() + "\"}";
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A10, jsonRecibo, digest.generateHash(jsonRecibo + secBanco)));
        salida.flush();
        return true;
    }

    private boolean procesarDescarga(String json, String mac) throws IOException, MalMensajeProtocoloException {
        Map<String, String> cliente = registroBanco.get(idClienteAutenticado);
        if (cliente == null) return false;

        String secWallet = cliente.get("SecWallet");
        String secBanco = cliente.get("SecBanco");

        if (!mac.equals(digest.generateHash(json + secWallet))) {
            salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
            salida.flush();
            return false;
        }

        Map<String, String> data = gson.fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
        double cantidad = Double.parseDouble(data.get("cantidad"));

        synchronized (REGISTRO_LOCK) {
            cargarRegistroBanco();

            Map<String, String> clienteActual = registroBanco.get(idClienteAutenticado);
            Map<String, String> banco = registroBanco.get(this.nombreBanco);

            double saldoActual = parseDoubleSeguro(clienteActual.get("Saldo"));
            int fondosBanco = Integer.parseInt(banco.get("FondosEC"));
            int hw = Integer.parseInt(banco.get("HWEurocoins"));

            // 1. Ingresar saldo en cuenta y sumar liquidez al banco comercial
            clienteActual.put("Saldo", String.valueOf(saldoActual + cantidad));
            fondosBanco += (int) cantidad;
            banco.put("FondosEC", String.valueOf(fondosBanco));

            // 2. Control de Banda (Regla 4: Drenaje si fondos > high watermark)
            if (fondosBanco > hw && centralStub != null) {
                System.out.println("[Sirviente] Fondos exceden HWEurocoins (" + fondosBanco + " > " + hw + "). Solicitando drenaje RMI...");
                try {
                    Parametros res = centralStub.solicitaDecremento(this.nombreBanco, fondosBanco);
                    banco.put("FondosEC", String.valueOf(res.fondosEC));
                } catch (Exception rme) {
                    System.err.println("[Sirviente] Fallo de comunicación con el Banco Central: " + rme.getMessage());
                }
            }

            guardarRegistroBanco();
        }

        String jsonRecibo = "{ \"op\": \"recibo-descarga\", \"titular\": \"" + idClienteAutenticado + "\", \"cantidad\": \"" + data.get("cantidad") + "\", \"fecha\": \"" + new Date() + "\"}";
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A12, jsonRecibo, digest.generateHash(jsonRecibo + secBanco)));
        salida.flush();
        return true;
    }

    private double parseDoubleSeguro(String valor) {
        if (valor == null || valor.trim().isEmpty()) return 0.0;
        return Double.parseDouble(valor);
    }

    private void cerrar() {
        try {
            if (idClienteAutenticado != null) {
                synchronized (Banco.usuariosActivos) {
                    Banco.usuariosActivos.remove(idClienteAutenticado);
                }
            }
            if (entrada != null) entrada.close();
            if (salida != null) salida.close();
            if (socketCliente != null) socketCliente.close();
        } catch (IOException e) { }
    }
}