package com.autoai.chapter01.example05;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/29 13:31
 * @Description:
 */
@Slf4j
public class PutGetThirdTest {

    @Test
    public void test1() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        byte[] bytes = new byte[]{1,2,3,4};
        byteBuffer.put(bytes);
        // 1，2，3，4，0，0，0，0，0，0
        log.info("position:{}", byteBuffer.position());
        byteBuffer.put(1, (byte) 5);
        // 1，5，3，4，0，0，0，0，0，0
        log.info("position:{}", byteBuffer.position());
        byte b = byteBuffer.get(1);
        log.info("position:{}", byteBuffer.position());
        log.info("b:{}", b);
        byteBuffer.flip();
        log.info("position:{}, limit:{}", byteBuffer.position(), byteBuffer.limit());
        while (byteBuffer.hasRemaining()) {
            log.info("item:{}", byteBuffer.get());
        }
    }

    @Test
    public void test2() {
        byte[] src = new byte[]{1,2,3};
        ByteBuffer srcBuffer = ByteBuffer.wrap(src);
        srcBuffer.position(0);
        log.info("src position:{}", srcBuffer.position());

        byte[] dest = new byte[]{4,5,6};
        ByteBuffer destBuffer = ByteBuffer.allocate(10);
        destBuffer.put(dest);
        log.info("dest position:{}", destBuffer.position());

        destBuffer.put(srcBuffer);
        log.info("src position:{}", srcBuffer.position());
        log.info("dest position:{}", destBuffer.position());

        destBuffer.flip();
        while (destBuffer.hasRemaining()) {
            log.info("item:{}", destBuffer.get());
        }
    }

    @Test
    public void test3() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(100);
        // 0
        byteBuffer.putChar('a');
        // 2
        byteBuffer.putChar(2, 'b');
        // 4
        byteBuffer.position(4);
        byteBuffer.putDouble(1.11);
        // 12
        byteBuffer.putDouble(12,1.21);
        // 20
        byteBuffer.position(20);
        // 24
        byteBuffer.putFloat(1.1f);
        // 24
        byteBuffer.putFloat(24, 1.2f);
        // 28
        byteBuffer.position(28);
        // 32
        byteBuffer.putInt(1);
        // 32
        byteBuffer.putInt(32, 2);
        // 36
        byteBuffer.position(36);
        // 44
        byteBuffer.putLong(111);
        // 44
        byteBuffer.putLong(44, 222);
        // 52
        byteBuffer.position(52);
        // 54
        byteBuffer.putShort(Short.parseShort("11"));

        byteBuffer.flip();
        System.out.println(byteBuffer.getChar());
        System.out.println(byteBuffer.getChar(2));
        byteBuffer.position(4);
        System.out.println(byteBuffer.getDouble());
        System.out.println(byteBuffer.getDouble(12));
        byteBuffer.position(20);
        System.out.println(byteBuffer.getFloat());
        System.out.println(byteBuffer.getFloat(24));
        byteBuffer.position(28);
        System.out.println(byteBuffer.getInt());
        System.out.println(byteBuffer.getInt(32));
        byteBuffer.position(36);
        System.out.println(byteBuffer.getLong());
        System.out.println(byteBuffer.getLong(44));
        byteBuffer.position(52);
        System.out.println(byteBuffer.getShort());
    }
}
