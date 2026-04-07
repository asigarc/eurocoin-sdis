package eurocoin.common;

public interface APPClienteInterface {
    void saludo(String desafio); // Procesar lo que se envia al Banco
    void saludo_err();  // Procesar si el banco rechaza
    void saludo_ok(String respuesta);   // Procesar si el banco acepta, y su respuesta
    void final_ok();    // Confirmar el cierre de conexion
}
