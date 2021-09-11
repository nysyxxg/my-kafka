package com.autoai.chapter02.example07;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/1 15:03
 * @Description:
 */
@Slf4j
public class TransferFromTest {

    /**
     * 如果给定的位置大于该文件的当前大小，则不传输任何字节
     */
    @Test
    public void test1() {
        try (
            RandomAccessFile fileI = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            RandomAccessFile fileJ = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channelI = fileI.getChannel();
            FileChannel channelJ = fileJ.getChannel()
        ){
            channelJ.transferFrom(channelI, 100, 3);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 正常传输数据的测试
     */
    @Test
    public void test2() {
        try (
            RandomAccessFile fileI = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            RandomAccessFile fileJ = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channelI = fileI.getChannel();
            FileChannel channelJ = fileJ.getChannel()
        ){
            channelI.position(6);
            channelJ.transferFrom(channelI, 0, 3);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 如果count的字节个数大于src.remaining，则通道的src.remaining字节数传输到当前通道的position位置
     */
    @Test
    public void test3() {
        try (
            RandomAccessFile fileI = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            RandomAccessFile fileJ = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channelI = fileI.getChannel();
            FileChannel channelJ = fileJ.getChannel()
        ){
            channelI.position(6);
            channelJ.transferFrom(channelI, 0, 300);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }
}
