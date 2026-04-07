package eurocoin.common;

public class APPBancoMensaje implements java.io.Serializable {

    private final APPBancoPrimitive primitiva;
    private final String arg1;
    private final String arg2;
    private final String arg3;

    // Constructor 0 args
    public APPBancoMensaje(APPBancoPrimitive p) throws MalMensajeProtocoloException {
        if (p == APPBancoPrimitive.A3 || p == APPBancoPrimitive.A5 ||
                p == APPBancoPrimitive.A6 || p == APPBancoPrimitive.A7 ||
                p == APPBancoPrimitive.A8) {

            this.primitiva = p;
            this.arg1 = this.arg2 = this.arg3 = null;

        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    // Constructor 1 arg
    public APPBancoMensaje(APPBancoPrimitive p, String arg1)
            throws MalMensajeProtocoloException {

        if (p == APPBancoPrimitive.A1 || p == APPBancoPrimitive.A4) {
            this.primitiva = p;
            this.arg1 = arg1;
            this.arg2 = this.arg3 = null;
        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    // Constructor 2 args
    public APPBancoMensaje(APPBancoPrimitive p, String arg1, String arg2)
            throws MalMensajeProtocoloException {

        if (p == APPBancoPrimitive.A9 || p == APPBancoPrimitive.A10 ||
                p == APPBancoPrimitive.A11 || p == APPBancoPrimitive.A12) {

            this.primitiva = p;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = null;

        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    // Constructor 3 args
    public APPBancoMensaje(APPBancoPrimitive p, String arg1, String arg2, String arg3)
            throws MalMensajeProtocoloException {

        if (p == APPBancoPrimitive.A2) {
            this.primitiva = p;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        } else {
            throw new MalMensajeProtocoloException();
        }
    }

    public APPBancoPrimitive getPrimitiva() {
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