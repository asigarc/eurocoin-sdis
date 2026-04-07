package eurocoin.common;

public interface BancoInterface {
    void saludo(String id_cliente, String desafio, String respuesta);

    void saludo_err(); // Informar al cliente que algo ha fallado

    void saludo_ok(); // Confimrar que el cliente ha sido aceptado

    void m_final(); // Gestionar el cierre de la sesion
}
