package com.autoai.chapter05.example02;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @Author: zhukaishengy
 * @Date: 2020/7/8 13:11
 * @Description: 多路复用器
 */
@Slf4j
public class SelectorTest {

    @Test
    public void test1() {
        try (
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);
            // 将OP_ACCEPT事件当成感兴趣的事件
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            int select = selector.select();
            log.info("selectCount:{}", select);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        try (
            SocketChannel socketChannel1 = SocketChannel.open()
        ){
            socketChannel1.bind(new InetSocketAddress("localhost", 8080));
            socketChannel1.connect(new InetSocketAddress("localhost", 9000));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test3() {
        try (
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);
            // 将OP_ACCEPT事件当成感兴趣的事件
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            while (true) {
                int selectKey = selector.select();
                Set<SelectionKey> keys = selector.keys();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                log.info("selectKey:{}, keys:{}, selectionKeys:{}", selectKey, keys.size(), selectionKeys.size());
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        try (
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);
            // 将OP_ACCEPT事件当成感兴趣的事件
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            while (true) {
                int selectKey = selector.select();
                Set<SelectionKey> keys = selector.keys();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                log.info("selectKey:{}, keys:{}, selectionKeys:{}", selectKey, keys.size(), selectionKeys.size());
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    ServerSocketChannel channel = (ServerSocketChannel)selectionKey.channel();
                    // 将accept事件消化处理
                    channel.accept();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test5() {
        try (
            ServerSocketChannel serverSocketChannel1 = ServerSocketChannel.open();
            ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            serverSocketChannel1.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel2.bind(new InetSocketAddress("localhost", 9001));
            serverSocketChannel1.configureBlocking(false);
            serverSocketChannel2.configureBlocking(false);
            // 将OP_ACCEPT事件当成感兴趣的事件
            serverSocketChannel1.register(selector, SelectionKey.OP_ACCEPT);
            serverSocketChannel2.register(selector, SelectionKey.OP_ACCEPT);
            while (true) {
                int selectKey = selector.select();
                Set<SelectionKey> keys = selector.keys();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                log.info("selectKey:{}, keys:{}, selectionKeys:{}", selectKey, keys.size(), selectionKeys.size());
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    ServerSocketChannel channel = (ServerSocketChannel)selectionKey.channel();
                    InetSocketAddress localAddress = (InetSocketAddress)channel.getLocalAddress();
                    int port = localAddress.getPort();
                    log.info("绑定ServerSocketChannel port:{}", port);
                    // 将accept事件消化处理
                    SocketChannel socketChannel = channel.accept();
                    if (socketChannel == null) {
                        log.info("重复消费。。。");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test6() {
        try (
            SocketChannel socketChannel1 = SocketChannel.open()
        ){
            socketChannel1.bind(new InetSocketAddress("localhost", 8081));
            socketChannel1.connect(new InetSocketAddress("localhost", 9001));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test7() {
        try (
            ServerSocketChannel serverSocketChannel1 = ServerSocketChannel.open();
            ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            serverSocketChannel1.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel2.bind(new InetSocketAddress("localhost", 9001));
            serverSocketChannel1.configureBlocking(false);
            serverSocketChannel2.configureBlocking(false);
            // 将OP_ACCEPT事件当成感兴趣的事件
            serverSocketChannel1.register(selector, SelectionKey.OP_ACCEPT);
            serverSocketChannel2.register(selector, SelectionKey.OP_ACCEPT);
            while (true) {
                int selectKey = selector.select();
                Set<SelectionKey> keys = selector.keys();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                log.info("selectKey:{}, keys:{}, selectionKeys:{}", selectKey, keys.size(), selectionKeys.size());
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    ServerSocketChannel channel = (ServerSocketChannel)selectionKey.channel();
                    InetSocketAddress localAddress = (InetSocketAddress)channel.getLocalAddress();
                    int port = localAddress.getPort();
                    log.info("绑定ServerSocketChannel port:{}", port);
                    // 将accept事件消化处理
                    SocketChannel socketChannel = channel.accept();
                    if (socketChannel == null) {
                        log.info("重复消费。。。");
                    }
                    // 消费后移除selectionKeys set
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test8() {
        try (
            ServerSocketChannel serverSocketChannel1 = ServerSocketChannel.open();
            ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            serverSocketChannel1.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel2.bind(new InetSocketAddress("localhost", 9001));
            serverSocketChannel1.configureBlocking(false);
            serverSocketChannel2.configureBlocking(false);
            // 将OP_ACCEPT事件当成感兴趣的事件
            serverSocketChannel1.register(selector, SelectionKey.OP_ACCEPT);
            serverSocketChannel2.register(selector, SelectionKey.OP_ACCEPT);
            Thread.sleep(5000);
            while (true) {
                int selectKey = selector.select();
                Set<SelectionKey> keys = selector.keys();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                log.info("selectKey:{}, keys:{}, selectionKeys:{}", selectKey, keys.size(), selectionKeys.size());
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    ServerSocketChannel channel = (ServerSocketChannel)selectionKey.channel();
                    // 将accept事件消化处理
                    channel.accept();
                    // 消费后移除selectionKeys set
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test9() {
        try (
            Socket socket1 = new Socket("localhost", 9000);
            Socket socket2 = new Socket("localhost", 9001)
        ) {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
