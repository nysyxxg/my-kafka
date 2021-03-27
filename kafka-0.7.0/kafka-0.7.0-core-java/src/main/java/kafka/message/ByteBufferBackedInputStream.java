package kafka.message;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ByteBufferBackedInputStream extends InputStream {
    private ByteBuffer buffer;
    
    public ByteBufferBackedInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }
    
    @Override
    public int read() throws IOException {
        boolean bl = buffer.hasRemaining();
        if (bl) {
            return (buffer.get() & 0xFF);
        } else {
            return -1;
        }
    }
    
    @Override
    public int read(byte bytes[], int off, int len) {
        boolean bl = buffer.hasRemaining();
        if (bl) { // Read only what's left
            int realLen = Math.min(len, buffer.remaining());
            buffer.get(bytes, off, realLen); // 从buffer中读取多长的数据
            return realLen;
        } else {
            return -1;
        }
    }
    
}
