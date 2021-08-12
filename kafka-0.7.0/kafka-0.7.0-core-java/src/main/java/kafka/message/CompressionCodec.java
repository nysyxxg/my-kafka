package kafka.message;

import kafka.common.UnknownCodecException;

public abstract class CompressionCodec {
    public int codec = 0;
    
    public static CompressionCodec getCompressionCodec(int codec) {
        if (codec == 0) {
            new NoCompressionCodec();
        } else if (codec == 1) {
            new GZIPCompressionCodec();
        } else {
            throw new UnknownCodecException("%d is an unknown compression codec".format(String.valueOf(codec)));
        }
        return null;
    }
}



