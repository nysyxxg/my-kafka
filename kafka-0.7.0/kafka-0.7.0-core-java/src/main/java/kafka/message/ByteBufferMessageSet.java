package kafka.message;

import kafka.common.ErrorMapping;
import kafka.common.InvalidMessageSizeException;
import kafka.utils.IteratorTemplate;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;

public class ByteBufferMessageSet extends MessageSet {
    
    private Logger logger = Logger.getLogger(ByteBufferMessageSet.class);
    private Long validByteCount = -1L;
    private Long shallowValidByteCount = -1L;
    
    private ByteBuffer buffer;
    private Long initialOffset = 0L;
    private int errorCode = ErrorMapping.NoError;
    public Long validBytes;
    
    public ByteBufferMessageSet(ByteBuffer buffer) throws Throwable {
        this.buffer = buffer;
    }
    
    public ByteBufferMessageSet(ByteBuffer buffer,
                                Long initialOffset,
                                int errorCode) throws Throwable {
        this.buffer = buffer;
        this.initialOffset = initialOffset;
        this.errorCode = errorCode;
        this.validBytes = shallowValidBytes();
    }
    
    
    public ByteBufferMessageSet(CompressionCodec compressionCodec, Iterable<Message> messages) throws Throwable {
        this(MessageSet.createByteBuffer(compressionCodec, messages), 0L, ErrorMapping.NoError);
    }
    
    public ByteBufferMessageSet(Iterable<Message> messages) throws Throwable {
        this(new NoCompressionCodec(), messages);
    }
    
    
    public ByteBuffer serialized() {
        return buffer;
    }
    
    
    private Long shallowValidBytes() throws Throwable {
        if (shallowValidByteCount < 0) {
            Iterator<MessageAndOffset> iter = deepIterator();
            while (iter.hasNext()) {
                MessageAndOffset messageAndOffset = iter.next();
                shallowValidByteCount = messageAndOffset.offset;
            }
        }
        if (shallowValidByteCount < initialOffset) {
            return Long.valueOf(0);
        } else {
            return (shallowValidByteCount - initialOffset);
        }
    }
    
    
    public Long getValidByteCount() {
        return validByteCount;
    }
    
    public void setValidByteCount(Long validByteCount) {
        this.validByteCount = validByteCount;
    }
    
    public Long getShallowValidByteCount() {
        return shallowValidByteCount;
    }
    
    public void setShallowValidByteCount(Long shallowValidByteCount) {
        this.shallowValidByteCount = shallowValidByteCount;
    }
    
    public ByteBuffer getBuffer() {
        return buffer;
    }
    
    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }
    
    public Long getInitialOffset() {
        return initialOffset;
    }
    
    public void setInitialOffset(Long initialOffset) {
        this.initialOffset = initialOffset;
    }
    
    public int getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }
    
    @Override
    public Long writeTo(WritableByteChannel channel, Long offset, Long maxSize) throws IOException {
        return Long.valueOf(channel.write(buffer.duplicate()));
    }
    
    
    private Iterator<MessageAndOffset> deepIterator() throws Throwable {
        ErrorMapping.maybeThrowException(errorCode);
        
        return new IteratorTemplate<MessageAndOffset>() {
            ByteBuffer topIter = buffer.slice();
            Long currValidBytes = initialOffset;
            Iterator<MessageAndOffset> innerIter = null;
            Long lastMessageSize = 0L;
            
            Boolean innerDone() {
                return (innerIter == null || !innerIter.hasNext());
            }
            
            MessageAndOffset makeNextOuter() throws Throwable {
                if (topIter.remaining() < 4) {
                    return allDone();
                }
                int size = topIter.getInt();
                lastMessageSize = Long.parseLong(size + "");
                
                if (logger.isTraceEnabled()) {
                    logger.trace("Remaining bytes in iterator = " + topIter.remaining());
                    logger.trace("size of data = " + size);
                }
                if (size < 0 || topIter.remaining() < size) {
                    if (currValidBytes == initialOffset || size < 0)
                        throw new InvalidMessageSizeException("invalid message size: " + size + " only received bytes: " +
                                topIter.remaining() + " at " + currValidBytes + "( possible causes (1) a single message larger than " +
                                "the fetch size; (2) log corruption )");
                    return allDone();
                }
                ByteBuffer message = topIter.slice();
                message.limit(size);
                topIter.position(topIter.position() + size);
                Message newMessage = new Message(message);
                CompressionCodec compressionCodec = newMessage.compressionCodec();
                if (compressionCodec instanceof NoCompressionCodec) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Message is uncompressed. Valid byte count = %d".format(String.valueOf(currValidBytes)));
                    }
                    innerIter = null;
                    currValidBytes += 4 + size;
                    if (logger.isTraceEnabled())
                        logger.trace("currValidBytes = " + currValidBytes);
                    return new MessageAndOffset(newMessage, currValidBytes);
                } else {
                    if (logger.isDebugEnabled())
                        logger.debug("Message is compressed. Valid byte count = %d".format(String.valueOf(currValidBytes)));
                    innerIter = CompressionUtils.decompress(newMessage).deepIterator();
                    if (!innerIter.hasNext()) {
                        currValidBytes += 4 + lastMessageSize;
                        innerIter = null;
                    }
                    return makeNext();
                }
            }
            
            protected MessageAndOffset makeNext() throws Throwable {
                Boolean isInnerDone = innerDone();
                if (logger.isDebugEnabled()) {
                    logger.debug("makeNext() in deepIterator: innerDone = " + isInnerDone);
                }
                if (isInnerDone == true) {
                    return makeNextOuter();
                } else if (isInnerDone == false) {
                    MessageAndOffset messageAndOffset = innerIter.next();
                    if (!innerIter.hasNext()) {
                        currValidBytes += 4 + lastMessageSize;
                    }
                    return new MessageAndOffset(messageAndOffset.message, currValidBytes);
                }
                return null;
            }
        };
    }
    
    @Override
    public Iterator<MessageAndOffset> iterator() {
        System.out.println("--------------------------ByteBufferMessageSet-------------------iterator()---------");
        try {
            return deepIterator();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return null;
    }
    
    @Override
    public long sizeInBytes() {
        return Long.valueOf(buffer.limit());
    }
    
    
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ByteBufferMessageSet(");
        for (MessageAndOffset message : this) {
            builder.append(message);
            builder.append(", ");
        }
        builder.append(")");
        return builder.toString();
    }
    
    public boolean equals(Object other) {
        if (other instanceof ByteBufferMessageSet) {
            ByteBufferMessageSet that = (ByteBufferMessageSet) other;
            return ((that == this) && errorCode == that.errorCode && buffer.equals(that.buffer) && initialOffset == that.initialOffset);
        } else {
            return false;
        }
    }
    
    public Boolean canEqual(Object other) {
        if (other instanceof ByteBufferMessageSet) {
            return true;
        }
        return false;
    }
    
    public int hashCode() {
        return 31 + (17 * errorCode) + buffer.hashCode() + initialOffset.hashCode();
    }
    
    
}
