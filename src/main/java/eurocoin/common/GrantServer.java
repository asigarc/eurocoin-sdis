package eurocoin.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GrantServer extends Remote {

    public Parametros nuevoBanco(String nombreBanco, Integer activos)
            throws RemoteException;

    public Parametros solicitaAumento(String nombreBanco, Integer fondoECPrevisto)
            throws RemoteException;

    public Parametros solicitaDecremento(String nombreBanco, Integer fondoECPrevisto)
            throws RemoteException;
}