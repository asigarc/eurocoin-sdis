package eurocoin.common;


public interface GrantServer extends java.rmi.Remote {

    public Parametros nuevoBanco(String nombre, Integer activos)
        throws java.rmi.RemoteException;
    public Parametros solicitaAumento(Integer fondoECPrevisto)
        throws java.rmi.RemoteException;
    public Parametros solicitaDecremento(Integer fondoECPrevisto)
        throws java.rmi.RemoteException;

}