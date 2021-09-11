package com.autoai.chapter01.example06;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class OrderTest {

    @Test
    public void test1() {

        int value = 123456789;
        ByteBuffer bytebuffer1 = ByteBuffer.allocate(4);
        System.out.print(bytebuffer1.order());
        System.out.println("===");
        bytebuffer1.putInt(value);

        byte[] byteArray = bytebuffer1.array();
        for (int i = 0; i < byteArray.length; i++) {
            System.out.print(byteArray[i]);
        }
        System.out.println();

        bytebuffer1 = ByteBuffer.allocate(4);
        System.out.print(bytebuffer1.order());
        bytebuffer1.order(ByteOrder.BIG_ENDIAN);
        System.out.print(bytebuffer1.order());
        System.out.println("===");
        bytebuffer1.putInt(value);
        byteArray = bytebuffer1.array();
        for (int i = 0; i < byteArray.length; i++) {
            System.out.print(byteArray[i]);
        }
        System.out.println();

        bytebuffer1 = ByteBuffer.allocate(4);
        bytebuffer1.order(ByteOrder.LITTLE_ENDIAN);
        System.out.print(bytebuffer1.order());
        System.out.println("===");
        bytebuffer1.putInt(value);
        byteArray = bytebuffer1.array();
        for (int i = 0; i < byteArray.length; i++) {
            System.out.print(byteArray[i]);
        }
    }

    @Test
    public void test2() {
        int value = 12345678;
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        byteBuffer.putInt(value);

        // 开始读取
        byteBuffer.flip();
        int anInt = byteBuffer.getInt();
        log.info("anint:{}", anInt);

        byteBuffer.flip();
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int bnInt = byteBuffer.getInt();
        log.info("bnint:{}", bnInt);
    }
}
