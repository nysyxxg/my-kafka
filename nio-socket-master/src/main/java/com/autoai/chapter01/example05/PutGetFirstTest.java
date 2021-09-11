package com.autoai.chapter01.example05;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/29 11:20
 * @Description:
 */
@Slf4j
public class PutGetFirstTest {

    @Test
    public void test1() {
        byte[] byteArrayIn1 = { 1, 2, 3, 4, 5, 6, 7, 8 };
        byte[] byteArrayIn2 = { 55, 66, 77, 88 };
        // 开辟10个空间
        ByteBuffer bytebuffer = ByteBuffer.allocate(10);
        // 将1,2,3,4,5,6,7,8放入缓冲区的前8个位置中
        bytebuffer.put(byteArrayIn1);
        // 执行put()方法后位置发生改变，将位置设置成2
        bytebuffer.position(2);
        // 1,2,66,77,88,6,7,8,0,0
        bytebuffer.put(byteArrayIn2, 1, 3);
        System.out.print("A=");
        byte[] getByte = bytebuffer.array();
        for (int i = 0; i < getByte.length; i++) {
            System.out.print(getByte[i] + " ");
        }
        System.out.println();
        bytebuffer.position(1);
        // 创建新的byte[]数组byteArrayOut，目的是将缓冲区中的数据导出来
        byte[] byteArrayOut = new byte[bytebuffer.capacity()];
        // 使用get()方法从缓冲区position值为1的位置开始，向byteArrayOut数组的索引为3处一共复制4个字节
        bytebuffer.get(byteArrayOut, 3, 4);
        System.out.print("B=");
        // 打印byteArrayOut数组中的内容
        for (int i = 0; i < byteArrayOut.length; i++) {
            System.out.print(byteArrayOut[i] + " ");
        }
    }

    /**
     * 当offset+length的值大于src.length时，抛出IndexOutOfBoundsException异常
     */
    @Test
    public void test2() {

        ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        byte[] bytes =  new byte[]{1,2,3};
        try {
            byteBuffer.put(bytes, 1, 3);
        } catch (IndexOutOfBoundsException e) {
            log.error("IndexOutOfBoundsException");
        }
    }

    /**
     * 当参数length的值大于buffer.remaining时，抛出BufferOverflowException异常。
     */
    @Test
    public void test3() {

        ByteBuffer byteBuffer = ByteBuffer.allocate(5);
        byteBuffer.position(5);
        byte[] bytes =  new byte[]{1,2,3};
        try {
            byteBuffer.put(bytes, 1, 1);
        } catch (BufferOverflowException e) {
            log.error("BufferOverflowException");
        }
    }

    /**
     * 当offset+length的值大于dst.length时，抛出IndexOutOfBoundsException异常
     */
    @Test
    public void test4() {
        byte[] bytes = new byte[]{1,2,3,4,5};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byte[] result = new byte[byteBuffer.array().length];
        try {
            byteBuffer.get(result, 2, 4);
        } catch (IndexOutOfBoundsException e) {
            log.error("IndexOutOfBoundsException");
        }
    }

    /**
     * 当参数length的值大于buffer.remaining时，抛出BufferUnderflowException异常。
     */
    @Test
    public void test5() {
        byte[] bytes = new byte[]{1,2,3,4,5};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byte[] result = new byte[byteBuffer.array().length + 5];
        try {
            byteBuffer.get(result, 0, 6);
        } catch (BufferUnderflowException e) {
            log.error("BufferOverflowException");
        }
    }
}
