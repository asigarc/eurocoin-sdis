package eurocoin.bancocentral;
import eurocoin.common.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


public class GrantServerObject extends UnicastRemoteObject implements GrantServer { // hay que hacer algo con nombreBanco??

    public GrantServerObject() throws RemoteException {
        super();
    }

    @Override
    public Parametros nuevoBanco(String nombreBanco, Integer activos) throws RemoteException {
        return new Parametros(
            10000,
            100000,
            activos
        );
    }

    @Override
    public Parametros solicitaAumento(String nombreBanco, Integer fondoECPrevisto) throws RemoteException {
        return new Parametros(
            10000,
            100000,
            fondoECPrevisto + 50000
        );
    }

    @Override
    public Parametros solicitaDecremento(String nombreBanco, Integer fondoECPrevisto) throws RemoteException {
        return new Parametros(
            10000,
            100000,
            fondoECPrevisto - 50000
        );
    }
}