package eurocoin.banco;

import eurocoin.common.APPBancoMensaje;
import eurocoin.common.APPBancoPrimitive;
import eurocoin.common.GrantServer;
import eurocoin.common.Parametros;
import eurocoin.common.MalMensajeProtocoloException;
import eurocoin.util.Digest;
import eurocoin.util.Movimientos;

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
    private final GrantServer centralStub;

    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private Digest digest;

    private Map<String, Map<String, String>> registroBanco;
    private String idClienteAutenticado;

    private final Gson gson = new Gson();
    private final Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();

    public BancoSirviente(
            Socket socketCliente,
            String archivoRegistro,
            String archivoMovimientos,
            String nombreBanco,
            GrantServer centralStub) {

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

            /*
             * En la P1 el banco trabajaba con SHA-256 como función técnica del servidor.
             * Los clientes siguen firmando sus documentos con sus secretos.
             */
            digest = new Digest("SHA-256");

            if (!autenticar()) {
                System.out.println("[Sirviente] Autenticación fallida o denegada.");
                return;
            }

            System.out.println("[Sirviente] Sesión iniciada para cliente: " + idClienteAutenticado);

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
            System.out.println("[Sirviente] Sesión finalizada por excepción/cierre: " + e.getMessage());

        } finally {
            cerrar();
        }
    }

    private void cargarRegistroBanco() throws IOException {
        try (Reader reader = new FileReader(archivoRegistro)) {
            this.registroBanco = gson.fromJson(
                    reader,
                    new TypeToken<Map<String, Map<String, String>>>() {
                    }.getType());
        }

        if (this.registroBanco == null) {
            throw new IOException("RegistroBanco.json vacío o mal formado.");
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

            if (!(obj instanceof APPBancoMensaje)) {
                return false;
            }

            APPBancoMensaje msgA2 = (APPBancoMensaje) obj;

            if (msgA2.getPrimitiva() != APPBancoPrimitive.A2) {
                return false;
            }

            String desafioApp = msgA2.getArg1();
            String idCliente = msgA2.getArg2();
            String respuestaCliente = msgA2.getArg3();

            Map<String, String> datosCliente = registroBanco.get(idCliente);

            if (datosCliente == null) {
                salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
                salida.flush();
                return false;
            }

            String secWallet = datosCliente.get("SecWallet");
            String secBanco = datosCliente.get("SecBanco");

            if (secWallet == null || secBanco == null) {
                salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
                salida.flush();
                return false;
            }

            String respuestaEsperada = digest.generateHash(desafioBanco + secWallet);

            if (!respuestaEsperada.equals(respuestaCliente)) {
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

            String respuestaBanco = digest.generateHash(desafioApp + secBanco);

            salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A4, respuestaBanco));
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
            enviarErrorOperacion();
            return false;
        }

        String secWallet = cliente.get("SecWallet");
        String secBanco = cliente.get("SecBanco");

        if (secWallet == null || secBanco == null) {
            enviarErrorOperacion();
            return false;
        }

        if (!mac.equals(digest.generateHash(json + secWallet))) {
            enviarErrorOperacion();
            return false;
        }

        Map<String, String> data = gson.fromJson(
                json,
                new TypeToken<Map<String, String>>() {
                }.getType());

        String cantidadStr = data.get("cantidad");
        double cantidad = parseCantidadPositiva(cantidadStr);

        if (cantidad <= 0) {
            enviarErrorOperacion();
            return false;
        }

        Movimientos movimientosBanco = new Movimientos(archivoMovimientos);
        movimientosBanco.guardarMovimiento(json, mac);

        String macBanco;

        synchronized (REGISTRO_LOCK) {
            cargarRegistroBanco();

            Map<String, String> clienteActual = registroBanco.get(idClienteAutenticado);
            Map<String, String> banco = registroBanco.get(nombreBanco);

            if (clienteActual == null || banco == null) {
                enviarErrorOperacion();
                return false;
            }

            double saldoActual = parseDoubleSeguro(clienteActual.get("Saldo"));
            double fondosCliente = parseDoubleSeguro(clienteActual.get("FondosEC"));
            double fondosBanco = parseDoubleSeguro(banco.get("FondosEC"));
            double lowWatermark = parseDoubleSeguro(banco.get("LWEurocoins"));

            if (saldoActual < cantidad) {
                System.out.println("[Sirviente] Recarga rechazada: saldo bancario insuficiente.");
                enviarErrorOperacion();
                return false;
            }

            /*
             * RECARGA:
             * - El cliente convierte saldo bancario en eurocoins.
             * - Baja su Saldo.
             * - Sube su FondosEC.
             * - Baja la liquidez FondosEC del banco comercial.
             */
            saldoActual -= cantidad;
            fondosCliente += cantidad;
            fondosBanco -= cantidad;

            clienteActual.put("Saldo", formatoNumero(saldoActual));
            clienteActual.put("FondosEC", formatoNumero(fondosCliente));
            banco.put("FondosEC", formatoNumero(fondosBanco));

            if (fondosBanco < lowWatermark) {
                System.out.println("[Sirviente] FondosEC del banco por debajo de LWEurocoins: "
                        + formatoNumero(fondosBanco) + " < " + formatoNumero(lowWatermark));

                Parametros parametros = centralStub.solicitaAumento(
                        nombreBanco,
                        redondearAEntero(fondosBanco));

                aplicarParametrosBanco(banco, parametros);

                System.out.println("[Sirviente] BancoCentral aplicó aumento: " + parametros);
            }

            guardarRegistroBanco();
        }

        String jsonRecibo = "{ \"op\": \"recibo-recarga\", "
                + "\"titular\": \"" + idClienteAutenticado + "\", "
                + "\"cantidad\": \"" + cantidadStr + "\", "
                + "\"fecha\": \"" + new Date() + "\"}";

        macBanco = digest.generateHash(jsonRecibo + secBanco);

        movimientosBanco.guardarMovimiento(jsonRecibo, macBanco);

        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A10, jsonRecibo, macBanco));
        salida.flush();

        return true;
    }

    private boolean procesarDescarga(String json, String mac)
            throws IOException, MalMensajeProtocoloException {

        Map<String, String> cliente = registroBanco.get(idClienteAutenticado);

        if (cliente == null) {
            enviarErrorOperacion();
            return false;
        }

        String secWallet = cliente.get("SecWallet");
        String secBanco = cliente.get("SecBanco");

        if (secWallet == null || secBanco == null) {
            enviarErrorOperacion();
            return false;
        }

        if (!mac.equals(digest.generateHash(json + secWallet))) {
            enviarErrorOperacion();
            return false;
        }

        Map<String, String> data = gson.fromJson(
                json,
                new TypeToken<Map<String, String>>() {
                }.getType());

        String cantidadStr = data.get("cantidad");
        double cantidad = parseCantidadPositiva(cantidadStr);

        if (cantidad <= 0) {
            enviarErrorOperacion();
            return false;
        }

        Movimientos movimientosBanco = new Movimientos(archivoMovimientos);
        movimientosBanco.guardarMovimiento(json, mac);

        String macBanco;

        synchronized (REGISTRO_LOCK) {
            cargarRegistroBanco();

            Map<String, String> clienteActual = registroBanco.get(idClienteAutenticado);
            Map<String, String> banco = registroBanco.get(nombreBanco);

            if (clienteActual == null || banco == null) {
                enviarErrorOperacion();
                return false;
            }

            double saldoActual = parseDoubleSeguro(clienteActual.get("Saldo"));
            double fondosCliente = parseDoubleSeguro(clienteActual.get("FondosEC"));
            double fondosBanco = parseDoubleSeguro(banco.get("FondosEC"));
            double highWatermark = parseDoubleSeguro(banco.get("HWEurocoins"));

            /*
             * DESCARGA:
             * - El cliente devuelve eurocoins al banco.
             * - Sube su Saldo.
             * - Baja su FondosEC.
             * - Sube la liquidez FondosEC del banco comercial.
             *
             * No forzamos aquí rechazo por fondosCliente < cantidad porque en la práctica
             * puede haber ingresos offline pendientes de consolidación.
             */
            saldoActual += cantidad;
            fondosCliente -= cantidad;
            fondosBanco += cantidad;

            clienteActual.put("Saldo", formatoNumero(saldoActual));
            clienteActual.put("FondosEC", formatoNumero(fondosCliente));
            banco.put("FondosEC", formatoNumero(fondosBanco));

            if (fondosBanco > highWatermark) {
                System.out.println("[Sirviente] FondosEC del banco por encima de HWEurocoins: "
                        + formatoNumero(fondosBanco) + " > " + formatoNumero(highWatermark));

                Parametros parametros = centralStub.solicitaDecremento(
                        nombreBanco,
                        redondearAEntero(fondosBanco));

                aplicarParametrosBanco(banco, parametros);

                System.out.println("[Sirviente] BancoCentral aplicó decremento: " + parametros);
            }

            guardarRegistroBanco();
        }

        String jsonRecibo = "{ \"op\": \"recibo-descarga\", "
                + "\"titular\": \"" + idClienteAutenticado + "\", "
                + "\"cantidad\": \"" + cantidadStr + "\", "
                + "\"fecha\": \"" + new Date() + "\"}";

        macBanco = digest.generateHash(jsonRecibo + secBanco);

        movimientosBanco.guardarMovimiento(jsonRecibo, macBanco);

        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A12, jsonRecibo, macBanco));
        salida.flush();

        return true;
    }

    private void aplicarParametrosBanco(Map<String, String> banco, Parametros parametros) {
        banco.put("FondosEC", String.valueOf(parametros.fondosEC));
        banco.put("LWEurocoins", String.valueOf(parametros.lowWatermark));
        banco.put("HWEurocoins", String.valueOf(parametros.highWatermark));
    }

    private void enviarErrorOperacion() throws IOException, MalMensajeProtocoloException {
        salida.writeObject(new APPBancoMensaje(APPBancoPrimitive.A3));
        salida.flush();
    }

    private double parseCantidadPositiva(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return -1.0;
        }

        try {
            double cantidad = Double.parseDouble(valor);
            return cantidad > 0 ? cantidad : -1.0;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    private double parseDoubleSeguro(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(valor);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int redondearAEntero(double valor) {
        return (int) Math.round(valor);
    }

    private String formatoNumero(double valor) {
        double redondeado = Math.rint(valor);

        if (Math.abs(valor - redondeado) < 0.0000001) {
            return String.valueOf((long) redondeado);
        }

        return String.valueOf(valor);
    }

    private void cerrar() {
        try {
            if (idClienteAutenticado != null) {
                synchronized (Banco.usuariosActivos) {
                    Banco.usuariosActivos.remove(idClienteAutenticado);
                }
            }

            if (entrada != null) {
                entrada.close();
            }

            if (salida != null) {
                salida.close();
            }

            if (socketCliente != null) {
                socketCliente.close();
            }

        } catch (IOException e) {
            // Cierre silencioso.
        }
    }
}