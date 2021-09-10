package kafka.server;

import kafka.common.ErrorMapping;
import kafka.message.MessageSet;
import kafka.network.Send;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class MessageSetSend extends Send {
    private Long sent = 0L;
    private Boolean complete = false;
    private MessageSet messages;
    private Integer errorCode;
    public long size;
    private ByteBuffer header;
    
    public MessageSetSend(MessageSet messages, Integer errorCode) {
        this.messages = messages;
        this.errorCode = errorCode;
        this.size = messages.sizeInBytes();
        
        this.header = ByteBuffer.allocate(6);
        this.header.putInt(Integer.valueOf(String.valueOf(size)) + 2);
        this.header.putShort(Short.parseShort(errorCode.toString()));
        this.header.rewind();
    }
    
    
    public MessageSetSend(MessageSet messages) {
        this(messages, ErrorMapping.NoError);
    }
    
    public int sendSize() {
        return (int) (size + header.capacity());
    }
    
    @Override
    public int writeTo(WritableByteChannel channel) throws IOException {
        expectIncomplete();
        int written = 0;
        if (header.hasRemaining())
            written += channel.write(header);
        if (!header.hasRemaining()) {
            Long fileBytesSent = messages.writeTo(channel, sent, size - sent);
            written += fileBytesSent;
            sent += fileBytesSent;
        }
        
        if (logger.isTraceEnabled())
            if (channel instanceof SocketChannel) {
                SocketChannel socketChannel = (SocketChannel) channel;
                logger.trace(sent + " bytes written to " + socketChannel.socket().getRemoteSocketAddress() + " expecting to send " + size + " bytes");
            }
        
        if (sent >= size) {
            complete = true;
        }
        return written;
    }
    
    @Override
    public Boolean complete() {
        return complete;
    }
}
