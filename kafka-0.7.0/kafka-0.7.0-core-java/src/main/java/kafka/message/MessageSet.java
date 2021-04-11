package kafka.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

public abstract class MessageSet implements Iterable<MessageAndOffset> {
    
    static int LogOverhead = 4;
    public static MessageSet Empty;
    
    static {
        try {
            Empty = new ByteBufferMessageSet(ByteBuffer.allocate(0));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
    
    public MessageSet() throws Throwable {
    }
    
    static int entrySize(Message message) {
        return LogOverhead + message.size;
    }
    
    static int messageSetSize(Iterable<Message> messages) {
        int size = 0;
        Iterator<Message> it = messages.iterator();
        while (it.hasNext()) {
            Message message = it.next();
            int msgSize = entrySize(message);
            size = size + msgSize;
        }
        return size;
    }
    
    int messageSetSize(List<Message> messages) {
        int size = 0;
        Iterator<Message> iter = messages.iterator();
        while (iter.hasNext()) {
            Message message = iter.next();
            size += entrySize(message);
        }
        return size;
    }
    
    
    static ByteBuffer createByteBuffer(CompressionCodec compressionCodec, Iterable<Message> messages) throws IOException {
        if (compressionCodec instanceof NoCompressionCodec) {
            ByteBuffer buffer = ByteBuffer.allocate(MessageSet.messageSetSize(messages));
            Iterator<Message> iterator = messages.iterator();
            while (iterator.hasNext()) {
                Message message = iterator.next();
                message.serializeTo(buffer);
            }
            buffer.rewind();
            return buffer;
        } else {
            int size = 0;//
            Iterator it = messages.iterator();
            while (it.hasNext()) {
                it.next();
                size++;
            }
            
            if (size == 0) {
                ByteBuffer buffer = ByteBuffer.allocate(MessageSet.messageSetSize(messages));
                buffer.rewind();
                return buffer;
            } else {
                Message message = CompressionUtils.compress(messages, compressionCodec);
                ByteBuffer buffer = ByteBuffer.allocate(message.serializedSize);
                message.serializeTo(buffer);
                buffer.rewind();
                return buffer;
            }
        }
    }
    
    
    @Override
    public Iterator<MessageAndOffset> iterator() {
        return null;
    }
    
    @Override
    public void forEach(Consumer<? super MessageAndOffset> action) {
    
    }
    
    @Override
    public Spliterator<MessageAndOffset> spliterator() {
        return null;
    }
    
    
    public abstract Long sizeInBytes();
    
    public void validate() {
        for (MessageAndOffset messageAndOffset : this)
            if (!messageAndOffset.message.isValid()) {
                throw new InvalidMessageException();
            }
    }
    
    public abstract Long writeTo(WritableByteChannel channel, Long offset, Long maxSize) throws IOException;
    
}
