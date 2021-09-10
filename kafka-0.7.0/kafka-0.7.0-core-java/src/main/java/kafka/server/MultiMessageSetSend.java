package kafka.server;

import kafka.network.ByteBufferSend;
import kafka.network.*;

import java.nio.ByteBuffer;
import java.util.List;

public class MultiMessageSetSend extends MultiSend {
    
    private int expectedBytesToWrite;
    private ByteBuffer buffer;
    private int allMessageSetSize;
    
    public MultiMessageSetSend(List<Send> sets) {
        super(sets);
        ByteBufferSend bufferSend = (ByteBufferSend) this.current.get(0); //获取第一个元素
//        ByteBufferSend bufferSend =  new ByteBufferSend(6);
        this.buffer = bufferSend.buffer;
        
        for (int i = 1; i < sets.size(); i++) {
            MessageSetSend byteBufferSend = (MessageSetSend) sets.get(i);
            this.allMessageSetSize += byteBufferSend.sendSize();
        }
        System.out.println("---------------------------MultiMessageSetSend-----------allMessageSetSize: " + allMessageSetSize);
        this.expectedBytesToWrite = 4 + 2 + allMessageSetSize;
        this.buffer.putInt(2 + allMessageSetSize);
        this.buffer.putShort((short) 0);
        this.buffer.rewind();
    }
    
    public int getExpectedBytesToWrite() {
        return expectedBytesToWrite;
    }
}
