package eurocoin.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GrantServer extends Remote {

        public Parametros nuevoBanco(String nombreBanco, BancoComercial bc)
                throws RemoteException;

        public Parametros solicitaAumento(String nombreBanco, Integer fondoECPrevisto)
                throws RemoteException;

        public Parametros solicitaDecremento(String nombreBanco, Integer fondoECPrevisto)
                throws RemoteException;

        public Contacto dameContacto(String nombreBanco, String nombreBancoDestino)
                throws RemoteException;
}