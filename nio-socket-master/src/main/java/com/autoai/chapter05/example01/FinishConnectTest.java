package com.autoai.chapter05.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/24 17:58
 * @Description:
 */
@Slf4j
public class FinishConnectTest {

    @Test
    public void test1() {
        try {
            log.info("server start...");
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9001));
            serverSocketChannel.configureBlocking(true);

            serverSocketChannel.accept();
            log.info("server end...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        SocketChannel socketChannel;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.bind(new InetSocketAddress("localhost", 8000));
            socketChannel.configureBlocking(false);

            boolean connect = socketChannel.connect(new InetSocketAddress("localhost", 9001));
            if (!connect) {
                while (!socketChannel.finishConnect()) {
                    log.info("connecting...");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
