package eurocoin.banco;

import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;

import eurocoin.common.Contacto;

public class ContactoImpl extends UnicastRemoteObject implements Contacto {

    private static final long serialVersionUID = 1L;

    private final String bancoDestino;
    private final String bancoOrigen;

    public ContactoImpl(
            String bancoDestino,
            String bancoOrigen,
            RMIClientSocketFactory clientFactory,
            RMIServerSocketFactory serverFactory)
            throws RemoteException {

        super(0, clientFactory, serverFactory);

        this.bancoDestino = bancoDestino;
        this.bancoOrigen = bancoOrigen;
    }

    @Override
    public String hello(String mensaje) throws RemoteException {
        System.out.println("[Contacto-" + bancoDestino + "] Mensaje recibido desde "
                + bancoOrigen + ": " + mensaje);

        String respuesta = "Hola " + bancoOrigen + ", soy " + bancoDestino
                + ". He recibido tu mensaje: \"" + mensaje + "\"";

        System.out.println("[Contacto-" + bancoDestino + "] Respuesta enviada: " + respuesta);

        return respuesta;
    }
}