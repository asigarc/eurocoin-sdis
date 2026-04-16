package eurocoin.common;

// APPOfflineMensaje - Mensaje del Protocolo APP-APP offline

public class APPOfflineMensaje implements java.io.Serializable {
  private final APPOfflinePrimitive primitiva;
  private final String arg1;
  private final String arg2;

  // Constructor 0 args para O9 y O10
  public APPOfflineMensaje(APPOfflinePrimitive p)
      throws MalMensajeProtocoloException {
    if (p == APPOfflinePrimitive.O9 || p == APPOfflinePrimitive.O10) {
      this.primitiva = p;
      this.arg1 = this.arg2 = null;
    } else
      throw new MalMensajeProtocoloException();
  }

  // Constructor 1 args para O1,O4
  public APPOfflineMensaje(APPOfflinePrimitive p, String arg1)
      throws MalMensajeProtocoloException {
    if (p == APPOfflinePrimitive.O1 || p == APPOfflinePrimitive.O4) {
      this.arg1 = arg1;
      this.arg2 = null;
    } else
      throw new MalMensajeProtocoloException();
    this.primitiva = p;
  }

  // Constructor 2 args para O2, O3, O5, O6, O7, O8
  public APPOfflineMensaje(APPOfflinePrimitive p, String arg1, String arg2)
      throws MalMensajeProtocoloException {
    if (p == APPOfflinePrimitive.O2 || p == APPOfflinePrimitive.O3 ||
        p == APPOfflinePrimitive.O5 || p == APPOfflinePrimitive.O6 ||
        p == APPOfflinePrimitive.O7 || p == APPOfflinePrimitive.O8) {
      this.arg1 = arg1;
      this.arg2 = arg2;
    } else
      throw new MalMensajeProtocoloException();
    this.primitiva = p;
  }

  public APPOfflinePrimitive getPrimitiva() {
    return this.primitiva;
  }

  public String getArg1() {
    return this.arg1;
  }

  public String getArg2() {
    return this.arg2;
  }

  @Override
  public String toString() {
    switch (this.primitiva) {
      case O1:
        return "saludo (P->S) " + this.arg1;
      case O2:
        return "saludo (S->P) " + this.arg1 + ":" + this.arg2;
      case O3:
        return "saludo_ok (P->S) " + this.arg1 + ":" + this.arg2;
      case O4:
        return "saludo_ok (S->P) " + this.arg1;
      case O5:
        return "ingreso (P->S) " + this.arg1 + "\n" + this.arg2;
      case O6:
        return "recibo (P->S) " + this.arg1 + "\n" + this.arg2;
      case O7:
        return "factura (P->S) " + this.arg1 + "\n" + this.arg2;
      case O8:
        return "pago (P->S) " + this.arg1 + "\n" + this.arg2;
      case O9:
        return "final (P->S)";
      case O10:
        return "final_ok (S->P)";
      default:
        return "Codigo de primitiva no encontrado.";
    }
  }
}