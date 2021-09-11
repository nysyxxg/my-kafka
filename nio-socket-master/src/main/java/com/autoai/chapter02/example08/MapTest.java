package com.autoai.chapter02.example08;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/2 10:43
 * @Description:
 */
@Slf4j
public class MapTest {

    /**
     * map(MapMode mode, long position, long size)方法的使用
     * java.nio.BufferUnderflowException
     */
    @Test
    public void test1() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            MappedByteBuffer byteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, 3);
            log.info("result:{},position:{}", byteBuffer.get(), byteBuffer.position());
            log.info("result:{},position:{}", byteBuffer.get(), byteBuffer.position());
            log.info("result:{},position:{}", byteBuffer.get(), byteBuffer.position());
            log.info("result:{},position:{}", byteBuffer.get(), byteBuffer.position());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 只读模式（READ_ONLY）的测试
     * java.nio.ReadOnlyBufferException
     */
    @Test
    public void test2() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            MappedByteBuffer byteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, 3);
            byteBuffer.put(0, new Byte("1"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 可写可读模式（READ_WRITE）的测试
     */
    @Test
    public void test3() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/k.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            MappedByteBuffer byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 3);
            byteBuffer.put((byte) 'a');
            byteBuffer.put((byte) 'b');
            byteBuffer.put((byte) 'c');
            byteBuffer.rewind();
            log.info("result:{},position:{}", byteBuffer.get(), byteBuffer.position());
            log.info("result:{},position:{}", byteBuffer.get(), byteBuffer.position());
            log.info("result:{},position:{}", byteBuffer.get(), byteBuffer.position());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 专用模式（PRIVATE）的测试
     */
    @Test
    public void test4() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/k.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            MappedByteBuffer byteBuffer = channel.map(FileChannel.MapMode.PRIVATE, 0, 3);
            byteBuffer.put((byte) 'a');
            byteBuffer.put((byte) 'b');
            byteBuffer.put((byte) 'c');
            byteBuffer.rewind();
            log.info("result:{},position:{}", byteBuffer.get(), byteBuffer.position());
            log.info("result:{},position:{}", byteBuffer.get(), byteBuffer.position());
            log.info("result:{},position:{}", byteBuffer.get(), byteBuffer.position());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * MappedByteBuffer类的force()方法的使用
     * 2020-06-02 11:12:35 [INFO] common duration:5
     * 2020-06-02 11:12:35 [INFO] force sync disk duration:312
     */
    @Test
    public void test5() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/k.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            // 映射的内存，不是直接分配这么大
            MappedByteBuffer byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 5000 * 3);
            long begin = System.currentTimeMillis();
            for (int i = 0; i < 5000; i++) {
                byteBuffer.put("abc".getBytes());
            }
            long end = System.currentTimeMillis();
            log.info("common duration:{}", end - begin);

            byteBuffer.clear();

            begin = System.currentTimeMillis();
            for (int i = 0; i < 5000; i++) {
                byteBuffer.put("abc".getBytes());
                byteBuffer.force();
            }
            end = System.currentTimeMillis();
            log.info("force sync disk duration:{}", end - begin);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * MappedByteBuffer load()和boolean isLoaded()方法的使用
     */
    @Test
    public void test6() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/k.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            Thread.sleep(20000);
            MappedByteBuffer byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1*1024*1024);
            log.info("isload:{}", byteBuffer.isLoaded());
            Thread.sleep(20000);
            byteBuffer.load();
            log.info("isload:{}", byteBuffer.isLoaded());
            Thread.sleep(2000000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
