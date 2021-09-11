package com.autoai.chapter01.example01;

import java.nio.CharBuffer;


/**
 * 1.3.2 限制获取与设置
 * limit()的作用
 * limit代表第一个不应该读取或写入元素的index，缓冲区的limit不能为负，并且limit不能大于其capacity
 *
 * @author carway
 * @date 2019-04-02 20:45
 */
public class Test2 {

    public static void main(String[] args) {
        char[] charArray = new char[] {'a','b','c','d','e'};
        CharBuffer buffer = CharBuffer.wrap(charArray);
        System.out.println("A capacity()="+buffer.capacity()+" limit()="+buffer.limit());
        buffer.limit(3);
        System.out.println();
        System.out.println("B capacity()="+buffer.capacity()+" limit()="+buffer.limit());
        buffer.put(0,'o');//0
        buffer.put(1,'p');//1
        buffer.put(2,'q');//2
        buffer.put(3,'r');//3 此位置是第一个不可读不可写的缩影
        buffer.put(4,'s');//4
        buffer.put(5,'t');//4
        buffer.put(6,'u');//6

//       wrap初始化后，limit的值是capacity+1，因为limit取值范围是从索引0开始，而capacity是从1开始
//        A capacity()=5 limit()=5
//
//        B capacity()=5 limit()=3
    }
}