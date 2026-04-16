package eurocoin.common;

public class APPBancoMensaje implements java.io.Serializable {

    private final APPBancoPrimitive primitiva;
    private final String arg1;
    private final String arg2;
    private final String arg3;

    // Constructor 0 args para A3, A5, A6, A7, A8
    public APPBancoMensaje(APPBancoPrimitive p) throws MalMensajeProtocoloException {
        if (p == APPBancoPrimitive.A3 || p == APPBancoPrimitive.A5 ||
            p == APPBancoPrimitive.A6 || p == APPBancoPrimitive.A7 ||
            p == APPBancoPrimitive.A8) {

            this.primitiva = p;
            this.arg1 = null;
            this.arg2 = null;
            this.arg3 = null;
        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    // Constructor 1 arg para A1, A4
    public APPBancoMensaje(APPBancoPrimitive p, String arg1)
            throws MalMensajeProtocoloException {

        if (p == APPBancoPrimitive.A1 || p == APPBancoPrimitive.A4) {
            this.primitiva = p;
            this.arg1 = new String(arg1);
            this.arg2 = null;
            this.arg3 = null;
        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    // Constructor 2 args para A9, A10, A11, A12
    public APPBancoMensaje(APPBancoPrimitive p, String arg1, String arg2)
            throws MalMensajeProtocoloException {

        if (p == APPBancoPrimitive.A9 || p == APPBancoPrimitive.A10 ||
            p == APPBancoPrimitive.A11 || p == APPBancoPrimitive.A12) {

            this.primitiva = p;
            this.arg1 = new String(arg1);
            this.arg2 = new String(arg2);
            this.arg3 = null;
        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    // Constructor 3 args para A2
    public APPBancoMensaje(APPBancoPrimitive p, String arg1, String arg2, String arg3)
            throws MalMensajeProtocoloException {

        if (p == APPBancoPrimitive.A2) {
            this.primitiva = p;
            this.arg1 = new String(arg1);
            this.arg2 = new String(arg2);
            this.arg3 = new String(arg3);
        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    public APPBancoPrimitive getPrimitiva() {
        return this.primitiva;
    }

    public String getArg1() {
        return this.arg1;
    }

    public String getArg2() {
        return this.arg2;
    }

    public String getArg3() {
        return this.arg3;
    }

    @Override
    public String toString() {
        switch (this.primitiva) {
            case A1:
                return "saludo (B->A) " + this.arg1;
            case A2:
                return "saludo (A->B) " + this.arg1 + ":" + this.arg2 + ":" + this.arg3;
            case A3:
                return "saludo_err (B->A)";
            case A4:
                return "saludo_ok (B->A) " + this.arg1;
            case A5:
                return "saludo_ok (A->B)";
            case A6:
                return "saludo_err (A->B)";
            case A7:
                return "final (A->B)";
            case A8:
                return "final_ok (B->A)";
            case A9:
                return "recarga (A->B) " + this.arg1 + "\n\tMAC " + this.arg2;
            case A10:
                return "recibo-recarga (B->A) " + this.arg1 + "\n\tMAC " + this.arg2;
            case A11:
                return "descarga (A->B) " + this.arg1 + "\n\tMAC " + this.arg2;
            case A12:
                return "recibo-descarga (B->A) " + this.arg1 + "\n\tMAC " + this.arg2;
            default:
                return "Codigo de primitiva no encontrado.";
        }
    }
}