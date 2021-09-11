package com.autoai.chapter01.example01;

import java.nio.CharBuffer;

/**
 * 1.3.3 位置获取与设置
 * position()和 Buffer position(int new Position)的作用
 * position代表”下一个“要读取或写入元素的index(索引)，缓冲区的position(位置)不能为负，并且position不能大于其limit。
 * 如果mark已定义且大于新的position，则丢弃该mark
 *
 * @author
 * @date
 */
public class Test3 {
    public static void main(String[] args) {
        char[] charArray = new char[]{'a', 'b', 'c', 'd'};
        CharBuffer charBuffer = CharBuffer.wrap(charArray);
        System.out.println("A capacity()=" + charBuffer.capacity() + " limit()=" + charBuffer.limit() + " position()=" + charBuffer.position());
       
        charBuffer.position(2);
        System.out.println("B capacity()=" + charBuffer.capacity() + " limit()=" + charBuffer.limit() + " position()=" + charBuffer.position());
       
        charBuffer.put('z');
        System.out.println("C capacity()=" + charBuffer.capacity() + " limit()=" + charBuffer.limit() + " position()=" + charBuffer.position());
       
        charBuffer.put(1, 'e');
        System.out.println("D capacity()=" + charBuffer.capacity() + " limit()=" + charBuffer.limit() + " position()=" + charBuffer.position());

        for (int i = 0; i < charArray.length; i++) {
            System.out.print(charArray[i] + " ");
        }
    }

    /**
     * A capacity()=4 limit()=4 position()=0
     * B capacity()=4 limit()=4 position()=2
     * C capacity()=4 limit()=4 position()=3
     * D capacity()=4 limit()=4 position()=3
     * a e z d
     * put(index,e)不影响position
     */
}