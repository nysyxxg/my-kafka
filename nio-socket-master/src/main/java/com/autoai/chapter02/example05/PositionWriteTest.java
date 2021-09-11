package com.autoai.chapter02.example05;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/1 13:08
 * @Description:
 */
@Slf4j
public class PositionWriteTest {

    /**
     * 验证write(ByteBuffer src, long position)方法是从通道的指定位置开始写入的
     */
    @Test
    public void test1() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt");
            FileChannel channel = fos.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.wrap("abcdefg".getBytes());
            channel.write(byteBuffer);
            byteBuffer.rewind();
            channel.write(byteBuffer, 6);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证write(ByteBuffer src, long position)方法将ByteBuffer的remaining写入通道
     */
    @Test
    public void test2() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt");
            FileChannel channel = fos.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.wrap("abcdefg".getBytes());
            byteBuffer.position(2);
            channel.write(byteBuffer, 1);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Data
    @AllArgsConstructor
    class PositionWriteCallable implements Callable<String> {

        private FileChannel fileChannel;
        private ByteBuffer byteBuffer1;
        private ByteBuffer byteBuffer2;

        @Override
        public String call() throws Exception {
            byteBuffer1.clear();
            byteBuffer2.clear();
            fileChannel.write(byteBuffer1, 0);
            fileChannel.write(byteBuffer2, 0);
            return null;
        }
    }

    /**
     * 验证write(ByteBuffer src, long position)方法具有同步特性
     * 文件在position=0处写入的为最后执行线程写入的
     */
    @Test
    public void test3() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt");
            FileChannel channel = fos.getChannel()
        ){
            // 创建线程池
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 5, 5, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(100), new ThreadFactory() {
                private AtomicInteger count = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread th = new Thread(r);
                    th.setName("zks-" + count.getAndIncrement());
                    return th;
                }
            }, (r, executor) -> log.error("queue is full"));

            List<Callable<String>> callables = new ArrayList<>();

            AtomicInteger position = new AtomicInteger(0);

            for (int i = 0; i < 20; i++) {
                ByteBuffer byteBuffer1 = ByteBuffer.wrap("abc".getBytes());
                ByteBuffer byteBuffer2 = ByteBuffer.wrap("123".getBytes());
                Callable<String> callable = new PositionWriteCallable(channel, byteBuffer1, byteBuffer2);
                callables.add(callable);
            }
            threadPoolExecutor.invokeAll(callables);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证write(ByteBuffer src, long position)方法中的position不变性
     */
    @Test
    public void test4() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt");
            FileChannel channel = fos.getChannel()
        ){
            log.info("position:{}", channel.position());
            ByteBuffer byteBuffer = ByteBuffer.wrap("abcdefg".getBytes());
            channel.write(byteBuffer, 6);
            log.info("position:{}", channel.position());
        } catch (Exception e) {
            log.error(e.toString());
        }
    }
}
