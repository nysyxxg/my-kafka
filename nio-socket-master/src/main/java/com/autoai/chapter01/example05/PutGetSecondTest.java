package com.autoai.chapter01.example05;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/29 13:18
 * @Description:
 */
@Slf4j
public class PutGetSecondTest {

    @Test
    public void test1() {
        byte[] bytes = new byte[]{1,2,3,4,5};
        ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        byteBuffer.put((byte)111);
        byteBuffer.put((byte)222);
        // 111,222,0,0,0,0,0,0,0,0
        log.info("position:{}", byteBuffer.position());
        byteBuffer.put(bytes);
        // 111,222,1,2,3,4,5,0,0,0
        log.info("position:{}", byteBuffer.position());
        // 111,222,1,2,3,4,5
        byteBuffer.flip();
        byteBuffer.position(2);
        // 0,0,0,0,0
        byte[] result = new byte[byteBuffer.remaining()];
        // 1,2,3,4,5
        byteBuffer.get(result);
        for (int i = 0; i < result.length; i++) {
            log.info("item:{}", result[i]);
        }
    }
}
