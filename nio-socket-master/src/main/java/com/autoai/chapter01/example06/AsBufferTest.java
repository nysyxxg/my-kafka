package com.autoai.chapter01.example06;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.*;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class AsBufferTest {

    @Test
    public void test1() {

        ByteBuffer bytebuffer1 = ByteBuffer.allocate(32);
        bytebuffer1.putDouble(1.1D);
        bytebuffer1.putDouble(1.2D);
        bytebuffer1.putDouble(1.3D);
        bytebuffer1.putDouble(1.4D);
        bytebuffer1.flip();
        DoubleBuffer doubleBuffer = bytebuffer1.asDoubleBuffer();
        for (int i = 0; i < doubleBuffer.capacity(); i++) {
            System.out.print(doubleBuffer.get(i) + " ");
        }
        System.out.println();
        ByteBuffer bytebuffer2 = ByteBuffer.allocate(16);
        bytebuffer2.putFloat(2.1F);
        bytebuffer2.putFloat(2.2F);
        bytebuffer2.putFloat(2.3F);
        bytebuffer2.putFloat(2.4F);
        bytebuffer2.flip();
        FloatBuffer floatBuffer = bytebuffer2.asFloatBuffer();
        for (int i = 0; i < floatBuffer.capacity(); i++) {
            System.out.print(floatBuffer.get(i) + " ");
        }
        System.out.println();
        ByteBuffer bytebuffer3 = ByteBuffer.allocate(16);
        bytebuffer3.putInt(31);
        bytebuffer3.putInt(32);
        bytebuffer3.putInt(33);
        bytebuffer3.putInt(34);
        bytebuffer3.flip();
        IntBuffer intBuffer = bytebuffer3.asIntBuffer();
        for (int i = 0; i < intBuffer.capacity(); i++) {
            System.out.print(intBuffer.get(i) + " ");
        }
        System.out.println();
        ByteBuffer bytebuffer4 = ByteBuffer.allocate(32);
        bytebuffer4.putLong(41L);
        bytebuffer4.putLong(42L);
        bytebuffer4.putLong(43L);
        bytebuffer4.putLong(44L);
        bytebuffer4.flip();
        LongBuffer longBuffer = bytebuffer4.asLongBuffer();
        for (int i = 0; i < longBuffer.capacity(); i++) {
            System.out.print(longBuffer.get(i) + " ");
        }
        System.out.println();
        ByteBuffer bytebuffer5 = ByteBuffer.allocate(8);
        bytebuffer5.putShort((short) 51);
        bytebuffer5.putShort((short) 52L);
        bytebuffer5.putShort((short) 53L);
        bytebuffer5.putShort((short) 54L);
        bytebuffer5.flip();
        ShortBuffer shortBuffer = bytebuffer5.asShortBuffer();
        for (int i = 0; i < shortBuffer.capacity(); i++) {
            System.out.print(shortBuffer.get(i) + " ");
        }
    }

    /**
     * 视图缓冲区可能更高效，这是因为当且仅当其支持的字节缓冲区为直接缓冲区时，它才是直接缓冲区。
     */
    @Test
    public void test2(){
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(10);
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        log.info("byte:{}, float:{}", byteBuffer.isDirect(), floatBuffer.isDirect());
    }
}
