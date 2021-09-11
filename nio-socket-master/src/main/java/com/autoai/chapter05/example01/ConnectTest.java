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
public class ConnectTest {

    @Test
    public void test1() {
        try {
            log.info("server start...");
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(true);

            serverSocketChannel.accept();
            log.info("server end...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        boolean connect = false;
        long start = 0L;
        long end = 0L;
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.bind(new InetSocketAddress("localhost", 8000));
            log.info("isBlocking:{}", socketChannel.isBlocking());

            start = Instant.now().toEpochMilli();
            connect = socketChannel.connect(new InetSocketAddress("localhost", 9000));
            end = Instant.now().toEpochMilli();
        } catch (Exception e) {
            end = Instant.now().toEpochMilli();
            e.printStackTrace();
        } finally {
            log.info("connect:{}, time:{}", connect, end -start);
        }
    }

    @Test
    public void test3() {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.bind(new InetSocketAddress("localhost", 8000));
            log.info("isBlocking:{}", socketChannel.isBlocking());
            socketChannel.configureBlocking(false);

            long start = Instant.now().toEpochMilli();
            boolean connect = socketChannel.connect(new InetSocketAddress("localhost", 9000));
            long end = Instant.now().toEpochMilli();

            log.info("connect:{}, time:{}", connect, end -start);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
