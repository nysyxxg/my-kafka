package com.autoai.chapter05.example04;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

/**
 * @Author: zhukaishengy
 * @Date: 2020/7/13 14:46
 * @Description: 管道测试
 */
@Slf4j
public class PipeTest {

    @Test
    public void test() {

        try {
            Pipe pipe = Pipe.open();
            Pipe.SourceChannel sourceChannel = pipe.source();
            Pipe.SinkChannel sinkChannel = pipe.sink();

            ExecutorService executorService = Executors.newFixedThreadPool(2);

            // 线程同步器
            Phaser phaser = new Phaser(3);

            executorService.submit(() -> {
                try {
                    sinkChannel.write(ByteBuffer.wrap("123".getBytes()));
                    phaser.arrive();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            executorService.submit(() -> {
                try {
                    sinkChannel.write(ByteBuffer.wrap("456".getBytes()));
                    phaser.arrive();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            phaser.arriveAndAwaitAdvance();

            ByteBuffer byteBuffer = ByteBuffer.allocate(6);
            sourceChannel.read(byteBuffer);

            log.info("read:{}", new String(byteBuffer.array()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
