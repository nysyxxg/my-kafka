package com.autoai.chapter05.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/25 14:13
 * @Description:
 */
@Slf4j
public class NBlockTest {

    @Test
    public void test1() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            Selector selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                ServerSocketChannel serverSocketChannel1 = (ServerSocketChannel)selectionKey.channel();
                SocketChannel socketChannel = serverSocketChannel1.accept();
                socketChannel.configureBlocking(false);
                ByteBuffer byteBuffer = ByteBuffer.allocate(10);
                socketChannel.read(byteBuffer);
                socketChannel.write(byteBuffer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress("localhost", 9000));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
