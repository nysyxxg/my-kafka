package com.autoai.chapter05.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/24 14:59
 * @Description:
 */
@Slf4j
public class SelectorTest {

    @Test
    public void test1() {
        try {
            Selector selector = Selector.open();
            log.info("selector:{}", selector);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            // 要注册到选择器必须设置channel为非阻塞模式
            serverSocketChannel.configureBlocking(false);

            Selector selector = Selector.open();
            SelectionKey selectionKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            log.info("selectionKey:{}", selectionKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test3() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);
            log.info("isRegistered:{}", serverSocketChannel.isRegistered());

            Selector selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            log.info("isRegistered:{}", serverSocketChannel.isRegistered());
            selector.close();
            log.info("isRegistered:{}", serverSocketChannel.isRegistered());
            serverSocketChannel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            log.info("isOpen:{}", serverSocketChannel.isOpen());
            serverSocketChannel.close();
            log.info("isOpen:{}", serverSocketChannel.isOpen());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test5() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Object lock = serverSocketChannel.blockingLock();
            log.info("lock:{}", lock);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test6() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

            Set<SocketOption<?>> channelSupportOptions = serverSocketChannel.supportedOptions();
            List<String> list = channelSupportOptions.stream().map(socketOption -> socketOption.name() + "-" + socketOption.type())
                    .collect(Collectors.toList());
            log.info("list:{}", list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Test
    public void test7() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            // 128K
            Integer rcvBuffer = serverSocketChannel.getOption(StandardSocketOptions.SO_RCVBUF);
            Boolean reuseAddr = serverSocketChannel.getOption(StandardSocketOptions.SO_REUSEADDR);
            log.info("rcvBuffer:{}, reuseAddr:{}", rcvBuffer, reuseAddr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test8() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            InetSocketAddress localAddress = (InetSocketAddress)serverSocketChannel.getLocalAddress();
            log.info("hostname:{}, port:{}", localAddress.getHostString(), localAddress.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test9() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            log.info("isBlocking:{}", serverSocketChannel.isBlocking());
            serverSocketChannel.configureBlocking(false);
            log.info("isBlocking:{}", serverSocketChannel.isBlocking());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test10() {
        try {
            // 创建 ServerSocketChannel，绑定端口
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);

            // 创建选择器
            Selector selector1 = Selector.open();
            Selector selector2 = Selector.open();

            // channel 注册到 selector
            SelectionKey register1 = serverSocketChannel.register(selector1, SelectionKey.OP_ACCEPT);
            SelectionKey register2 = serverSocketChannel.register(selector2, SelectionKey.OP_ACCEPT);

            // 获取key
            SelectionKey selectionKey1 = serverSocketChannel.keyFor(selector1);
            SelectionKey selectionKey2 = serverSocketChannel.keyFor(selector2);

            Assert.assertEquals(register1, selectionKey1);
            Assert.assertEquals(register2, selectionKey2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * KQueueSelectorProvider
     */
    @Test
    public void test11() {
        try {
            SelectorProvider provider1 = SelectorProvider.provider();
            log.info("provider1:{}", provider1);
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            SelectorProvider provider2 = serverSocketChannel.provider();
            log.info("provider2:{}", provider2);
            Assert.assertEquals(provider1, provider2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test12() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);

            Selector selector1 = Selector.open();
            Selector selector2 = Selector.open();

            SelectionKey selectionKey1 = serverSocketChannel.register(selector1, SelectionKey.OP_ACCEPT);
            SelectionKey selectionKey2 = serverSocketChannel.register(selector2, SelectionKey.OP_ACCEPT);

            Assert.assertNotEquals(selectionKey1, selectionKey2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test13() {
        try {
            ServerSocketChannel serverSocketChannel1 = ServerSocketChannel.open();
            ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();
            serverSocketChannel1.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel2.bind(new InetSocketAddress("localhost", 8000));
            serverSocketChannel1.configureBlocking(false);
            serverSocketChannel2.configureBlocking(false);

            Selector selector = Selector.open();

            SelectionKey selectionKey1 = serverSocketChannel1.register(selector, SelectionKey.OP_ACCEPT);
            SelectionKey selectionKey2 = serverSocketChannel2.register(selector, SelectionKey.OP_ACCEPT);

            Assert.assertNotEquals(selectionKey1, selectionKey2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test14() {
        try {
            ServerSocketChannel serverSocketChannel1 = ServerSocketChannel.open();
            ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();
            serverSocketChannel1.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel2.bind(new InetSocketAddress("localhost", 8000));
            serverSocketChannel1.configureBlocking(false);
            serverSocketChannel2.configureBlocking(false);

            Selector selector1 = Selector.open();
            Selector selector2 = Selector.open();

            SelectionKey selectionKey1 = serverSocketChannel1.register(selector1, SelectionKey.OP_ACCEPT);
            SelectionKey selectionKey2 = serverSocketChannel2.register(selector2, SelectionKey.OP_ACCEPT);

            Assert.assertNotEquals(selectionKey1, selectionKey2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test15() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);

            Selector selector = Selector.open();

            SelectionKey selectionKey1 = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            SelectionKey selectionKey2 = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            Assert.assertEquals(selectionKey1, selectionKey2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test16() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            SocketChannel socketChannel = SocketChannel.open();
            int serverValidOps = serverSocketChannel.validOps();
            int socketValidOps = socketChannel.validOps();
            log.info("server:{}, client:{}", serverValidOps, socketValidOps);
            log.info("OP_ACCEPT:{}", SelectionKey.OP_ACCEPT);
            log.info("OP_CONNECT:{}", SelectionKey.OP_CONNECT);
            log.info("OP_READ:{}", SelectionKey.OP_READ);
            log.info("OP_WRITE:{}", SelectionKey.OP_WRITE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }





}
