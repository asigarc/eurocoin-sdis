package eurocoin.common;

// APPBancoPrimitive - Enum de Primitivas para protocolo APP-Banco

public enum APPBancoPrimitive implements java.io.Serializable {
  A1, // saludo(desafio_banco) banco->app
  A2, // saludo(desafio_app, id_cliente, hash(desafio_banco+secreto_app)) app->banco
  A3, // saludo_err.
  A4, // saludo_ok(hash(desafio_app+secreto_banco)) banco->app
  A5, // saludo_ok app->banco
  A6, // saludo_err app->banco
  A7, // final app->banco
  A8, // final_ok banco->app
  A9, // recarga(doc-recarga, MAC_app) wallet->banco
  A10, // recibo-recarga(doc-recibo-recarga, MAC_banco) banco->wallet
  A11, // descarga(doc-descarga, MAC_app) app->banco
  A12 // recibo-descarga(doc-recibo-descarga, MAC_banco) banco->app
}