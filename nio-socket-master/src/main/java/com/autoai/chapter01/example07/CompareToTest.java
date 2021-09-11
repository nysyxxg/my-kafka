package com.autoai.chapter01.example07;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/30 09:56
 * @Description:
 */
@Slf4j
public class CompareToTest {

    /**
     * 如果在开始与结束的范围之间有一个字节不同，则返回两者的减数。
     */
    @Test
    public void test1() {

        byte[] bytes1 = new byte[]{4,5,6};
        byte[] bytes2 = new byte[]{1,2,3,4,10,6};
        ByteBuffer byteBuffer1 = ByteBuffer.wrap(bytes1);
        ByteBuffer byteBuffer2 = ByteBuffer.wrap(bytes2);

        byteBuffer2.position(3);

        int compareTo = byteBuffer1.compareTo(byteBuffer2);
        log.info("compareto:{}", compareTo);
    }

    /**
     * 如果在开始与结束的范围之间每个字节都相同，则返回两者remaining()的减数。
     */
    @Test
    public void test2() {

        byte[] bytes1 = new byte[]{4,5,6};
        byte[] bytes2 = new byte[]{1,2,3,4,5,6,7,8};
        ByteBuffer byteBuffer1 = ByteBuffer.wrap(bytes1);
        ByteBuffer byteBuffer2 = ByteBuffer.wrap(bytes2);

        byteBuffer2.position(3);

        int compareTo = byteBuffer1.compareTo(byteBuffer2);
        log.info("compareto:{}", compareTo);
    }
}
