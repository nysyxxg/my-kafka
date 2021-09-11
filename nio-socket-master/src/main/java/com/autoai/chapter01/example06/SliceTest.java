package com.autoai.chapter01.example06;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/29 15:44
 * @Description:
 */
@Slf4j
public class SliceTest {

    @Test
    public void test1() {
        byte[] bytes = new byte[]{1,2,3,4,5};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.position(2);
        // 2 5 5
        log.info("position:{},limit:{},capacity:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity());
        ByteBuffer byteBuffer1 = byteBuffer.slice();
        // 12345->345
        log.info("position:{},limit:{},capacity:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity());
        log.info("position:{},limit:{},capacity:{}", byteBuffer1.position(), byteBuffer1.limit(), byteBuffer1.capacity());
        byteBuffer1.put((byte)6);
        for (int i = 0; i < bytes.length; i++) {
            System.out.print(bytes[i]);
        }
        log.info("offset:{}", byteBuffer1.arrayOffset());

    }
}
