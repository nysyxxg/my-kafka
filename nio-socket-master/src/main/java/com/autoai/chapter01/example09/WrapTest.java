package com.autoai.chapter01.example09;

import lombok.extern.slf4j.Slf4j;

import java.nio.CharBuffer;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class WrapTest {

    public static void main(String[] args) {

        // a b c d e
        CharBuffer charBuffer = CharBuffer.wrap("abcde", 1, 3);  // 不包括3，只截取 1,2 数据为: bc

        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());
        while (charBuffer.hasRemaining()) {
            System.out.print(charBuffer.get());
        }
        System.out.println("---------------------");
        charBuffer.clear();
        System.out.println("---------------charBuffer------"+ charBuffer);
        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());
        while (charBuffer.hasRemaining()) {
            System.out.print(charBuffer.get());
        }
    }
}
