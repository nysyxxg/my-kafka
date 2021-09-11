package com.autoai.chapter01.example04;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.*;
import java.time.Instant;

/**
 * @Author:
 * @Date:
 * @Description: 直接缓冲区和非直接缓冲区性能对比
 */
@Slf4j
public class PerformTest {


    @Test
    public void test1() {
        long begin = Instant.now().toEpochMilli();
        ByteBuffer byteBuffer1 = ByteBuffer.allocate(1900000000);
        for (int i = 0; i < 1900000000; i++) {
            byteBuffer1.put((byte)123);
        }
        long end = Instant.now().toEpochMilli();
        log.info("cost:{}", end - begin);
    }

    @Test
    public void test2() {
        long begin = Instant.now().toEpochMilli();
        ByteBuffer byteBuffer2 = ByteBuffer.allocateDirect(1900000000);
        for (int i = 0; i < 1900000000; i++) {
            byteBuffer2.put((byte)123);
        }
        long end = Instant.now().toEpochMilli();
        log.info("cost:{}", end - begin);
    }

    @Test
    public void test3() {
        test2();
        test1();
    }
}
