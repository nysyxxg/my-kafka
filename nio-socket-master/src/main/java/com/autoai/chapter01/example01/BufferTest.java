package com.autoai.chapter01.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.*;

/**
 * @Author:
 * @Date:
 * @Description: 0 <=  mark <=    position   <= limit <=  capacity
 */
@Slf4j
public class BufferTest {
    
    /**
     * {@code Buffer#capacity}
     */
    @Test
    public void testCapacity() {
        byte[] bytes = new byte[]{1, 2, 3, 4};
        char[] chars = new char[]{'a', 'b', 'c', 'd'};
        short[] shorts = new short[]{1, 2, 3, 4};
        int[] ints = new int[]{1, 2, 3, 4};
        long[] longs = new long[]{1, 2, 3, 4};
        float[] floats = new float[]{1, 2, 3, 4};
        double[] doubles = new double[]{1, 2, 3, 4};
        
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ShortBuffer shortBuffer = ShortBuffer.wrap(shorts);
        IntBuffer intBuffer = IntBuffer.wrap(ints);
        LongBuffer longBuffer = LongBuffer.wrap(longs);
        FloatBuffer floatBuffer = FloatBuffer.wrap(floats);
        DoubleBuffer doubleBuffer = DoubleBuffer.wrap(doubles);
        
        System.out.println(byteBuffer.getClass());
        System.out.println(charBuffer.getClass());
        System.out.println(shortBuffer.getClass());
        System.out.println(intBuffer.getClass());
        System.out.println(longBuffer.getClass());
        System.out.println(floatBuffer.getClass());
        System.out.println(doubleBuffer.getClass());
        
        System.out.println(byteBuffer.capacity());
        System.out.println(charBuffer.capacity());
        System.out.println(shortBuffer.capacity());
        System.out.println(intBuffer.capacity());
        System.out.println(longBuffer.capacity());
        System.out.println(floatBuffer.capacity() + "--------->" + floatBuffer.array().length);
        System.out.println(doubleBuffer.capacity());
    }
    
    @Test
    public void testLimit() {
        char[] chars = new char[]{'a', 'b', 'c'};
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        log.info("capacity:{}", charBuffer.capacity());
        log.info("limit default:{}", charBuffer.limit());
        charBuffer.limit(1);
        log.info("limit changed:{}", charBuffer.limit());
        
        charBuffer.put('d');
        charBuffer.put('e');
    }
    
    @Test
    public void testLimit2() {
        char[] charArray = new char[]{'a', 'b', 'c', 'd', 'e'};
        CharBuffer buffer = CharBuffer.wrap(charArray);
        //  capacity 容量索引是从1开始， limit 取值范围是从索引0开始
        System.out.println("A capacity () = " + buffer.capacity() + " limit() = " + buffer.limit());
        buffer.limit(3);
        System.out.println();
        System.out.println("B capacity () = " + buffer.capacity() + "  limit() = " + buffer.limit());
        System.out.println(" buffer : " + buffer );
        
        buffer.put(0, 'o'); // 0
        buffer.put(1, 'p'); // 1
        buffer.put(2, 'q'); // 2
        buffer.put(3, 'r'); // 3- 此位置是第 1个不可读不可写的,  如果继续put数据，就会报错，超过存储容量
        buffer.put(4, 'S'); // 4
        buffer.put(5, 't'); // 5
        buffer.put(6, 'u'); // 6
        /**
         * java.lang.IndexOutOfBoundsException
         * 	at java.nio.Buffer.checkIndex(Buffer.java:540)
         * 	at java.nio.HeapCharBuffer.put(HeapCharBuffer.java:178)
         * 	at com.autoai.chapter01.example01.BufferTest.testLimit2(BufferTest.java:78)
         * 	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
         * 	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
         * 	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
         * 	at java.lang.reflect.Method.invoke(Method.java:498)
         * 	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)
         * 	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
         */
    }
    
    @Test
    public void testPosition() {
        char[] chars = new char[]{'a', 'b', 'c', 'd'};
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        log.info("capacity:{}, limit:{}, position:{}", charBuffer.capacity(), charBuffer.limit(), charBuffer.position());
        charBuffer.position(1);
        System.out.println(chars);
    }
    
    @Test
    public void testRemaining() {
        char[] chars = new char[]{'a', 'b', 'c'};
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        log.info("capacity:{}, limit:{}, position:{}, remaining:{}", charBuffer.capacity(), charBuffer.limit(),
                charBuffer.position(), charBuffer.remaining());
        charBuffer.position(1);
        log.info("capacity:{}, limit:{}, position:{}, remaining:{}", charBuffer.capacity(), charBuffer.limit(),
                charBuffer.position(), charBuffer.remaining());
    }
    
    @Test
    public void testMark() {
        byte[] bytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.position(4);
        log.info("position:{}", byteBuffer.position());
        byteBuffer.mark();
        byteBuffer.position(7);
        log.info("position:{}", byteBuffer.position());
        byteBuffer.reset();
        log.info("position:{}", byteBuffer.position());
    }
}
