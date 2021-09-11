package com.autoai.chapter02.example04;

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
 * @Date: 2020/6/1 09:58
 * @Description: 部分批量写操作
 */
@Slf4j
public class PartBatchWriteTest {

    /**
     * 验证long write(ByteBuffer[] srcs, int offset, int length)方法是从通道的当前位置开始写入的
     */
    @Test
    public void test1() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/h.txt");
            FileChannel channel = fos.getChannel()
        ){
            ByteBuffer byteBuffer1 = ByteBuffer.wrap("ab".getBytes());
            ByteBuffer byteBuffer2 = ByteBuffer.wrap("cd".getBytes());
            ByteBuffer[] buffers = new ByteBuffer[]{byteBuffer1, byteBuffer2};
            channel.position(1);
            // 偏移量和len级别都是数组的元素
            channel.write(buffers, 0, 2);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证long write(ByteBuffer[] srcs, int offset, int length)方法将ByteBuffer的remaining写入通道
     */
    @Test
    public void test2() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/h.txt");
            FileChannel channel = fos.getChannel()
        ){
            ByteBuffer byteBuffer1 = ByteBuffer.wrap("ab".getBytes());
            ByteBuffer byteBuffer2 = ByteBuffer.wrap("cd".getBytes());
            ByteBuffer[] buffers = new ByteBuffer[]{byteBuffer1, byteBuffer2};
            byteBuffer1.position(1);
            byteBuffer2.position(1);
            channel.write(buffers, 0, 2);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Data
    class PartBatchWriteCallable implements Callable<String> {

        private FileChannel fileChannel;
        private ByteBuffer[] byteBuffers;

        public PartBatchWriteCallable(FileChannel fileChannel, ByteBuffer... byteBuffers) {
            this.fileChannel = fileChannel;
            this.byteBuffers = byteBuffers;
        }

        @Override
        public String call() throws Exception {
            for (ByteBuffer byteBuffer : byteBuffers) {
                byteBuffer.clear();
            }
            fileChannel.write(byteBuffers, 0, 2);
            log.info("{} worker end...", Thread.currentThread().getName());
            return null;
        }
    }

    /**
     * 验证long write(ByteBuffer[] srcs, int offset, int length)方法具有同步特性
     */
    @Test
    public void test3() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/h.txt");
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

            for (int i = 0; i < 20; i++) {
                ByteBuffer byteBuffer1 = ByteBuffer.wrap("abc".getBytes());
                ByteBuffer byteBuffer2 = ByteBuffer.wrap("123".getBytes());
                Callable<String> callable = new PartBatchWriteCallable(channel, byteBuffer1, byteBuffer2);
                callables.add(callable);
            }
            threadPoolExecutor.invokeAll(callables);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }
}
