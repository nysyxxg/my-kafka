package com.autoai.chapter01.example04;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/28 17:54
 * @Description:
 */
@Slf4j
public class WrapTest {

    @Test
    public void test1() {
        byte[] bytes = new byte[]{1,2,3,4,5,6,7,8};
        ByteBuffer byteBuffer1 = ByteBuffer.wrap(bytes);
        ByteBuffer byteBuffer2 = ByteBuffer.wrap(bytes, 1, 5);
        log.info("byteBuffer1: position:{},limit:{},capacity:{}", byteBuffer1.position(), byteBuffer1.limit(), byteBuffer1.capacity());
        // 1 6 8
        log.info("byteBuffer2: position:{},limit:{},capacity:{}", byteBuffer2.position(), byteBuffer2.limit(), byteBuffer2.capacity());
    }

    @Test
    public void test2() {

        ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        log.info("byteBuffer: position:{},limit:{},capacity:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity());
        byteBuffer.put((byte)'a');
        log.info("byteBuffer: position:{},limit:{},capacity:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity());
        byteBuffer.put((byte)'b');
        log.info("byteBuffer: position:{},limit:{},capacity:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity());
        byteBuffer.put((byte)'c');
        log.info("byteBuffer: position:{},limit:{},capacity:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity());
        byteBuffer.rewind();
        byte b1 = byteBuffer.get();
        log.info("byteBuffer: position:{},limit:{},capacity:{},b:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity(), (char)b1);
        b1 = byteBuffer.get();
        log.info("byteBuffer: position:{},limit:{},capacity:{},b:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity(), (char)b1);
        b1 = byteBuffer.get();
        log.info("byteBuffer: position:{},limit:{},capacity:{},b:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity(), (char)b1);
        b1 = byteBuffer.get();
        log.info("byteBuffer: position:{},limit:{},capacity:{},b:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity(), b1);
    }


}
