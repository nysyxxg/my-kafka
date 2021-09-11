package com.autoai.chapter01.example03;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class ByteBufferArray {

    public static void main(String[] args) {

        byte[] b1 = new byte[]{'a','b','c'};
        byte[] b2 = new byte[]{'z','k','s'};
        byte[] b3 = new byte[]{'h','a','h','a'};
        ByteBuffer byteBuffer1 = ByteBuffer.wrap(b1);
        ByteBuffer byteBuffer2 = ByteBuffer.wrap(b2);
        ByteBuffer byteBuffer3 = ByteBuffer.wrap(b3);
        List<ByteBuffer> list = new ArrayList<>(3);
        list.add(byteBuffer1);
        list.add(byteBuffer2);
        list.add(byteBuffer3);
        ByteBuffer[] byteBuffers = new ByteBuffer[list.size()];
        list.toArray(byteBuffers);
        for (ByteBuffer byteBuffer : byteBuffers) {
            while (byteBuffer.hasRemaining()) {
                System.out.print((char) byteBuffer.get());
            }
            System.out.println();
        }
    }
}
