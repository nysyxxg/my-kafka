package kafka.network;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

public abstract class Send extends Transmission {
    
    public abstract int writeTo(WritableByteChannel channel) throws IOException;
    
    public int writeCompletely(WritableByteChannel channel) throws IOException {
        int written = 0;
        while (!complete()){
            written = writeTo(channel);
            if (logger.isTraceEnabled()) {
                logger.trace(written + " bytes written.");
            }
        }
        return written;
    }
    
}
