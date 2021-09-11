package com.autoai.chapter01.example06;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class AsReadOnlyBufferTest {

    public static void main(String[] args) {
        byte[] bytes = new byte[]{1,2,3,4,5};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        log.info("readonly:{}", byteBuffer.isReadOnly());

        ByteBuffer readOnlyBuffer = byteBuffer.asReadOnlyBuffer();
        log.info("readonly:{}", readOnlyBuffer.isReadOnly());
        log.info("position:{}", readOnlyBuffer.position());

        try {
            readOnlyBuffer.put((byte)6);
        } catch (ReadOnlyBufferException e) {
            log.error("read only");
        }

    }
}
