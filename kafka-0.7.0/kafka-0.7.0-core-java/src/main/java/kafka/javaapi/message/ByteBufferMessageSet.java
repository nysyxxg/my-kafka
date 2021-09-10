package kafka.javaapi.message;

import kafka.common.ErrorMapping;
import kafka.message.CompressionCodec;
import kafka.message.Message;
import kafka.message.MessageAndOffset;
import kafka.message.NoCompressionCodec;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.Iterator;

public class ByteBufferMessageSet extends MessageSet {
    
    private Logger logger = Logger.getLogger(kafka.message.ByteBufferMessageSet.class);
    
    private ByteBuffer buffer;
    private Long initialOffset = 0L;
    private int errorCode;
    
    private kafka.message.ByteBufferMessageSet underlying;
    
    public ByteBufferMessageSet(ByteBuffer buffer,
                                Long initialOffset,
                                int errorCode) throws Throwable {
        this.buffer = buffer;
        this.initialOffset = initialOffset;
        this.errorCode = errorCode;
        this.underlying = new kafka.message.ByteBufferMessageSet(buffer, initialOffset, errorCode);
    }
    
    public ByteBufferMessageSet(ByteBuffer buffer) throws Throwable {
        this(buffer, 0L, ErrorMapping.NoError);
    }
    
    public ByteBufferMessageSet(CompressionCodec compressionCodec, java.util.List<Message> messages) throws Throwable {
        this(kafka.message.MessageSet.createByteBuffer(compressionCodec, messages), 0L, ErrorMapping.NoError);
    }
    
    public ByteBufferMessageSet(java.util.List<Message> messages) throws Throwable {
        this(new NoCompressionCodec(), messages);
    }
    
    public Long validBytes() {
        return underlying.validBytes;
    }
    
    public ByteBuffer serialized() {
        return underlying.serialized();
    }
    
    public Long getInitialOffset() {
        return initialOffset;
    }
    
    public ByteBuffer getBuffer() {
        return buffer;
    }
    
    public int getErrorCode() {
        return errorCode;
    }
    
    public kafka.message.ByteBufferMessageSet getUnderlying() {
        return underlying;
    }
    
    public String toString() {
        return underlying.toString();
    }
    
    @Override
    public java.util.Iterator<MessageAndOffset> iterator() {
        return new java.util.Iterator<MessageAndOffset>() {
            Iterator<MessageAndOffset> underlyingIterator = underlying.iterator();
            
            public boolean hasNext() {
                return underlyingIterator.hasNext();
            }
            
            public MessageAndOffset next() {
                return underlyingIterator.next();
            }
            
            public void remove() {
                throw new UnsupportedOperationException("remove API on MessageSet is not supported");
            }
        };
    }
    
    public Long sizeInBytes() {
        return underlying.sizeInBytes();
    }
    
    
    public boolean equals(Object other) {
        if (other instanceof kafka.message.ByteBufferMessageSet) {
            ByteBufferMessageSet that = (ByteBufferMessageSet) other;
            return (that.canEqual(this) && errorCode == that.errorCode && buffer.equals(that.buffer) && initialOffset == that.initialOffset);
        } else {
            return false;
        }
    }
    
    
    public Boolean canEqual(Object other) {
        if (other instanceof kafka.message.ByteBufferMessageSet) {
            return true;
        }
        return false;
    }
    
    public int hashCode() {
        return 31 + (17 * errorCode) + buffer.hashCode() + initialOffset.hashCode();
    }
}
