package com.autoai.chapter01.example03;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.CharBuffer;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class ClearBufferTest {

    public static void main(String[] args) {
        char[] chars = new char[]{'a','b','c','d'};
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        // 0 4 4
        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());
        charBuffer.limit(3);
        charBuffer.position(2);
        charBuffer.mark();
        // 2 3 4
        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());
        charBuffer.clear();
        // 0 4 4
        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());
        System.out.println(chars);
    
        try {
            charBuffer.reset();
        }catch (Exception e){
            System.out.println(" charBuffer  的 mark 丢失.......");
            e.printStackTrace();
        }
    }
}
