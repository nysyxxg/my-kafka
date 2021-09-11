package com.autoai.chapter02.example02;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @Author: zhukaishengy
 * @Date: 2020/5/7 09:33
 * @Description:
 */
@Slf4j
public class FileChannelWriteTest {

    /**
     * 验证int write(ByteBuffer src)方法是从通道的当前位置开始写入的
     */
    @Test
    public void test1() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/a.txt");
            FileChannel channel = fos.getChannel()
        ) {
            log.info("init position:{}", channel.position());
            ByteBuffer src = ByteBuffer.wrap("朱开生".getBytes(StandardCharsets.UTF_8));
            int r1 = channel.write(src);
            log.info("first op position:{},result:{}", channel.position(), r1);
            src.rewind();
            int r2 = channel.write(src, 12);
            log.info("second op position:{},result:{}", channel.position(), r2);
        } catch (FileNotFoundException e) {
            log.error("file not exist...");
        } catch (IOException e) {
            log.error("write error...");
        }
    }

    /**
     * 验证int write(ByteBuffer src)方法将ByteBuffer的remaining写入通道
     */
    @Test
    public void test2() {

        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/b.txt");
            FileChannel channel = fos.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.wrap("abcdefg".getBytes());
            byteBuffer.position(1);
            byteBuffer.limit(4);
            channel.write(byteBuffer);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @AllArgsConstructor
    class MyCallable implements Callable<Integer> {

        private Integer period;
        private ByteBuffer byteBuffer;
        private FileChannel fileChannel;

        @Override
        public Integer call() throws Exception {
            Thread.sleep(period * 1000);
            byteBuffer.rewind();
            return fileChannel.write(byteBuffer);
        }
    }

    /**
     * 验证int write(ByteBuffer src)方法具有同步特性
     */
    @Test
    public void test3() {
        try (
            FileOutputStream fos = new FileOutputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/c.txt");
            FileChannel channel = fos.getChannel()
        ){
            ByteBuffer byteBuffer1 = ByteBuffer.wrap("abc".getBytes());
            ByteBuffer byteBuffer2 = ByteBuffer.wrap("123".getBytes());

            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 5, 1, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(10), r -> {
                Thread thread = new Thread(r);
                thread.setName("th-");
                return thread;
            }, (r, executor) -> log.error("out off queue size"));

            List<Callable<Integer>> callables = new ArrayList<>();
            for (int i = 0; i < (threadPoolExecutor.getCorePoolSize() + threadPoolExecutor.getQueue().remainingCapacity()) / 2; i++) {
                callables.add(new MyCallable(i, byteBuffer1, channel));
                callables.add(new MyCallable(i, byteBuffer2, channel));
            }
            threadPoolExecutor.invokeAll(callables);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

}
