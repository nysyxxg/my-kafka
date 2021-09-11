package com.autoai.chapter05.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Instant;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/24 16:58
 * @Description:
 */
@Slf4j
public class ConnectionPendingTest {

    @Test
    public void test1() {
        try {
            log.info("server start...");
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9002));
            serverSocketChannel.configureBlocking(true);

            serverSocketChannel.accept();
            log.info("server end...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * false
     */
    @Test
    public void test2() {
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.bind(new InetSocketAddress("localhost", 8000));
            log.info("isBlocking:{}", socketChannel.isBlocking());

            // 不存在的IP
            socketChannel.connect(new InetSocketAddress("www.baidu.com", 9002));
        } catch (Exception e) {
            log.info("isConnectionPending:{}", socketChannel.isConnectionPending());
            e.printStackTrace();
        }
    }

    @Test
    public void test3() {
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.bind(new InetSocketAddress("localhost", 8000));
            log.info("isBlocking:{}", socketChannel.isBlocking());

            // 不存在的IP
            socketChannel.connect(new InetSocketAddress("localhost", 9002));
        } catch (Exception e) {
            log.info("isConnectionPending:{}", socketChannel.isConnectionPending());
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.bind(new InetSocketAddress("localhost", 8000));
            log.info("isBlocking:{}", socketChannel.isBlocking());

            socketChannel.connect(new InetSocketAddress("www.baidu.com", 9002));
        } catch (Exception e) {
            log.info("isConnectionPending:{}", socketChannel.isConnectionPending());
            e.printStackTrace();
        }
    }

    @Test
    public void test5() {
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.bind(new InetSocketAddress("localhost", 8000));
            log.info("isBlocking:{}", socketChannel.isBlocking());

            socketChannel.connect(new InetSocketAddress("localhost", 9002));
        } catch (Exception e) {
            log.info("isConnectionPending:{}", socketChannel.isConnectionPending());
            e.printStackTrace();
        }
    }
}
