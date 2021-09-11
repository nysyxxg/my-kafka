package com.autoai.chapter05.example03;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @Author: zhukaishengy
 * @Date: 2020/7/9 13:51
 * @Description:
 */
@Slf4j
public class SelectionKey1Test {

    @Test
    public void server() {
        try (
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                // 1s定时器
                selector.select(1000);
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey selectionKey = keyIterator.next();

                    // 处理accept的注册
                    if (selectionKey.isAcceptable()) {
                        ServerSocketChannel serverSocketChannel1 = (ServerSocketChannel)selectionKey.channel();
                        SocketChannel socketChannel = serverSocketChannel1.accept();
                        log.info("accept end...");
                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_READ);
                    }

                    // 处理read的注册
                    if (selectionKey.isReadable()) {
                        SocketChannel socketChannel = (SocketChannel)selectionKey.channel();
                        log.info("begin read...");
                        // 初始化ReceiveBufferSize
                        ByteBuffer byteBuffer = ByteBuffer.allocate(socketChannel.socket().getReceiveBufferSize());
                        // 用来存储最终message
                        String message;
                        int flag;
                        while ((flag = socketChannel.read(byteBuffer)) != -1) {
                            if (flag > 0) {
                                message = new String(byteBuffer.array(), 0, byteBuffer.position(), Charset.forName("utf-8"));
                                byteBuffer.clear();
                                log.info("message:{}, read end...", message);
                            }
                        }
                        // 取消监听
                        selectionKey.cancel();
                    }

                    // 从set中移除
                    keyIterator.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client() {

        try (
            SocketChannel socketChannel = SocketChannel.open();
            Selector selector = Selector.open()
        ){
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
            socketChannel.connect(new InetSocketAddress("localhost", 9000));

            while (true) {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey selectionKey = keyIterator.next();

                    if (selectionKey.isConnectable()) {
                        while (!socketChannel.finishConnect()) {
                            log.info("waiting for client finish connect...");
                        }
                        SocketChannel socketChannel1 = (SocketChannel)selectionKey.channel();
                        log.info("connect end...");
                        String message = "我是朱开生!";
                        // 添加附件
                        SelectionKey selectionKey1 = socketChannel1.register(selector, SelectionKey.OP_WRITE, message);
                        // 更改附件
                        selectionKey1.attach(message + "haha");
                        log.info("eq:{}", selectionKey.equals(selectionKey1));
                    }

                    if (selectionKey.isWritable()) {
                        // ready 的事件
                        log.info("OP_ACCEPT:{},OP_CONNECT:{},OP_READ:{},OP_WRITE:{}",
                                SelectionKey.OP_ACCEPT, SelectionKey.OP_CONNECT, SelectionKey.OP_READ, SelectionKey.OP_WRITE);
                        int readyOps = selectionKey.readyOps();
                        log.info("readyOps:{}", readyOps);
                        SocketChannel socketChannel1 = (SocketChannel)selectionKey.channel();
                        // 写数据
                        String attachment = (String)selectionKey.attachment();
                        ByteBuffer byteBuffer = ByteBuffer.wrap(attachment.getBytes(Charset.forName("utf-8")));
                        socketChannel1.write(byteBuffer);
                        log.info("message:{},write end...", attachment);

                        Thread.sleep(1000);

                        String message2 = "哈哈哈嘻嘻嘻";
                        ByteBuffer byteBuffer2 = ByteBuffer.wrap(message2.getBytes(Charset.forName("utf-8")));
                        socketChannel1.write(byteBuffer2);
                        log.info("message:{},write end...", message2);

                        selectionKey.cancel();
                    }
                    // 从set中移除
                    keyIterator.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test1() {

        try (
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            SocketChannel socketChannel = SocketChannel.open();
            Selector selector = Selector.open()
        ){
            serverSocketChannel.configureBlocking(false);
            socketChannel.configureBlocking(false);

            SelectionKey selectionKey1 = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            SelectionKey selectionKey2 = socketChannel.register(selector,
                    SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            int interestOps1 = selectionKey1.interestOps();
            int interestOps2 = selectionKey2.interestOps();

            log.info("OP_ACCEPT:{},OP_CONNECT:{},OP_READ:{},OP_WRITE:{}",
                    SelectionKey.OP_ACCEPT, SelectionKey.OP_CONNECT, SelectionKey.OP_READ, SelectionKey.OP_WRITE);
            log.info("interestOps1:{},interestOps2:{}", interestOps1, interestOps2);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {

        try (
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open()
        ){
            serverSocketChannel.configureBlocking(false);

            SelectionKey selectionKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            log.info("valid:{}", selectionKey.isValid());
            selectionKey.cancel();
            log.info("valid:{}", selectionKey.isValid());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
