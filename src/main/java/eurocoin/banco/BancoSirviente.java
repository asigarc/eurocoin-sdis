package eurocoin.banco;

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

import eurocoin.common.APPBancoMensaje;
import eurocoin.common.APPBancoPrimitive;
import eurocoin.common.MalMensajeProtocoloException;
import eurocoin.util.Digest;

public class BancoSirviente implements Runnable {

    private static final Object REGISTRO_LOCK = new Object();

    private final Socket socketCliente;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private Digest digest;

    private Map<String, Map<String, String>> registroBanco;
    private String idClienteAutenticado;
    private final Gson gson = new Gson();
    private final Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();

    public BancoSirviente(Socket socketCliente) {
        this.socketCliente = socketCliente;
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

            System.out.println("[Sirviente] Nueva conexión desde " + socketCliente.getInetAddress());

            if (!autenticar()) {
                System.out.println("[Sirviente] Autenticación fallida.");
                return;
            }

            System.out.println("[Sirviente] Sesión iniciada para: " + idClienteAutenticado);

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
                        System.out.println("[Sirviente] Primitiva inesperada: " + primitiva);
                        conectado = false;
                        break;
                }
            }

        } catch (Exception e) {
            System.out.println("[Sirviente] Error: " + e.getMessage());
        } finally {
            cerrar();
        }
    }

    private void cargarRegistroBanco() throws IOException {
        try (Reader reader = new FileReader("RegistroBanco.json")) {
            this.registroBanco = gson.fromJson(
                    reader,
                    new TypeToken<Map<String, Map<String, String>>>() {}.getType());
        }

        if (this.registroBanco == null) {
            throw new IOException("RegistroBanco.json no encontrado o mal formado.");
        }
    }

    private void guardarRegistroBanco() throws IOException {
        try (Writer writer = new FileWriter("RegistroBanco.json")) {
            gsonPretty.toJson(this.registroBanco, writer);
        }
    }

    private boolean autenticar() {
        try {
            String desafioBanco = "DesafB-" + System.currentTimeMillis();
            salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A1, desafioBanco));
            salida.flush();

            Object obj = entrada.readObject();
            if (!(obj instanceof APPBancoMensaje)) {
                return false;
            }

            APPBancoMensaje msgA2 = (APPBancoMensaje) obj;
            if (msgA2.getPrimitiva() != APPBancoPrimitive.A2) {
                return false;
            }

            // (desafio_app, id_cliente, respuesta)
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

            if (secWallet == null || secBanco == null) {
                salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
                salida.flush();
                return false;
            }

            if (!digest.generateHash(desafioBanco + secWallet).equals(respuestaCliente)) {
                salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
                salida.flush();
                return false;
            }

            synchronized (Banco.usuariosActivos) {
                if (Banco.usuariosActivos.contains(idCliente)) {
                    System.out.println("[Sirviente] Usuario ya conectado: " + idCliente);
                    salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
                    salida.flush();
                    return false;
                }
                Banco.usuariosActivos.add(idCliente);
            }

            this.idClienteAutenticado = idCliente;

            salida.writeObject(new APPBancoMensaje(
                    APPBancoPrimitive.A4,
                    digest.generateHash(desafioApp + secBanco)));
            salida.flush();

            Object objFin = entrada.readObject();
            if (!(objFin instanceof APPBancoMensaje)) {
                return false;
            }

            APPBancoMensaje msgFin = (APPBancoMensaje) objFin;
            return msgFin.getPrimitiva() == APPBancoPrimitive.A5;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean procesarRecarga(String json, String mac)
            throws IOException, MalMensajeProtocoloException {

        Map<String, String> cliente = registroBanco.get(idClienteAutenticado);
        if (cliente == null) {
            return false;
        }

        String secWallet = cliente.get("SecWallet");
        String secBanco = cliente.get("SecBanco");

        if (secWallet == null || secBanco == null) {
            return false;
        }

        if (!mac.equals(digest.generateHash(json + secWallet))) {
            salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
            salida.flush();
            return false;
        }

        Map<String, String> data = gson.fromJson(
                json,
                new TypeToken<Map<String, String>>() {}.getType());

        String cantidadStr = data.get("cantidad");
        double cantidad = Double.parseDouble(cantidadStr);

        synchronized (REGISTRO_LOCK) {
            cargarRegistroBanco();

            Map<String, String> clienteActual = registroBanco.get(idClienteAutenticado);
            if (clienteActual == null) {
                return false;
            }

            double saldoActual = parseDoubleSeguro(clienteActual.get("Saldo"));
            double fondosActuales = parseDoubleSeguro(clienteActual.get("FondosEC"));

            clienteActual.put("Saldo", String.valueOf(saldoActual - cantidad));
            clienteActual.put("FondosEC", String.valueOf(fondosActuales + cantidad));

            guardarRegistroBanco();
        }

        String jsonRecibo = "{ \"op\": \"recibo-recarga\", \"titular\": \"" + idClienteAutenticado
                + "\", \"cantidad\": \"" + cantidadStr
                + "\", \"fecha\": \"" + new Date() + "\"}";

        String macBanco = digest.generateHash(jsonRecibo + secBanco);

        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A10, jsonRecibo, macBanco));
        salida.flush();

        return true;
    }

    private boolean procesarDescarga(String json, String mac)
            throws IOException, MalMensajeProtocoloException {

        Map<String, String> cliente = registroBanco.get(idClienteAutenticado);
        if (cliente == null) {
            return false;
        }

        String secWallet = cliente.get("SecWallet");
        String secBanco = cliente.get("SecBanco");

        if (secWallet == null || secBanco == null) {
            return false;
        }

        if (!mac.equals(digest.generateHash(json + secWallet))) {
            salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
            salida.flush();
            return false;
        }

        Map<String, String> data = gson.fromJson(
                json,
                new TypeToken<Map<String, String>>() {}.getType());

        String cantidadStr = data.get("cantidad");
        double cantidad = Double.parseDouble(cantidadStr);

        synchronized (REGISTRO_LOCK) {
            cargarRegistroBanco();

            Map<String, String> clienteActual = registroBanco.get(idClienteAutenticado);
            if (clienteActual == null) {
                return false;
            }

            double saldoActual = parseDoubleSeguro(clienteActual.get("Saldo"));
            double fondosActuales = parseDoubleSeguro(clienteActual.get("FondosEC"));

            // Descarga: eurocoins/ingresos pendientes -> saldo bancario
            clienteActual.put("Saldo", String.valueOf(saldoActual + cantidad));
            clienteActual.put("FondosEC", String.valueOf(fondosActuales - cantidad));

            guardarRegistroBanco();
        }

        String jsonRecibo = "{ \"op\": \"recibo-descarga\", \"titular\": \"" + idClienteAutenticado
                + "\", \"cantidad\": \"" + cantidadStr
                + "\", \"fecha\": \"" + new Date() + "\"}";

        String macBanco = digest.generateHash(jsonRecibo + secBanco);

        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A12, jsonRecibo, macBanco));
        salida.flush();

        return true;
    }

    private double parseDoubleSeguro(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return 0.0;
        }
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