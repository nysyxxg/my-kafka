package com.autoai.chapter01.example06;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class CharBufferTest {

    /**
     * recommand usage
     * @throws UnsupportedEncodingException
     */
    @Test
    public void test1() throws UnsupportedEncodingException {

        String str = "朱开生";
        byte[] bytes = str.getBytes(StandardCharsets.UTF_16BE);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        log.info("class1:{}", byteBuffer.getClass());
        java.nio.CharBuffer charBuffer = byteBuffer.asCharBuffer();
        log.info("class2:{}", charBuffer.getClass());
        log.info("position:{},limit:{},capacity:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity());
        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());
        while (charBuffer.hasRemaining()) {
            System.out.print(charBuffer.get());
        }
    }

    @Test
    public void test2() {

        String str = "朱开生";
        byte[] bytes = str.getBytes();
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        log.info("class1:{}", byteBuffer.getClass());
        CharBuffer charBuffer = Charset.forName("utf-8").decode(byteBuffer);
        log.info("class2:{}", charBuffer.getClass());
        log.info("position:{},limit:{},capacity:{}", byteBuffer.position(), byteBuffer.limit(), byteBuffer.capacity());
        log.info("position:{},limit:{},capacity:{}", charBuffer.position(), charBuffer.limit(), charBuffer.capacity());
        while (charBuffer.hasRemaining()) {
            System.out.print(charBuffer.get());
        }
    }

}
