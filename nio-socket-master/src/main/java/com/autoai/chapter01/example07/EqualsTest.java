package com.autoai.chapter01.example07;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/30 09:32
 * @Description:
 */
@Slf4j
public class EqualsTest {

    /**
     * 判断是不是自身，如果是自身，则返回为true。
     */
    @Test
    public void test1() {

        ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        boolean b = byteBuffer.equals(byteBuffer);
        log.info("b:{}", b);
    }

    /**
     * 判断是不是ByteBuffer类的实例，如果不是，则返回false。
     */
    @Test
    public void test2() {
        byte[] bytes = new byte[]{1,2,3};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        boolean b = byteBuffer.equals(intBuffer);
        log.info("b:{}", b);
    }

    /**
     * 判断remaining()值是否一样，如果不一样，则返回false。
     */
    @Test
    public void test3() {
        byte[] bytes = new byte[]{1,2,3};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        ByteBuffer byteBuffer1 = ByteBuffer.wrap(bytes);
        byteBuffer1.position(1);

        boolean b = byteBuffer.equals(byteBuffer1);
        log.info("b:{}", b);
    }

    /**
     * 判断两个缓冲区中的position与limit之间的数据是否完全一样，只要有一个字节不同，就返回false，否则返回true。
     */
    @Test
    public void test4() {
        byte[] bytes1 = new byte[]{1,2,3};
        ByteBuffer byteBuffer1 = ByteBuffer.wrap(bytes1);

        byte[] bytes2 = new byte[]{0,1,2,3,4};
        ByteBuffer byteBuffer2 = ByteBuffer.wrap(bytes2);
        byteBuffer2.position(1);
        byteBuffer2.limit(4);

        boolean b = byteBuffer1.equals(byteBuffer2);
        log.info("b:{}", b);
    }
}