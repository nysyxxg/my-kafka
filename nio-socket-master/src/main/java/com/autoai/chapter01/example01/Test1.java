package com.autoai.chapter01.example01;

import java.nio.*;

/**
 * 1.3.1 包装数据与获取容量
 * shortBuffer.capacity()和具体实现类
 *
 * @author carway
 * @date 2019-04-02 16:02
 */
public class Test1 {

    public static void main(String[] args) {
        byte[] byteArray = new byte[]{1, 2, 3};
        short[] shortArray = new short[]{1, 2, 3, 4};
        int[] intArray = new int[]{1, 2, 3, 4, 5};
        long[] longArray = new long[]{1, 2, 3, 4, 5, 6};
        float[] floatArray = new float[]{1, 2, 3, 4, 5, 6, 7};
        double[] doubleArray = new double[]{1, 2, 3, 4, 5, 6, 7, 8};
        char[] charArray = new char[]{'a', 'b', 'c', 'd'};

        ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
        ShortBuffer shortBuffer = ShortBuffer.wrap(shortArray);
        IntBuffer intBuffer = IntBuffer.wrap(intArray);
        LongBuffer longBuffer = LongBuffer.wrap(longArray);
        FloatBuffer floatBuffer = FloatBuffer.wrap(floatArray);
        DoubleBuffer doubleBuffer = DoubleBuffer.wrap(doubleArray);
        CharBuffer charBuffer = CharBuffer.wrap(charArray);

        System.out.println("byteBuffer=" + byteBuffer.getClass().getName());
        System.out.println("shortBuffer=" + shortBuffer.getClass().getName());
        System.out.println("intBuffer=" + intBuffer.getClass().getName());
        System.out.println("longBuffer=" + longBuffer.getClass().getName());
        System.out.println("floatBuffer=" + floatBuffer.getClass().getName());
        System.out.println("doubleBuffer=" + doubleBuffer.getClass().getName());
        System.out.println("charBuffer=" + charBuffer.getClass().getName());

        System.out.println();

        System.out.println("byteBuffer.capacity=" + byteBuffer.capacity());
        System.out.println("shortBuffer.capacity=" + shortBuffer.capacity());
        System.out.println("intBuffer.capacity=" + intBuffer.capacity());
        System.out.println("longBuffer.capacity=" + longBuffer.capacity());
        System.out.println("floatBuffer.capacity=" + floatBuffer.capacity());
        System.out.println("doubleBuffer.capacity=" + doubleBuffer.capacity());
        System.out.println("charBuffer.capacity=" + charBuffer.capacity());

//        byteBuffer=java.nio.HeapByteBuffer
//        shortBuffer=java.nio.HeapShortBuffer
//        intBuffer=java.nio.HeapIntBuffer
//        longBuffer=java.nio.HeapLongBuffer
//        floatBuffer=java.nio.HeapFloatBuffer
//        doubleBuffer=java.nio.HeapDoubleBuffer
//        charBuffer=java.nio.HeapCharBuffer
//
//        byteBuffer.capacity=3
//        shortBuffer.capacity=4
//        intBuffer.capacity=5
//        longBuffer.capacity=6
//        floatBuffer.capacity=7
//        doubleBuffer.capacity=8
//        charBuffer.capacity=4

    }
}