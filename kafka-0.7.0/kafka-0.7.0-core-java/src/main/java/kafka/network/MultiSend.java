package kafka.network;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.List;

public abstract class MultiSend extends Send { // S 泛型必须是Send的子类
    
    public List<Send> current;
    public int totalWritten = 0;
    
    public MultiSend(List<Send> sends) {
        this.current = sends;
        this.current.add(0, new ByteBufferSend(6)); // 将对象加入到头部
    }
    
    protected MultiSend() {
    }
    
    public abstract int getExpectedBytesToWrite();
    
    
    public int writeTo(WritableByteChannel channel) throws IOException {
        expectIncomplete();
        Send send = this.current.get(0);//head 获取第一个元素
        System.out.println("----------------------获取集合中头部元素：send = " + send);
//        ByteBufferSend  send = new ByteBufferSend(6);
        int written = send.writeTo(channel);
        totalWritten += written;
        if (send.complete()) {
            current.remove(0);
        }
        return written;
    }
    
    @Override
    public Boolean complete() {
        if (current.size() == 0 ) {
            if (totalWritten != getExpectedBytesToWrite()) {
                logger.error("mismatch in sending bytes over socket; expected: " + getExpectedBytesToWrite() + " actual: " + totalWritten);
            }
            return true;
        } else
            return false;
    }
    
}
