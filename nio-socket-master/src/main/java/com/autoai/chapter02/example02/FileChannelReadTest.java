package com.autoai.chapter02.example02;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Author: zhukaishengy
 * @Date: 2020/5/14 16:12
 * @Description:
 */
@Slf4j
public class FileChannelReadTest {

    /**
     * 验证int read(ByteBuffer dst)方法返回值的意义
     * 1）正数：代表从通道的当前位置向ByteBuffer缓冲区中读的字节个数。
     * 2）0：代表从通道中没有读取任何的数据，也就是0字节，有可能发生的情况就是缓冲区中没有remainging剩余空间了。
     * 3）-1：代表到达流的末端。
     */
    @Test
    public void test1() {

        try (
            FileInputStream fis = new FileInputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/e.txt");
            FileChannel channel = fis.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.allocate(4);
            // 正常读取4字节到buffer
            int result = channel.read(byteBuffer);
            log.info("return:{}", result);
            // 判定buffer满了
            result = channel.read(byteBuffer);
            log.info("return:{}", result);
            // 清除buffer
            byteBuffer.clear();
            // 判定channel中的数据读取完了
            result = channel.read(byteBuffer);
            log.info("return:{}", result);

            byteBuffer.rewind();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            String str = new String(bytes);
            log.info("str:{}", str);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testRead() {
        int position = 0;
        while (true) {
            try (
                FileInputStream fis = new FileInputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/d.txt");
                FileChannel channel = fis.getChannel()
            ){
                channel.position(position);
                ByteBuffer byteBuffer = ByteBuffer.allocate(3);
                int result = channel.read(byteBuffer);
                switch (result) {
                    case 0:
                        // buffer满了
                        byteBuffer.rewind();
                        break;
                    case -1:
                        // channel写完了
                        try {
                            Thread.sleep(2000);
                            break;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    default:
                        byteBuffer.rewind();
                        byte[] bytes = new byte[byteBuffer.remaining()];
                        byteBuffer.get(bytes);
                        String str = new String(bytes);
                        log.info("str:{}", str);
                        position = result;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 验证int read(ByteBuffer dst)方法是从通道的当前位置开始读取的
     */
    @Test
    public void test2() {
        try (
            FileInputStream fis = new FileInputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/d.txt");
            FileChannel channel = fis.getChannel()
        ){
            channel.position(1);
            ByteBuffer byteBuffer = ByteBuffer.allocate(2);
            channel.read(byteBuffer);
            String result = new String(byteBuffer.array());
            log.info("result:{}", result);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证int read(ByteBuffer dst)方法将字节放入ByteBuffer当前位置
     */
    @Test
    public void test3() {
        try (
            FileInputStream fis = new FileInputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/d.txt");
            FileChannel channel = fis.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.allocate(5);
            byteBuffer.position(2);
            channel.read(byteBuffer);
            String result = new String(byteBuffer.array());
            log.info("result:{}", result);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Data
    @Accessors(chain = true)
    class ReadCallable implements Callable<String> {

        private FileChannel channel;

        @Override
        public String call() throws Exception {
            ByteBuffer byteBuffer = ByteBuffer.allocate(3);
            int count = channel.read(byteBuffer);
            if (count != -1) {
                byteBuffer.flip();
                log.info("string:{}", new String(byteBuffer.array()));
            }
            return null;
        }
    }

    /**
     * 验证int read(ByteBuffer dst)方法具有同步特性
     */
    @Test
    public void test4() {

        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/c.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 5, 1, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(10), r -> {
                Thread thread = new Thread(r);
                thread.setName("th-");
                return thread;
            }, (r, executor) -> log.error("out off queue size"));

            List<Callable<String>> callables = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                callables.add(new ReadCallable().setChannel(channel));
            }
            threadPoolExecutor.invokeAll(callables);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证int read(ByteBuffer dst)方法从通道读取的数据大于缓冲区容量
     */
    @Test
    public void test5() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/e.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.allocate(3);
            long beginPosition = channel.position();
            channel.read(byteBuffer);
            long endPosition = channel.position();
            log.info("beginL:{},end:{}", beginPosition, endPosition);
            log.info("result:{}", new String(byteBuffer.array()));
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证int read(ByteBuffer dst)方法从通道读取的字节放入缓冲区的remaining空间中
     */
    @Test
    public void test6() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/e.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.allocate(30);
            byteBuffer.position(10);
            byteBuffer.limit(12);
            long beginPosition = channel.position();
            channel.read(byteBuffer);
            long endPosition = channel.position();
            log.info("beginL:{},end:{}", beginPosition, endPosition);
            log.info("result:{}", new String(byteBuffer.array()));
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

}
