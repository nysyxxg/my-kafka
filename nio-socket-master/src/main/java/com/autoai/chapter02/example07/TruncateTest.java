package com.autoai.chapter02.example07;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/1 14:46
 * @Description:
 */
@Slf4j
public class TruncateTest {

    /**
     * 截断缓冲区，给定大小小于该文件的当前大小，则截取该文件
     */
    @Test
    public void test1() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.wrap("123456789".getBytes());
            channel.write(byteBuffer);
            channel.truncate(5);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 截断缓冲区，给定大小大于或等于该文件的当前大小，则不修改文件。
     */
    @Test
    public void test2() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.wrap("123456789".getBytes());
            channel.write(byteBuffer);
            channel.truncate(20);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }
}
