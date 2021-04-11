package kafka.network;

import kafka.common.InvalidRequestException;
import kafka.utils.Utils;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class BoundedByteBufferReceive extends Receive {
    
    Boolean complete = false;
    private ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
    private ByteBuffer contentBuffer = null;
    private int maxSize;
    
    public BoundedByteBufferReceive() {
        this(Integer.MAX_VALUE);
    }
    
    BoundedByteBufferReceive(int maxSize) {
        this.maxSize = maxSize;
    }
    
    @Override
    public ByteBuffer buffer() {
        expectComplete();
        return contentBuffer;
    }
    
    
    @Override
    public int readFrom(ReadableByteChannel channel) {
        expectIncomplete();
        int read = 0;
        // have we read the request size yet?
        if (sizeBuffer.remaining() > 0)
            read += Utils.read(channel, sizeBuffer);
        // have we allocated the request buffer yet?
        if (contentBuffer == null && !sizeBuffer.hasRemaining()) {
            sizeBuffer.rewind();
            int size = sizeBuffer.getInt();
            if (size <= 0)
                throw new InvalidRequestException("%d is not a valid request size.".format(String.valueOf(size)));
            if (size > maxSize)
                throw new InvalidRequestException("Request of length %d is not valid, it is larger than the maximum size of %d bytes.".format(String.valueOf(size), maxSize));
            contentBuffer = byteBufferAllocate(size);
        }
        // if we have a buffer read some stuff into it
        if (contentBuffer != null) {
            read = Utils.read(channel, contentBuffer);
            // did we get everything?
            if (!contentBuffer.hasRemaining()) {
                contentBuffer.rewind();
                complete = true;
            }
        }
        return read;
    }
    
    private ByteBuffer byteBufferAllocate(int size) {
        ByteBuffer buffer = null;
        try {
            buffer = ByteBuffer.allocate(size);
        } catch (OutOfMemoryError e) {
            throw new RuntimeException("OOME with size " + size, e);
        }
        return buffer;
    }
}
