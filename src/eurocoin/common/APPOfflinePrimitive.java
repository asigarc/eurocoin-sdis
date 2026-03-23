package eurocoin.common;

public enum APPOfflinePrimitive implements java.io.Serializable {
    O1,  // saludo(desafio_prim) prim->sec
    O2,  // saludo(desafio_sec, hash(desafio_prim)) sec->prim
    O3,  // saludo_ok(nombre_prim, hash(desafio_sec)) prim->sec
    O4,  // saludo_ok(nombre_sec) sec->prim
    O5,  // ingreso(ObjetoJson, Hash(Obj+secr_prim)) prim->sec
    O6,  // recibo(ObjetoJson, Hash(Obj+secr_sec)) sec->prim
    O7,  // factura(ObjetoJson, Hash(Obj+secr_prim)) prim->sec
    O8,  // pago(ObjetoJson, Hash(Obj+secr_sec)) sec->prim
    O9,  // final pri->sec
    O10  // final_ok sec->pri
}
