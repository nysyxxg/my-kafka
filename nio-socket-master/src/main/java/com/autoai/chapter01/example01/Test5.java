package com.autoai.chapter01.example01;

import java.nio.ByteBuffer;

/**
 * 1.3.5 使用Buffer mark()方法处理标记
 * <p>
 * 方法Buffer mark()的作用：在此缓冲区的位置设置标记
 * 标记有什么用呢？缓冲区的标记是一个索引，在调用reset()方法时，会将缓冲区等position位置重置为该索引。
 * 标记(mark)并不是必需的。定义mark时，不能将其定义为负数，并且不能让它大于position。如果定义了mark，
 * 则在将position或limit调整为小于该mark的时，该mark被舍弃，丢弃后mark的值为-1.如果未定义mark，那么
 * 调用reset()方法将导致抛出InvalidMarkException异常
 * 缓冲区中的mark有些类似于探险或爬山时在关键路口设置”路标“,目的是在原路返回找到回去的路。
 *
 * @author
 * @date
 */
public class Test5 {
    
    public static void main(String[] args) {
        byte[] byteArray = new byte[]{1, 2, 3};
        ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
        
        System.out.println("byteBuffer.capacity=" + byteBuffer.capacity());
        System.out.println(byteBuffer);
        
        byteBuffer.position(1);
//        byteBuffer.mark();  //在位置1设置mark
        System.out.println(byteBuffer);
        System.out.println("byteBuffer.position=" + byteBuffer.position());
        
        byteBuffer.position(2);     //改变位置
        
        System.out.println("byteBuffer.position=" + byteBuffer.position());
        
        byteBuffer.reset();     //重置位置,
        // 如果没有进行 mark操作，调用reset 就会 Exception in thread "main" java.nio.InvalidMarkException
        
        System.out.println();
        System.out.println(byteBuffer);
        //回到位置为1处
        
        System.out.println("byteBuffer.position=" + byteBuffer.position());
        
    }
/*
    byteBuffer.capacity=3
    byteBuffer.position=1
    byteBuffer.position=2
    byteBuffer.position=1
    */
}