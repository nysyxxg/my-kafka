package kafka.network;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class ByteBufferSend extends Send {
    
    public Boolean complete = false;
    
    public ByteBuffer buffer;
    
    public ByteBufferSend(int size) {
        this(ByteBuffer.allocate(size));
    }
    
    public ByteBufferSend(ByteBuffer buffer) {
        this.buffer = buffer;
    }
    
    
    @Override
    public int writeTo(WritableByteChannel channel) throws IOException {
        expectIncomplete();
        int written = 0;
        written += channel.write(buffer);
        if (!buffer.hasRemaining()) {
            complete = true;
        }
        return written;
    }
    
}
