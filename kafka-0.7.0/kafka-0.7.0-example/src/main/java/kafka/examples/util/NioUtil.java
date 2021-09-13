package kafka.examples.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class NioUtil {
    
    
    /**
     * 需要处理两种情况：
     * 1： 发送的数据大小，超过你一次性分配的缓冲区
     * 2： 发送的数据大小，小于等于你申请的内存缓冲区
     * @param socketChannel
     * @return
     */
    public static ByteBuffer getDataByteBuffer(SocketChannel socketChannel) {
        int memMax = 10;
        List<ByteBuffer> byteBufferList = new ArrayList<>();
        int length = 0;
        while (true) {
            try {
                ByteBuffer byteBuffer = ByteBuffer.allocate(memMax);
                int readCount = socketChannel.read(byteBuffer);
                if (readCount <= 0) {
                    break;
                } else {
                    length += readCount;
                    byteBuffer.flip();
                    byteBufferList.add(byteBuffer);
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    socketChannel.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                break;
            }
        }
        
        ByteBuffer byteBuffer = ByteBuffer.allocate(length); // 一次性申请缓冲区
        for (ByteBuffer byteBuffer1 : byteBufferList) {
            byte[] dst = new byte[byteBuffer1.limit()];
            byteBuffer1.get(dst);// 只获取每个buffer的limit的数据长度
            byteBuffer.put(dst);
        }
        return byteBuffer;
    }
    
}
