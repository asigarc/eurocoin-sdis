package eurocoin.banco;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Date;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import eurocoin.common.APPBancoMensaje;
import eurocoin.common.APPBancoPrimitiva;
import eurocoin.common.MalMensajeProtocoloException;
import eurocoin.util.Digest;
import eurocoin.util.Registro;

public class BancoSirviente extends Thread {

    private final Socket socketCliente;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private Registro registroBanco;
    private Digest digest;

    // Guardamos el desafío del banco a nivel de clase para poder usarlo en los
    // métodos de la interfaz
    private String desafioBanco;

    // Guardamos el ID del cliente una vez autenticado para usarlo en las recargas
    private String idClienteAutenticado = null;

    // Objeto Gson para leer las cantidades dinámicas (PUNTO 4)
    private final Gson gson = new Gson();

    public BancoSirviente(Socket socketCliente) {
        this.socketCliente = socketCliente;
    }

    @Override
    public void run() {
        try {
            // 1. Preparar conexiones
            salida = new ObjectOutputStream(socketCliente.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socketCliente.getInputStream());

            // 2. Cargar utilidades
            registroBanco = new Registro("RegistroBanco.json");
            digest = new Digest("SHA-256");

            // ---- INICIO DEL PROTOCOLO ----

            // A1: Banco -> APP saludo(desafio_banco)
            desafioBanco = Long.toHexString(System.currentTimeMillis());
            System.out.println("[Sirviente] Enviando A1 y desafioBanco: " + desafioBanco);
            salida.writeObject(APPBancoPrimitiva.A1);
            salida.writeObject(desafioBanco);
            salida.flush();

            // A2: APP -> Banco saludo(desafio_app, id_cliente, hash)
            APPBancoPrimitiva prim = (APPBancoPrimitiva) entrada.readObject();

            if (prim == APPBancoPrimitiva.A2) {
                String desafioApp = (String) entrada.readObject();
                String idCliente = (String) entrada.readObject();
                String hashCliente = (String) entrada.readObject();

                System.out.println("[Sirviente] Recibido A2 del cliente: " + idCliente);

                saludo(idCliente, desafioApp, hashCliente);

                APPBancoPrimitiva respuestaApp = (APPBancoPrimitiva) entrada.readObject();

                if (respuestaApp == APPBancoPrimitiva.A5) {
                    System.out.println("[Sirviente] ¡Autenticación mutua OK (A5)!");

                    // MÓDULO 3: Bucle de operaciones (Recargas y Descargas)
                    boolean operando = true;
                    while (operando) {
                        System.out.println("[Sirviente] Esperando operación de la APP...");
                        Object peticion = entrada.readObject();

                        if (peticion instanceof APPBancoPrimitiva) {
                            APPBancoPrimitiva primOp = (APPBancoPrimitiva) peticion;
                            if (primOp == APPBancoPrimitiva.A7) {
                                System.out.println("[Sirviente] La APP ha solicitado desconexión (A7).");
                                m_final();
                                operando = false;
                            }
                        } else if (peticion instanceof APPBancoMensaje) {
                            APPBancoMensaje msg = (APPBancoMensaje) peticion;

                            if (msg.getPrimitiva() == APPBancoPrimitiva.A9) {
                                System.out.println("[Sirviente] Petición de recarga (A9) recibida.");
                                procesarRecarga(msg.getArg1(), msg.getArg2());
                            } else if (msg.getPrimitiva() == APPBancoPrimitiva.A11) {
                                System.out.println("[Sirviente] Petición de descarga (A11) recibida.");
                                procesarDescarga(msg.getArg1(), msg.getArg2());
                            }
                        }
                    }

                } else if (respuestaApp == APPBancoPrimitiva.A6) {
                    System.out.println("[Sirviente] La APP rechazó nuestra autenticación (A6).");
                }
            } else {
                System.out.println("[Sirviente] Se esperaba A2, llegó: " + prim);
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[Sirviente] Fin de conexión o error: " + e.getMessage());
        } finally {
            // Al terminar, liberamos el ID del usuario del Set para que pueda volver a
            // entrar
            if (idClienteAutenticado != null) {
                Banco.usuariosActivos.remove(idClienteAutenticado);
            }

            // Limpieza de recursos
            try {
                if (salida != null)
                    salida.close();
                if (entrada != null)
                    entrada.close();
                if (socketCliente != null)
                    socketCliente.close();
                System.out.println("[Sirviente] Socket cerrado.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // IMPLEMENTACIÓN DE BancoInterface

    public void saludo(String id_cliente, String desafioApp, String hashCliente) {
        try {
            // Evitar conexiones duplicadas del mismo usuario
            if (Banco.usuariosActivos.contains(id_cliente)) {
                System.out.println(
                        "[Sirviente] Acceso denegado: El usuario " + id_cliente + " ya tiene una sesión abierta.");
                saludo_err();
                return;
            }

            String secretoApp = registroBanco.getValor(id_cliente + "_secwallet");

            if (secretoApp == null) {
                System.out.println("[Sirviente] Usuario desconocido.");
                saludo_err();
                return;
            }

            String hashCalculado = digest.generateHash(desafioBanco + secretoApp);

            if (!hashCalculado.equals(hashCliente)) {
                System.out.println("[Sirviente] El hash no coincide.");
                saludo_err();
            } else {
                // Todo correcto: marcamos al usuario como activo oficialmente
                this.idClienteAutenticado = id_cliente;
                Banco.usuariosActivos.add(id_cliente);

                String secretoBanco = registroBanco.getValor(id_cliente + "_SecBanco");
                String hashParaApp = digest.generateHash(desafioApp + secretoBanco);

                System.out.println("[Sirviente] Cliente verificado. Enviando A4.");
                salida.writeObject(APPBancoPrimitiva.A4);
                salida.writeObject(hashParaApp);
                salida.flush();
                saludo_ok();
            }
        } catch (IOException e) {
            System.out.println("[Sirviente] Error verificando al cliente.");
            saludo_err();
        }
    }

    public void saludo_err() {
        try {
            System.out.println("[Sirviente] Enviando A3 (Error de autenticación).");
            salida.writeObject(APPBancoPrimitiva.A3);
            salida.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saludo_ok() {
        System.out.println("[Sirviente] Registro interno: saludo_ok procesado.");
    }

    public void m_final() {
        try {
            System.out.println("[Sirviente] Recibido A7. Enviando A8 (Final OK).");
            salida.writeObject(APPBancoPrimitiva.A8);
            salida.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // MÓDULO 3: Operaciones del Banco

    private void procesarRecarga(String jsonRecarga, String macApp) {
        try {
            // Parseamos el JSON para obtener la cantidad real que pide el cliente
            Map<String, String> datosPeticion = gson.fromJson(jsonRecarga, Map.class);
            String cantidad = datosPeticion.get("cantidad");

            System.out.println("[Sirviente] Autorizando recarga de " + cantidad + " EC para el cliente "
                    + idClienteAutenticado + "...");

            // Incluimos la fecha oficial del Banco en el recibo
            String fecha = new Date().toString();
            String jsonRecibo = "{\"op\": \"recibo-recarga\", \"titular\": \"" + idClienteAutenticado
                    + "\", \"cantidad\": \"" + cantidad + "\", \"fecha\": \"" + fecha + "\"}";

            // Firmamos el recibo con el Secreto del Banco
            String secretoBanco = registroBanco.getValor(idClienteAutenticado + "_SecBanco");
            String macBanco = digest.generateHash(jsonRecibo + secretoBanco);

            // Enviamos la respuesta (A10) a la APP
            salida.writeObject(new APPBancoMensaje(APPBancoPrimitiva.A10, jsonRecibo, macBanco));
            salida.flush();
            System.out.println("[Sirviente] Recibo A10 enviado con éxito.");

        } catch (JsonSyntaxException | MalMensajeProtocoloException | IOException e) {
            System.out.println("[Sirviente] Error al procesar la recarga: " + e.getMessage());
        }
    }

    private void procesarDescarga(String jsonDescarga, String macApp) {
        try {
            Map<String, String> datosPeticion = gson.fromJson(jsonDescarga, Map.class);
            String cantidad = datosPeticion.get("cantidad");

            System.out.println("[Sirviente] Procesando descarga de ingresos por valor de " + cantidad + " para "
                    + idClienteAutenticado + "...");

            String fecha = new Date().toString();
            String jsonRecibo = "{\"op\": \"recibo-descarga\", \"titular\": \"" + idClienteAutenticado
                    + "\", \"cantidad\": \"" + cantidad + "\", \"fecha\": \"" + fecha + "\"}";

            String secretoBanco = registroBanco.getValor(idClienteAutenticado + "_SecBanco");
            String macBanco = digest.generateHash(jsonRecibo + secretoBanco);

            salida.writeObject(new APPBancoMensaje(APPBancoPrimitiva.A12, jsonRecibo, macBanco));
            salida.flush();

            System.out.println("[Sirviente] Los ingresos han sido consolidados. Recibo A12 enviado con éxito.");

        } catch (JsonSyntaxException | MalMensajeProtocoloException | IOException e) {
            System.out.println("[Sirviente] Error al procesar la descarga: " + e.getMessage());
        }
    }
}