package kafka.message;

import clover.it.unimi.dsi.fastutil.bytes.AbstractByteList;
import kafka.common.InvalidMessageException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
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
    
    public static int entrySize(Message message) {
        return LogOverhead + message.size;
    }
    
    public static int messageSetSize(Iterable<Message> messages) {
        int size = 0;
        Iterator<Message> it = messages.iterator();
        while (it.hasNext()) {
            Message message = it.next();
            int msgSize = entrySize(message);
            size = size + msgSize;
        }
        return size;
    }
    
    public  static int messageSetSize(List<Message> messages) {
        int size = 0;
        Iterator<Message> iter = messages.iterator();
        while (iter.hasNext()) {
            Message message = iter.next();
            size += entrySize(message);
        }
        return size;
    }
    
    
    public static ByteBuffer createByteBuffer(CompressionCodec compressionCodec, Iterable<Message> messages) throws IOException {
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
            List<Message>  newMessages = new ArrayList<Message>();
            int size = 0;//
            Iterator<Message> it = messages.iterator();
            while (it.hasNext()) {
                newMessages.add(it.next());
                size++;
            }
            
            if (size == 0) {
                ByteBuffer buffer = ByteBuffer.allocate(MessageSet.messageSetSize(newMessages));
                buffer.rewind();
                return buffer;
            } else {
                Message message = CompressionUtils.compress(newMessages, compressionCodec);
                ByteBuffer buffer = ByteBuffer.allocate(message.serializedSize);
                message.serializeTo(buffer);
                buffer.rewind();
                return buffer;
            }
        }
    }
    
    public void validate() {
        for (MessageAndOffset messageAndOffset : this)
            if (!messageAndOffset.message.isValid()) {
                throw new InvalidMessageException();
            }
    }
    
    public abstract Long writeTo(WritableByteChannel channel, Long offset, Long maxSize) throws IOException;
    
    public abstract Iterator<MessageAndOffset> iterator();
    
    public abstract Long sizeInBytes();
}
