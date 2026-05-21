package eurocoin.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BancoComercial extends Remote {

    public Integer dameFondosEC() throws RemoteException;

    public Contacto dameContacto(String nombreBanco) throws RemoteException;
}