package com.autoai.chapter05.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/25 13:37
 * @Description:
 */
@Slf4j
public class SocketTest {

    @Test
    public void test1() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));

            SocketChannel socketChannel = serverSocketChannel.accept();
            log.info("SO_REUSEADDR:{}", socketChannel.getOption(StandardSocketOptions.SO_RCVBUF));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置不上option
     */
    @Test
    public void test2() {
        try {
            SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress("localhost", 9000));
            Boolean option = socketChannel.getOption(StandardSocketOptions.SO_REUSEADDR);
            log.info("option:{}", option);
            socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1234);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 可以设置成功option
     */
    @Test
    public void test3() {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1234);
            socketChannel.bind(new InetSocketAddress("localhost", 8888));
            Boolean option = socketChannel.getOption(StandardSocketOptions.SO_REUSEADDR);
            log.info("option:{}", option);

            socketChannel.connect(new InetSocketAddress("localhost", 9000));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
