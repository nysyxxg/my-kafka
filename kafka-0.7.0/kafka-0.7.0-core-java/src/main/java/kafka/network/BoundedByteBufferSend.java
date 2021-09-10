package kafka.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class BoundedByteBufferSend extends Send {
    private Boolean complete = false;
    private ByteBuffer sizeBuffer;
    public ByteBuffer buffer;
    
    public BoundedByteBufferSend(int size) {
        this(ByteBuffer.allocate(size));
    }
    
    public BoundedByteBufferSend(ByteBuffer buffer) {
        this.buffer = buffer;
        this.sizeBuffer = ByteBuffer.allocate(4);
        this.sizeBuffer.putInt(buffer.limit()); //  将数据的大小，放入缓冲区
        this.sizeBuffer.rewind();
    }
    
    public BoundedByteBufferSend(Request request) {
        this(request.sizeInBytes() + 2);
        System.out.println(this.buffer + "-----------BoundedByteBufferSend----------request.id------------>" + request.id);
        this.buffer.putShort(request.id);
        request.writeTo(buffer);
        this.buffer.rewind();
    }
    
    
    @Override
    public int writeTo(WritableByteChannel channel) throws IOException {
        expectIncomplete();
        int written = 0;
        // try to write the size if we haven't already
        if (sizeBuffer.hasRemaining()) {
            written += channel.write(sizeBuffer);
        }
        // try to write the actual buffer itself
        if (!sizeBuffer.hasRemaining() && buffer.hasRemaining()) {
            written += channel.write(buffer);
        }
        // if we are done, mark it off
        if (!buffer.hasRemaining()) {
            complete = true;
        }
        return written;
    }
    
    public Boolean getComplete() {
        return complete;
    }
    
    public void setComplete(Boolean complete) {
        this.complete = complete;
    }
    
    public ByteBuffer getSizeBuffer() {
        return sizeBuffer;
    }
    
    public void setSizeBuffer(ByteBuffer sizeBuffer) {
        this.sizeBuffer = sizeBuffer;
    }
    
    public ByteBuffer getBuffer() {
        return buffer;
    }
    
    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }
    
    @Override
    public Boolean complete() {
        return complete;
    }
}
