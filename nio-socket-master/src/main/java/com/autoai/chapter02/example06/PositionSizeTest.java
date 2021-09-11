package com.autoai.chapter02.example06;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/1 14:10
 * @Description:
 */
@Slf4j
public class PositionSizeTest {

    /**
     * position(long newPosition)方法的作用是设置此通道的文件位置。
     * long size()方法的作用是返回此通道关联文件的当前大小。
     */
    @Test
    public void test1() {
        try (
            FileInputStream fis = new FileInputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/a.txt");
            FileChannel channel = fis.getChannel()
        ){
            log.info("position:{},size:{}", channel.position(), channel.size());
            ByteBuffer byteBuffer = ByteBuffer.allocate(3);
            channel.read(byteBuffer);
            log.info("position:{},size:{}", channel.position(), channel.size());

        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证"将该位置设置为大于文件当前大小的值是合法的，但这不会更改文件的大小，
     * 试图在这样的位置读取字节将立即返回已到达文件末尾的指示，试图在这种位置写入字节将导致文件扩大，以容纳新的字节，
     * 在以前文件末尾和新写入字节之间的字节值是未指定的”
     */
    @Test
    public void test2() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            log.info("position:{},size:{}", channel.position(), channel.size());
            channel.position(20);
            log.info("position:{},size:{}", channel.position(), channel.size());
            ByteBuffer byteBuffer = ByteBuffer.allocate(3);
            channel.read(byteBuffer);
            log.info("position:{},size:{}", channel.position(), channel.size());

            byteBuffer.rewind();
            byteBuffer.put("123".getBytes());
            byteBuffer.rewind();
            channel.write(byteBuffer);
            log.info("position:{},size:{}", channel.position(), channel.size());

        } catch (Exception e) {
            log.error(e.toString());
        }
    }
}
