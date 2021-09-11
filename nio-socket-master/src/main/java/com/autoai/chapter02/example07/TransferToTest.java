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
public class TransferToTest {

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
            channelI.transferTo(100, 3, channelJ);
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
            channelI.transferTo(6, 3, channelJ);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 如果count的字节个数大于position到size的字节个数，则传输通道的sizeposition个字节数到dest通道的当前位置
     */
    @Test
    public void test3() {
        try (
            RandomAccessFile fileI = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            RandomAccessFile fileJ = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channelI = fileI.getChannel();
            FileChannel channelJ = fileJ.getChannel()
        ){
            channelI.transferTo(6, 100, channelJ);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }
}
