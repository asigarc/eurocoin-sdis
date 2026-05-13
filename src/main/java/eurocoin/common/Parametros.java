package eurocoin.common;
public class Parametros implements java.io.Serializable {
    
    public final Integer lowWatermark;
    public final Integer highWatermark;
    public final Integer fondosEC;
    
    public Parametros(
        Integer lowWatermark,
        Integer highWatermark,
        Integer fondosEC) {

    this.lowWatermark = lowWatermark;
    this.highWatermark = highWatermark;
    this.fondosEC = fondosEC;
}
}