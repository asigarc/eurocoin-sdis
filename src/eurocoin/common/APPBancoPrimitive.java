package eurocoin.common;

public enum APPBancoPrimitive implements java.io.Serializable {
    A1, // saludo (desafio_banco) banco->app
    A2, // saludo (desafio_app, id_cliente, hash(desafio_banco+secreto_app)) app->banco
    A3, // saludo_err banco->app
    A4, // saludo_ok (respuesta) banco->app
    A5, // saludo_ok app->banco
    A6, // saludo_err app->banco
    A7, // final app->banco
    A8 // final_ok banco->app
}