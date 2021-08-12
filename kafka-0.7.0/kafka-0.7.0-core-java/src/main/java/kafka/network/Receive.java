package kafka.network;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public abstract class Receive extends Transmission {
    
    public  abstract ByteBuffer buffer();
    
    public abstract int readFrom(ReadableByteChannel channel);
    
    public int readCompletely(ReadableByteChannel channel) {
        int read = 0;
        while (!complete()) {
            read = readFrom(channel);
            if (logger.isTraceEnabled()) {
                logger.trace(read + " bytes read.");
            }
        }
        return read;
    }
    
}
