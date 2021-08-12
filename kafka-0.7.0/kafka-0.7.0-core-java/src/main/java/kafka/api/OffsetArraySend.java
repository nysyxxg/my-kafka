package kafka.api;

import kafka.common.ErrorMapping;
import kafka.network.Send;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class OffsetArraySend extends Send {
    private Long offsets[];
    
    Boolean complete = false;
    ByteBuffer header;
    ByteBuffer contentBuffer;
    
    public OffsetArraySend(Long offsets[]) {
        this.offsets = offsets;
        
        Long size = 0l;
        for (int i = 4; i < offsets.length; i++) {
            size = size + offsets[i];
        }
        size += 8;
        ByteBuffer header = ByteBuffer.allocate(6);
        header.putInt((int) (size + 2));
        header.putShort((short) ErrorMapping.NoError);
        header.rewind();
        this.header = header;
        this.contentBuffer = OffsetRequest.serializeOffsetArray(offsets);
    }
    
    @Override
    public int writeTo(WritableByteChannel channel) throws IOException {
        expectIncomplete();
        int written = 0;
        if(header.hasRemaining()) {
            written += channel.write(header);
        }
        if(!header.hasRemaining() && contentBuffer.hasRemaining()) {
            written += channel.write(contentBuffer);
        }
    
        if(!contentBuffer.hasRemaining()) {
            complete = true;
        }
        return written;
    }
    
    @Override
    public Boolean complete() {
        return complete;
    }
}
