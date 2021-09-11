package com.autoai.chapter01.example03;

import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.nio.CharBuffer;

/**
 * @Author:
 * @Date:
 * @Description:
 */
public class FlipTest {
    
    @Test
    public void test1() throws UnsupportedEncodingException {
        String a = "名";
        System.out.println("UTF-8编码长度:" + a.getBytes("UTF-8").length);
        System.out.println("GBK编码长度:" + a.getBytes("GBK").length);
        System.out.println("GB2312编码长度:" + a.getBytes("GB2312").length);
        System.out.println("==========================================");
        
        String c = "0x20001";
        System.out.println("UTF-8编码长度:" + c.getBytes("UTF-8").length);
        System.out.println("GBK编码长度:" + c.getBytes("GBK").length);
        System.out.println("GB2312编码长度:" + c.getBytes("GB2312").length);
        System.out.println("==========================================");
        
        char[] arr = Character.toChars(0x20001);
        String s = new String(arr);
        System.out.println("char array length:" + arr.length);
        System.out.println("content:|  " + s + " |");
        System.out.println("String length:" + s.length());
        System.out.println("UTF-8编码长度:" + s.getBytes("UTF-8").length);
        System.out.println("GBK编码长度:" + s.getBytes("GBK").length);
        System.out.println("GB2312编码长度:" + s.getBytes("GB2312").length);
        System.out.println("==========================================");
    }
    
    public static void main(String[] args) throws UnsupportedEncodingException {
        String data = "阿达飒飒大厦   第";
        System.out.println("UTF-8编码长度:" + data.getBytes("UTF-8").length);
    
        
        CharBuffer charBuffer = CharBuffer.allocate(21); // 申请内存缓冲区
        // 存储中文字符，一个中文，在UTF-8编码下，占用三个字节
        charBuffer.put(data);
        charBuffer.flip();
        for (int i = 0; i < charBuffer.limit(); i++) {
            System.out.print(charBuffer.get());
        }
    }
}
