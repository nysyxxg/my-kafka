package kafka.network;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.List;

public abstract class MultiSend<S> extends Send { // S 泛型必须是Send的子类
    public List<S> sends;
    
    public List<S> current;
    public int totalWritten = 0;
    
    public MultiSend(List<S> sends) {
        this.sends = sends;
        this.current = sends;
    }
    
    protected MultiSend() {
    }
    
    public abstract int getExpectedBytesToWrite();
    
    
    public int writeTo(WritableByteChannel channel) throws IOException {
        expectIncomplete();
        Send send = (Send) current.get(0);
        int written = send.writeTo(channel);
        totalWritten += written;
        if (send.complete()) {
            current.remove(0);
        }
        return written;
    }
    
    @Override
    public Boolean complete() {
        if (current == null) {
            if (totalWritten != getExpectedBytesToWrite())
                logger.error("mismatch in sending bytes over socket; expected: " + getExpectedBytesToWrite() + " actual: " + totalWritten);
            return true;
        } else
            return false;
    }
    
}
