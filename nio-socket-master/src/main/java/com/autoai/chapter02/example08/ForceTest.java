package com.autoai.chapter02.example08;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/2 09:32
 * @Description:
 */
@Slf4j
public class ForceTest {

    /**
     * void force(boolean metaData)方法的性能
     */
    @Test
    public void test1() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.wrap("abc".getBytes());
            long begin = System.currentTimeMillis();
            for (int i = 0; i < 5000; i++) {
                byteBuffer.rewind();
                channel.write(byteBuffer);
            }
            long end = System.currentTimeMillis();
            log.info("duration:{}", end - begin);

            channel.position(0);

            begin = System.currentTimeMillis();
            for (int i = 0; i < 5000; i++) {
                byteBuffer.rewind();
                channel.write(byteBuffer);
                channel.force(false);
            }
            end = System.currentTimeMillis();
            log.info("duration:{}", end - begin);


        } catch (Exception e) {
            log.error(e.toString());
        }
    }
}
