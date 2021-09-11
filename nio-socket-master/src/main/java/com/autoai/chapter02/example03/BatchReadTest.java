package com.autoai.chapter02.example03;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: zhukaishengy
 * @Date: 2020/5/29 15:00
 * @Description:
 */
@Slf4j
public class BatchReadTest {

    /**
     * 验证long read(ByteBuffer[] dsts)方法返回值的意义
     */
    @Test
    public void test1() {
        try (
            FileInputStream fos = new FileInputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/g.txt");
            FileChannel channel = fos.getChannel()
        ){
            long size = channel.size();
            log.info("channel size{}", size);
            ByteBuffer byteBuffer1 = ByteBuffer.allocate(6);
            ByteBuffer byteBuffer2 = ByteBuffer.allocate(6);
            ByteBuffer[] buffers = new ByteBuffer[]{byteBuffer1, byteBuffer2};
            long read1 = channel.read(buffers);
            // 12
            log.info("read1:{}", read1);
            byteBuffer1.clear();
            byteBuffer2.clear();
            long read2 = channel.read(buffers);
            // 1
            log.info("read2:{}", read2);
            byteBuffer1.clear();
            byteBuffer2.clear();
            long read3 = channel.read(buffers);
            // -1
            log.info("read3:{}", read3);

        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证long read(ByteBuffer[] dsts)方法是从通道的当前位置开始读取的
     */
    @Test
    public void test2() {
        try (
            FileInputStream fos = new FileInputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/g.txt");
            FileChannel channel = fos.getChannel()
        ){
            channel.position(3);
            ByteBuffer byteBuffer1 = ByteBuffer.allocate(3);
            ByteBuffer byteBuffer2 = ByteBuffer.allocate(7);
            ByteBuffer[] buffers = new ByteBuffer[]{byteBuffer1, byteBuffer2};
            long read1 = channel.read(buffers);
            log.info("read1:{}", read1);
            log.info("bb1:{}, bb2:{}", new String(byteBuffer1.array(), Charset.forName("utf-8")), new String(byteBuffer2.array()));

        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证long read(ByteBuffer[] dsts)方法将字节放入ByteBuffer当前位置
     */
    @Test
    public void test3() {
        try (
            FileInputStream fos = new FileInputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/g.txt");
            FileChannel channel = fos.getChannel()
        ){
            ByteBuffer byteBuffer1 = ByteBuffer.allocate(7);
            ByteBuffer byteBuffer2 = ByteBuffer.allocate(8);
            ByteBuffer[] buffers = new ByteBuffer[]{byteBuffer1, byteBuffer2};
            byteBuffer1.position(1);
            byteBuffer2.position(1);
            long read1 = channel.read(buffers);
            log.info("read1:{}", read1);
            log.info("bb1:{}, bb2:{}", new String(byteBuffer1.array(), Charset.forName("utf-8")), new String(byteBuffer2.array()));

        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Data
    class ReadsCallable implements Callable<String> {

        private FileChannel fileChannel;
        private ByteBuffer byteBuffer1;
        private ByteBuffer byteBuffer2;

        public ReadsCallable(FileChannel fileChannel, ByteBuffer byteBuffer1, ByteBuffer byteBuffer2) {
            this.fileChannel = fileChannel;
            this.byteBuffer1 = byteBuffer1;
            this.byteBuffer2 = byteBuffer2;
        }

        @Override
        public String call() throws Exception {
            byteBuffer1.clear();
            byteBuffer2.clear();
            ByteBuffer[] byteBuffers = new ByteBuffer[]{byteBuffer1, byteBuffer2};
            long read = fileChannel.read(byteBuffers);
            if (read != -1) {
                log.info("thread {} byteBuffer1 {} byteBuffer2 {}", Thread.currentThread().getName(), new String(byteBuffer1.array(), Charset.forName("utf-8")),
                        new String(byteBuffer2.array(), Charset.forName("utf-8")));
            }
            return null;
        }
    }

    /**
     * 验证long read(ByteBuffer[] dsts)方法具有同步特性
     */
    @Test
    public void test4() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/f.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            // 创建线程池
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 5, 5, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(100), new ThreadFactory() {
                private AtomicInteger count = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread th = new Thread(r);
                    th.setName("th-" + count.getAndIncrement());
                    return th;
                }
            }, (r, executor) -> log.error("queue is full"));

            List<Callable<String>> callables = new ArrayList<>();
            ByteBuffer byteBuffer1 = ByteBuffer.allocate(3);
            ByteBuffer byteBuffer2 = ByteBuffer.allocate(3);
            for (int i = 0; i < 100; i++) {
                ReadsCallable readsCallable = new ReadsCallable(channel, byteBuffer1, byteBuffer2);
//                threadPoolExecutor.submit(readsCallable);
                callables.add(readsCallable);
            }
            threadPoolExecutor.invokeAll(callables);
            log.info("main end ...");
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

}
