package com.autoai.chapter01.example08;

import java.nio.ByteBuffer;

/**
 * @Author:
 * @Date:
 * @Description:
 */
public class ExtendSizeTest {

    public static void main(String[] args) {
        byte[] bytes = new byte[]{1,2,3};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        ByteBuffer buffer = ExtendSizeTest.extendSize(byteBuffer, 2);
        buffer.rewind();
        while (buffer.hasRemaining()) {
            System.out.print(buffer.get());
        }
    }

    /**
     * 手动扩容
     * @param byteBuffer
     * @param size
     * @return
     */
    public static ByteBuffer extendSize(ByteBuffer byteBuffer, int size) {
        ByteBuffer buffer = ByteBuffer.allocate(byteBuffer.capacity() + size);
        buffer.put(byteBuffer);
        return buffer;
    }
}
