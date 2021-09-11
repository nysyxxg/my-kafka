package com.autoai.chapter01.example03;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class ArrayOffSetTest {

    public static void main(String[] args) {

        ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        log.info("offset:{}", byteBuffer.arrayOffset());
    }
}
