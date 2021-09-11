package com.autoai.chapter01.example03;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class HasArrayTest {


    public static void main(String[] args) {
    
        /**
         * java.nio.ByteBuffer类的hasArray()方法用于确保给定缓冲区是否由可访问的字节数组支持。
         * 如果此缓冲区有可访问的后备数组，则返回true，否则返回false。如果此方法返回true，
         * 则可以安全地调用array()和arrayOffset()方法，因为它们在支持数组上起作用。
         *
         */
        ByteBuffer byteBuffer1 = ByteBuffer.allocate(10);
        boolean b1 = byteBuffer1.hasArray(); // 返回值：当且仅当此缓冲区由数组支持并且不是只读时，此方法才会返回true。否则返回false。
        log.info("hasArray:{}", b1);

        ByteBuffer byteBuffer2 = ByteBuffer.allocateDirect(10);
        boolean b2 = byteBuffer2.hasArray();
        log.info("hasArray:{}", b2);
    }
}
