package eurocoin.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Contacto extends Remote {

    public String hello(String mensaje) throws RemoteException;
}