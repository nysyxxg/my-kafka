package kafka.network;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.List;

public abstract class MultiSend<S> extends Send { // S 泛型必须是Send的子类
    public List<S> sends;
    
    public int expectedBytesToWrite;
    public List<S> current;
    public int totalWritten = 0;
    
    public MultiSend(List<S> sends) {
        this.sends = sends;
        this.current = sends;
    }
    
    protected MultiSend() {
    }
    
    public int writeTo(WritableByteChannel channel) throws IOException {
        expectIncomplete();
        Send send = (Send) current.get(0);
        int written = send.writeTo(channel);
        totalWritten += written;
        if (send.complete) {
            current.remove(0);
        }
        return written;
    }
    
    public Boolean complete() {
        if (current == null) {
            if (totalWritten != expectedBytesToWrite)
                logger.error("mismatch in sending bytes over socket; expected: " + expectedBytesToWrite + " actual: " + totalWritten);
            return true;
        } else
            return false;
    }
    
}
