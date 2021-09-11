package com.autoai.chapter05.example02;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author: zhukaishengy
 * @Date: 2020/7/8 15:55
 * @Description:
 */
@Slf4j
public class Selector2Test {

    @Test
    public void test1() {
        try (
            // 声明资源
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            // 绑定端口，设置非阻塞式IO，注册到多路复用器并监听OP_ACCEPT事件
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    if (selectionKey.isAcceptable()) {
                        ServerSocketChannel channel = (ServerSocketChannel)selectionKey.channel();
                        SocketChannel socketChannel = channel.accept();
                        ByteBuffer byteBuffer = ByteBuffer.allocate(9);
                        while (socketChannel.read(byteBuffer) != -1) {
                            log.info("message:{}", new String(byteBuffer.array(), 0, byteBuffer.position()));
                            byteBuffer.clear();
                        }
                    }
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        try (
            SocketChannel socketChannel = SocketChannel.open()
        ){
//            socketChannel.bind(new InetSocketAddress("localhost", 8080));
            socketChannel.connect(new InetSocketAddress("localhost", 9000));
            ByteBuffer byteBuffer = ByteBuffer.wrap("哈哈哈嘻嘻".getBytes());
            socketChannel.write(byteBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test3() {
        try (
            SocketChannel socketChannel = SocketChannel.open();
            Selector selector = Selector.open()
        ){
            socketChannel.connect(new InetSocketAddress("localhost", 9000));
            socketChannel.configureBlocking(false);

            SelectionKey key1 = socketChannel.register(selector, SelectionKey.OP_CONNECT);
            SelectionKey key2 = socketChannel.register(selector, SelectionKey.OP_READ);
            SelectionKey key3 = socketChannel.register(selector, SelectionKey.OP_WRITE);

            log.info("OP_CONNECT:{}, OP_READ:{}, OP_WRITE:{}", key1.isConnectable(), key2.isReadable(), key3.isWritable());
            Assert.assertEquals(key1, key2);
            Assert.assertEquals(key2, key3);


            ByteBuffer byteBuffer = ByteBuffer.wrap("哈哈哈嘻嘻".getBytes());
            socketChannel.write(byteBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        try (
            Selector selector = Selector.open()
        ){
            log.info("isOpen:{}", selector.isOpen());
            selector.close();
            log.info("isOpen:{}", selector.isOpen());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * sun.nio.ch.KQueueSelectorProvider@424c0bc4
     */
    @Test
    public void test5() {
        try (
            Selector selector = Selector.open()
        ){
            SelectorProvider provider1 = selector.provider();
            log.info("provider1:{}", provider1);
            SelectorProvider provider2 = SelectorProvider.provider();
            log.info("provider2:{}", provider2);
            Assert.assertEquals(provider1, provider2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test6() {
        try (
            // 声明资源
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            // 绑定端口，设置非阻塞式IO，注册到多路复用器并监听OP_ACCEPT事件
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel2.bind(new InetSocketAddress("localhost", 9001));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel2.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            SelectionKey register = serverSocketChannel2.register(selector, SelectionKey.OP_ACCEPT);

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
                    // 移除 selectionKeys
                    iterator.remove();
                    // 移除 keys并注销其通道
                    register.cancel();
                    serverSocketChannel2.close();
                    log.info("{}, {}", register.isValid(), serverSocketChannel2.isOpen());
                    Thread.sleep(5000);
                    log.info("keys:{}, selectionKeys:{}", keys.size(), selectionKeys.size());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test7() {
        try (
            // 声明资源
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            // 绑定端口，设置非阻塞式IO，注册到多路复用器并监听OP_ACCEPT事件
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                // 阻塞固定时间
                selector.select(3000);
                log.info("waiting...");
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
                    // 移除 selectionKeys
                    iterator.remove();
                    log.info("keys:{}, selectionKeys:{}", keys.size(), selectionKeys.size());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test8() {
        try (
            // 声明资源
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            // 绑定端口，设置非阻塞式IO，注册到多路复用器并监听OP_ACCEPT事件
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                // 阻塞固定时间
                selector.selectNow();
                log.info("waiting...");
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
                    // 移除 selectionKeys
                    iterator.remove();
                    log.info("keys:{}, selectionKeys:{}", keys.size(), selectionKeys.size());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class WakeUpRunnable implements Runnable {

        Selector selector;

        public WakeUpRunnable(Selector selector) {
            this.selector = selector;
        }

        @Override
        public void run() {
            selector.wakeup();
        }
    }

    @Test
    public void test9() {
        try (
            // 声明资源
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            // 绑定端口，设置非阻塞式IO，注册到多路复用器并监听OP_ACCEPT事件
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                // 阻塞固定时间
                selector.select();
                log.info("selected...");
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
                    // 移除 selectionKeys
                    iterator.remove();
                    log.info("keys:{}, selectionKeys:{}", keys.size(), selectionKeys.size());
                    // 启动wakeup线程
                    ExecutorService executorService = Executors.newSingleThreadExecutor();
                    executorService.submit(new WakeUpRunnable(selector));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
