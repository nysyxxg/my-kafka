package com.autoai.chapter01.example09;

import lombok.extern.slf4j.Slf4j;

import java.nio.CharBuffer;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/30 11:30
 * @Description:
 */
@Slf4j
public class LengthTest {

    public static void main(String[] args) {
        CharBuffer charBuffer = CharBuffer.wrap("abcd");
        // 0 4 4 4 4
        log.info("position:{},limit:{},capacity:{},length:{},remaining:{}", charBuffer.position(), charBuffer.limit(),
                charBuffer.capacity(), charBuffer.length(), charBuffer.remaining());
        charBuffer.position(1);
        // 1 4 4 3 3
        log.info("position:{},limit:{},capacity:{},length:{},remaining:{}", charBuffer.position(), charBuffer.limit(),
                charBuffer.capacity(), charBuffer.length(), charBuffer.remaining());
    }
}
