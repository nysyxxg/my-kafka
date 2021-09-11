package com.autoai.chapter05.example02;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author: zhukaishengy
 * @Date: 2020/7/8 17:52
 * @Description:
 */
@Slf4j
public class Selector3Test {

    class CancelRunnable implements Runnable {

        SelectionKey selectionKey;

        public CancelRunnable(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }

        @Override
        public void run() {
            selectionKey.cancel();
        }
    }

    class CloseRunnable implements Runnable {

        ServerSocketChannel serverSocketChannel;

        public CloseRunnable(ServerSocketChannel serverSocketChannel) {
            this.serverSocketChannel = serverSocketChannel;
        }

        @Override
        public void run() {
            try {
                serverSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class SocketRunnable implements Runnable {

        Integer port;

        public SocketRunnable(Integer port) {
            this.port = port;
        }

        @Override
        public void run() {
            try {
                SocketChannel socketChannel = SocketChannel.open();
                socketChannel.connect(new InetSocketAddress("localhost", port));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void test1() {
        try (
            // 声明资源
            ServerSocketChannel serverSocketChannel1 = ServerSocketChannel.open();
            ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            // 绑定端口，设置非阻塞式IO，注册到多路复用器并监听OP_ACCEPT事件
            serverSocketChannel1.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel2.bind(new InetSocketAddress("localhost", 9001));
            serverSocketChannel1.configureBlocking(false);
            serverSocketChannel2.configureBlocking(false);
            SelectionKey selectionKey1 = serverSocketChannel1.register(selector, SelectionKey.OP_ACCEPT);
            serverSocketChannel2.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                selector.select();
                Set<SelectionKey> keys = selector.keys();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    log.info("keys:{}, selectionKeys:{}", keys.size(), selectionKeys.size());
                    SelectionKey selectionKey = iterator.next();
                    if (selectionKey.isAcceptable()) {
                        ServerSocketChannel channel = (ServerSocketChannel)selectionKey.channel();
                        SocketChannel socketChannel = channel.accept();
                        // 读数据
                        ByteBuffer byteBuffer = ByteBuffer.allocate(9);
                        while (socketChannel.read(byteBuffer) != -1) {
                            log.info("message:{}", new String(byteBuffer.array(), 0, byteBuffer.position()));
                            byteBuffer.clear();
                        }
                    }
                }
                // 移除 selectionKeys
                iterator.remove();
                log.info("keys:{}, selectionKeys:{}", keys.size(), selectionKeys.size());
                // 启动cancel线程
                ExecutorService executorService1 = Executors.newSingleThreadExecutor();
                executorService1.submit(new CancelRunnable(selectionKey1));

                // 启动2个客户端线程
                ExecutorService executorService = Executors.newFixedThreadPool(3);
                executorService.submit(new SocketRunnable(9000));
                executorService.submit(new SocketRunnable(9001));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        try (
            // 声明资源
            ServerSocketChannel serverSocketChannel1 = ServerSocketChannel.open();
            ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            // 绑定端口，设置非阻塞式IO，注册到多路复用器并监听OP_ACCEPT事件
            serverSocketChannel1.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel2.bind(new InetSocketAddress("localhost", 9001));
            serverSocketChannel1.configureBlocking(false);
            serverSocketChannel2.configureBlocking(false);
            SelectionKey selectionKey1 = serverSocketChannel1.register(selector, SelectionKey.OP_ACCEPT);
            serverSocketChannel2.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                selector.select();
                Set<SelectionKey> keys = selector.keys();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    log.info("keys:{}, selectionKeys:{}", keys.size(), selectionKeys.size());
                    SelectionKey selectionKey = iterator.next();
                    if (selectionKey.isAcceptable()) {
                        ServerSocketChannel channel = (ServerSocketChannel)selectionKey.channel();
                        SocketChannel socketChannel = channel.accept();
                        // 读数据
                        ByteBuffer byteBuffer = ByteBuffer.allocate(9);
                        while (socketChannel.read(byteBuffer) != -1) {
                            log.info("message:{}", new String(byteBuffer.array(), 0, byteBuffer.position()));
                            byteBuffer.clear();
                        }
                    }
                }
                // 移除 selectionKeys
                iterator.remove();
                log.info("keys:{}, selectionKeys:{}", keys.size(), selectionKeys.size());
                // 启动close线程
                ExecutorService executorService1 = Executors.newSingleThreadExecutor();
                executorService1.submit(new CloseRunnable(serverSocketChannel2));

                // 启动2个客户端线程
                ExecutorService executorService = Executors.newFixedThreadPool(3);
                executorService.submit(new SocketRunnable(9000));
                executorService.submit(new SocketRunnable(9001));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test3() throws InterruptedException {
        // 启动2个客户端线程
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        executorService.submit(new SocketRunnable(9000));
        executorService.submit(new SocketRunnable(9001));
        // 异步执行完成
        Thread.sleep(5000);
    }

    @Test
    public void test4() {
        try {
            Selector selector = Selector.open();
            Set<SelectionKey> keys = selector.keys();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            log.info("keys:{}, selectionKeys:{}", keys.size(), selectionKeys.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test5() {
        try (
            // 声明资源
            ServerSocketChannel serverSocketChannel1 = ServerSocketChannel.open();
            ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            // 绑定端口，设置非阻塞式IO，注册到多路复用器并监听OP_ACCEPT事件
            serverSocketChannel1.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel2.bind(new InetSocketAddress("localhost", 9001));
            serverSocketChannel1.configureBlocking(false);
            serverSocketChannel2.configureBlocking(false);
            serverSocketChannel1.register(selector, SelectionKey.OP_ACCEPT);
            serverSocketChannel2.register(selector, SelectionKey.OP_ACCEPT);

            Set<SelectionKey> keys = selector.keys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                iterator.remove();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * set 线程不安全
     * @throws InterruptedException
     */
    @Test
    public void test6() throws InterruptedException {
        Set<String> set = new HashSet<>();
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        for (String s : set) {
            Thread.sleep(1000);
            executorService.submit(() -> set.remove("a"));
        }
    }
}
