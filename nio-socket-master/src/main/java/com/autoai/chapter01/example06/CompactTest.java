package com.autoai.chapter01.example06;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class CompactTest {

    public static void main(String[] args) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        byteBuffer.put((byte)1);
        byteBuffer.put((byte)2);
        byteBuffer.put((byte)3);
        byteBuffer.put((byte)4);
        byteBuffer.put((byte)5);
        byteBuffer.put((byte)6);
        byteBuffer.put((byte)7);
        // 1 2 3 4 5 6 7 0 0 0
        byteBuffer.position(2);
        // 3 4 5 6 7 0 0 0 0 0
        byteBuffer.compact();
        byteBuffer.clear();

        for (int i = 0; i < byteBuffer.capacity(); i++) {
            System.out.print(byteBuffer.get());
        }
    }
}
