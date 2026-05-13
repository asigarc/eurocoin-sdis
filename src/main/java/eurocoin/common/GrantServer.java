
package eurocoin.common;
public interface GrantServer extends java.rmi.Remote {
  public Parametros nuevoBanco(String nombreBanco, Integer activos)
        throws java.rmi.RemoteException;
  public Parametros solicitaAumento(String nombreBanco, Integer fondoECPrevisto)
        throws java.rmi.RemoteException;
  public Parametros solicitaDecremento(String  nombreBanco, Integer fondoECPrevisto)
        throws java.rmi.RemoteException;
}