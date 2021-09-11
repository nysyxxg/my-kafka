package com.autoai.chapter02.example03;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: zhukaishengy
 * @Date: 2020/5/28 16:55
 * @Description:
 */
@Slf4j
public class BatchWriteTest {

    /**
     * 验证long write(ByteBuffer[] srcs)方法是从通道的当前位置开始写入的
     */
    @Test
    public void test1() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/f.txt");
            FileChannel channel = fos.getChannel()
        ){
            channel.position(3);
            ByteBuffer byteBuffer1 = ByteBuffer.wrap("哈哈".getBytes(StandardCharsets.UTF_8));
            ByteBuffer byteBuffer2 = ByteBuffer.wrap("嘻嘻".getBytes(StandardCharsets.UTF_8));
            ByteBuffer[] buffers = new ByteBuffer[]{byteBuffer1, byteBuffer2};
            channel.write(buffers);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证long write(ByteBuffer[] srcs)方法将ByteBuffer的remaining写入通道
     */
    @Test
    public void test2() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/f.txt");
            FileChannel channel = fos.getChannel()
        ){
            channel.position(3);
            ByteBuffer byteBuffer1 = ByteBuffer.wrap("哈哈".getBytes(StandardCharsets.UTF_8));
            ByteBuffer byteBuffer2 = ByteBuffer.wrap("嘻嘻".getBytes(StandardCharsets.UTF_8));
            byteBuffer1.position(3);
            byteBuffer2.position(3);
            ByteBuffer[] buffers = new ByteBuffer[]{byteBuffer1, byteBuffer2};
            channel.write(buffers);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Data
    class WritesCallable implements Callable<String> {

        private FileChannel fileChannel;
        private ByteBuffer[] byteBuffers;

        public WritesCallable(FileChannel fileChannel, ByteBuffer... byteBuffers) {
            this.fileChannel = fileChannel;
            this.byteBuffers = byteBuffers;
        }

        @Override
        public String call() throws Exception {
            for (ByteBuffer byteBuffer : byteBuffers) {
                byteBuffer.clear();
            }
            fileChannel.write(byteBuffers);
            log.info("{} worker end...", Thread.currentThread().getName());
            return null;
        }
    }

    /**
     * 验证long write(ByteBuffer[] srcs)方法具有同步特性
     */
    @Test
    public void test3() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/f.txt");
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

            // build buffer
            ByteBuffer byteBuffer1 = ByteBuffer.wrap("abc".getBytes(StandardCharsets.UTF_8));
            ByteBuffer byteBuffer2 = ByteBuffer.wrap("123".getBytes(StandardCharsets.UTF_8));

            List<Callable<String>> callables = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Callable<String> callable = new WritesCallable(channel, byteBuffer1, byteBuffer2);
                callables.add(callable);
            }
            threadPoolExecutor.invokeAll(callables);
            log.info("{} main end...", Thread.currentThread().getName());
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

}
