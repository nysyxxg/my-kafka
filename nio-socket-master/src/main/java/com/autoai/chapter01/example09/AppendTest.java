package com.autoai.chapter01.example09;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.CharBuffer;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class AppendTest {

    @Test
    public void test1() {
        CharBuffer charBuffer = CharBuffer.allocate(10);
        // 0 10 10
        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());
        charBuffer.append('a');
        // 1 10 10
        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());
        charBuffer.append("bcd");
        // 4 10 10
        // a b c d _ _ _ _ _ _
        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());
//        charBuffer.append("abcdef", 4, 6);
//        charBuffer.append("abcdef", 4, 6);
        charBuffer.append("abcdef");
        // 6 10 10
        // a b c d e f _ _ _ _
        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());

        charBuffer.flip();
        while (charBuffer.hasRemaining()) {
            System.out.print(charBuffer.get());
        }
    }
}
