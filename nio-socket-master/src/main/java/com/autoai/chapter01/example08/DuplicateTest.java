package com.autoai.chapter01.example08;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/30 10:07
 * @Description:
 */
@Slf4j
public class DuplicateTest {

    @Test
    public void test1() {

        byte[] bytes = new byte[]{1,2,3,4,5,6};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.position(2);

        ByteBuffer byteBuffer1 = byteBuffer.slice();
        ByteBuffer byteBuffer2 = byteBuffer.duplicate();

        // 2 6 6
        log.info("byteBuffer position:{},limit:{},capacity:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity());
        // 1 2 3 4 5 6 -> 3 4 5 6
        // 0 4 4
        log.info("byteBuffer1 position:{},limit:{},capacity:{}", byteBuffer1.position(), byteBuffer1.limit(), byteBuffer1.capacity());
        // 1 2 3 4 5 6
        // 2 6 6
        log.info("byteBuffer2 position:{},limit:{},capacity:{}", byteBuffer2.position(), byteBuffer2.limit(), byteBuffer2.capacity());

        byteBuffer.put((byte)9);

        while (byteBuffer1.hasRemaining()) {
            System.out.print(byteBuffer1.get());
        }
        System.out.println();
        while (byteBuffer2.hasRemaining()) {
            System.out.print(byteBuffer2.get());
        }
    }
}
