package com.autoai.chapter05.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/24 13:50
 * @Description:
 */
@Slf4j
public class ServerSocketChannelTest {

    @Test
    public void test1() {
        try (
            // 使用静态方法创建 ServerSocketChannel
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            ServerSocket serverSocket = serverSocketChannel.socket()
         ){
            serverSocket.bind(new InetSocketAddress("localhost", 9000));
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            byte[] bytes = new byte[10];
            while (inputStream.read(bytes) != -1) {
                log.info("message:{}", new String(bytes));
                bytes = new byte[10];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client1() {
        try {
            Socket socket = new Socket("localhost", 9000);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write("112".getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        try (
            // 使用静态方法创建 ServerSocketChannel
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            ServerSocket serverSocket = serverSocketChannel.socket()
         ){
            // 如果使用serverSocketChannel进行了bind()绑定，那么就不再使用serverSocket进行bind()绑定
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000), 3);
//            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            while (true) {
                Socket socket = serverSocket.accept();
                InputStream inputStream = socket.getInputStream();
                byte[] bytes = new byte[10];
                while (inputStream.read(bytes) != -1) {
                    log.info("message:{}", new String(bytes));
                    bytes = new byte[10];
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client2() {
        try {
            for (int i = 0; i < 5; i++) {
                Socket socket = new Socket("localhost", 9000);
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write("111".getBytes());
                outputStream.flush();
                log.info("客户端连接数：{}", i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testBlockingServer() {

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(true);
            SocketChannel socketChannel = serverSocketChannel.accept();
            log.info("socketChannel:{}", socketChannel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testNonBlockingServer() {

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(false);
            SocketChannel socketChannel = serverSocketChannel.accept();
            // 在非阻塞模式下，accept()方法在没有客户端连接时，返回null
            log.info("socketChannel:{}", socketChannel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test3() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            serverSocketChannel.configureBlocking(true);
            SocketChannel socketChannel = serverSocketChannel.accept();
            ByteBuffer byteBuffer = ByteBuffer.allocate(2);
            while (socketChannel.read(byteBuffer) != -1) {
                byte[] result = new byte[byteBuffer.position()];
                byteBuffer.flip();
                byteBuffer.get(result);
                log.info("message:{}", new String(result));
                byteBuffer.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
