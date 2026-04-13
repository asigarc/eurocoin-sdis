package eurocoin.common;

public class APPBancoMensaje implements java.io.Serializable {

    private final APPBancoPrimitiva primitiva;
    private final String arg1;
    private final String arg2;
    private final String arg3;

    // Constructor 0 args
    public APPBancoMensaje(APPBancoPrimitiva p) throws MalMensajeProtocoloException {
        if (p == APPBancoPrimitiva.A3 || p == APPBancoPrimitiva.A5 ||
                p == APPBancoPrimitiva.A6 || p == APPBancoPrimitiva.A7 ||
                p == APPBancoPrimitiva.A8) {

            this.primitiva = p;
            this.arg1 = this.arg2 = this.arg3 = null;

        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    // Constructor 1 arg
    public APPBancoMensaje(APPBancoPrimitiva p, String arg1)
            throws MalMensajeProtocoloException {

        if (p == APPBancoPrimitiva.A1 || p == APPBancoPrimitiva.A4) {
            this.primitiva = p;
            this.arg1 = arg1;
            this.arg2 = this.arg3 = null;
        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    // Constructor 2 args
    public APPBancoMensaje(APPBancoPrimitiva p, String arg1, String arg2)
            throws MalMensajeProtocoloException {

        if (p == APPBancoPrimitiva.A9 || p == APPBancoPrimitiva.A10 ||
                p == APPBancoPrimitiva.A11 || p == APPBancoPrimitiva.A12) {

            this.primitiva = p;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = null;

        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    // Constructor 3 args
    public APPBancoMensaje(APPBancoPrimitiva p, String arg1, String arg2, String arg3)
            throws MalMensajeProtocoloException {

        if (p == APPBancoPrimitiva.A2) {
            this.primitiva = p;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    public APPBancoPrimitiva getPrimitiva() {
        return primitiva;
    }

    public String getArg1() {
        return arg1;
    }

    public String getArg2() {
        return arg2;
    }

    public String getArg3() {
        return arg3;
    }

    @Override
    public String toString() {
        switch (primitiva) {
            case A1:
                return "A1 " + arg1;
            case A2:
                return "A2 " + arg1 + " " + arg2 + " " + arg3;
            case A3:
                return "A3";
            case A4:
                return "A4 " + arg1;
            case A5:
                return "A5";
            case A6:
                return "A6";
            case A7:
                return "A7";
            case A8:
                return "A8";
            case A9:
                return "A9 " + arg1 + " " + arg2;
            case A10:
                return "A10 " + arg1 + " " + arg2;
            case A11:
                return "A11 " + arg1 + " " + arg2;
            case A12:
                return "A12 " + arg1 + " " + arg2;
            default:
                return "Error";
        }
    }
}