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

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class AsynchronousChannelTest {

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
                        Integer rcvBuffer = channel.getOption(StandardSocketOptions.SO_RCVBUF);
                        log.info("thread:{}", Thread.currentThread().getName());
                        log.info("rcvBuffer:{}", rcvBuffer);
                        ByteBuffer byteBuffer = ByteBuffer.allocate(rcvBuffer);
                        String message = null;
                        while (channel.read(byteBuffer).get() > 0) {
                            message = new String(byteBuffer.array(), 0, byteBuffer.position());
                        }
                        log.info("message:{}", message);
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
                    Future<Integer> future = asynchronousSocketChannel.write(ByteBuffer.wrap("123".getBytes()));
                    try {
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
    public void server2() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            AsynchronousServerSocketChannel asynchronousServerSocketChannel = AsynchronousServerSocketChannel.open();
            asynchronousServerSocketChannel.bind(new InetSocketAddress("localhost", 9000));

            Future<AsynchronousSocketChannel> future = asynchronousServerSocketChannel.accept();
            AsynchronousSocketChannel asynchronousSocketChannel = future.get();

            Integer rcvBuffer = asynchronousSocketChannel.getOption(StandardSocketOptions.SO_RCVBUF);
            log.info("thread:{}", Thread.currentThread().getName());
            log.info("rcvBuffer:{}", rcvBuffer);
            ByteBuffer byteBuffer = ByteBuffer.allocate(rcvBuffer);
            String message = null;
            while (asynchronousSocketChannel.read(byteBuffer).get() > 0) {
                message = new String(byteBuffer.array(), 0, byteBuffer.position());
            }
            log.info("message:{}", message);
            // 阻塞主线程
            countDownLatch.await();
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client2() {
        try {
            AsynchronousSocketChannel asynchronousSocketChannel = AsynchronousSocketChannel.open();
            Future<Void> future = asynchronousSocketChannel.connect(new InetSocketAddress("localhost", 9000));
            future.get();
            Future<Integer> future1 = asynchronousSocketChannel.write(ByteBuffer.wrap("123".getBytes()));
            Integer size = future1.get();
            log.info("size:{}", size);

        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test1() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            AsynchronousServerSocketChannel asynchronousServerSocketChannel = AsynchronousServerSocketChannel.open();
            asynchronousServerSocketChannel.bind(new InetSocketAddress("localhost", 9000));

            Future<AsynchronousSocketChannel> future = asynchronousServerSocketChannel.accept();
            AsynchronousSocketChannel asynchronousSocketChannel = future.get();

            ByteBuffer byteBuffer = ByteBuffer.allocate(10);
            // 多次读，会阻塞
            asynchronousSocketChannel.read(byteBuffer);
            asynchronousSocketChannel.read(byteBuffer);
            // 阻塞主线程
            countDownLatch.await();
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
