package kafka.message;

import kafka.common.UnknownCodecException;

public abstract class CompressionCodec {
    public static int codec = 0;
    
    public static CompressionCodec getCompressionCodec(int codec) {
        if (codec == 0) {
            return   new NoCompressionCodec();
        } else if (codec == 1) {
            return   new GZIPCompressionCodec();
        } else {
            throw new UnknownCodecException("%d is an unknown compression codec".format(String.valueOf(codec)));
        }
     }
}



