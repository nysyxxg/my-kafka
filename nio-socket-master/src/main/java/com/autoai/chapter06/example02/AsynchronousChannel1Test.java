package com.autoai.chapter06.example02;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @Author: zhukaishengy
 * @Date: 2020/7/13 18:42
 * @Description:
 */
@Slf4j
public class AsynchronousChannel1Test {

    @Test
    public void server1() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            AsynchronousServerSocketChannel asynchronousSocketChannel = AsynchronousServerSocketChannel.open();
            asynchronousSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            asynchronousSocketChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Object>() {
                @Override
                public void completed(AsynchronousSocketChannel channel, Object attachment) {
                    try {
                        // 重新启动accept
                        asynchronousSocketChannel.accept(attachment, this);
                        Integer rcvBuffer = channel.getOption(StandardSocketOptions.SO_RCVBUF);
                        log.info("thread:{}", Thread.currentThread().getName());
                        log.info("rcvBuffer:{}", rcvBuffer);
                        ByteBuffer byteBuffer = ByteBuffer.allocate(rcvBuffer);

                        channel.read(byteBuffer, 5, TimeUnit.SECONDS, null, new CompletionHandler<Integer, Object>() {
                            @Override
                            public void completed(Integer result, Object attachment) {
                                String message = new String(byteBuffer.array(), 0, byteBuffer.position());
                                log.info("message:{}", message);
                            }

                            @Override
                            public void failed(Throwable exc, Object attachment) {
                                log.error("ex:{}", exc);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    log.error("e:{}", exc);
                }
            });
            // 阻塞主线程
            countDownLatch.await();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client1() {
        try {
            CountDownLatch countDownLatch = new CountDownLatch(1);

            AsynchronousSocketChannel asynchronousSocketChannel = AsynchronousSocketChannel.open();
            asynchronousSocketChannel.connect(new InetSocketAddress("localhost", 9000), null, new CompletionHandler<Void, Object>() {
                @Override
                public void completed(Void result, Object attachment) {
                    try {
                        // 模拟客户端超时
                        Thread.sleep(6000);
                        Future<Integer> future = asynchronousSocketChannel.write(ByteBuffer.wrap("123".getBytes()));
                        log.info("bytes:{}", future.get());
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                    countDownLatch.countDown();
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    log.error("e:{}", exc);
                }
            });
            countDownLatch.await();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client2() {
        try {
            CountDownLatch countDownLatch = new CountDownLatch(1);

            AsynchronousSocketChannel asynchronousSocketChannel = AsynchronousSocketChannel.open();
            asynchronousSocketChannel.connect(new InetSocketAddress("localhost", 9000), null, new CompletionHandler<Void, Object>() {
                @Override
                public void completed(Void result, Object attachment) {
                    asynchronousSocketChannel.write(ByteBuffer.wrap("123".getBytes()), 5, TimeUnit.SECONDS, null,
                            new CompletionHandler<Integer, Object>() {
                        @Override
                        public void completed(Integer result, Object attachment) {
                            log.info("size:{}", result);
                        }

                        @Override
                        public void failed(Throwable exc, Object attachment) {
                            log.error("e:{}", exc);
                        }
                    });
                    countDownLatch.countDown();
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    log.error("e:{}", exc);
                }
            });
            countDownLatch.await();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
