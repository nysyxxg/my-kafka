package kafka.server;

import kafka.network.ByteBufferSend;
import kafka.network.MultiSend;

import java.nio.ByteBuffer;
import java.util.List;

public class MultiMessageSetSend extends MultiSend {
    
    
    public MultiMessageSetSend(List<MessageSetSend> sets) {
        super(sets);
        ByteBufferSend  bufferSend = new ByteBufferSend(6);
        ByteBuffer buffer = bufferSend.buffer;
        
        int allMessageSetSize = 0;
        for(MessageSetSend byteBufferSend:sets){
            allMessageSetSize +=  byteBufferSend.sendSize()  + byteBufferSend.size;
        }
        
        Integer expectedBytesToWrite = 4 + 2 + allMessageSetSize;
        buffer.putInt(2 + allMessageSetSize);
        buffer.putShort((short) 0);
        buffer.rewind();
        
    }
}
