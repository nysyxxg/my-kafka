package com.autoai.chapter04.example05;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/23 14:03
 * @Description:
 */
@Slf4j
public class SocketTest {

    @Test
    public void server() {
        try {
            ServerSocket serverSocket = new ServerSocket();
            SocketAddress serverEndPoint = new InetSocketAddress("localhost", 9000);
            serverSocket.bind(serverEndPoint);
            Socket socket = serverSocket.accept();
            InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
            String remoteSocketAddressHostString = remoteSocketAddress.getHostString();
            log.info("server: serverPort:{}, clientPort:{}, serverIP:{}, clientIP:{}, remoteSocketAddressHostString:{}", socket.getLocalPort(),
                    socket.getPort(), socket.getLocalAddress().getHostAddress(), socket.getInetAddress().getHostAddress(), remoteSocketAddressHostString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * mac上connect超时没有生效
     */
    @Test
    public void test1() {
        try {
            Socket socket = new Socket();
            SocketAddress serverEndPoint = new InetSocketAddress("localhost", 9000);
            SocketAddress clientEndPoint = new InetSocketAddress("localhost", 8080);
            socket.bind(clientEndPoint);
            socket.connect(serverEndPoint, 5000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        try {
            Socket socket = new Socket();
            SocketAddress serverEndPoint = new InetSocketAddress("localhost", 9000);
            SocketAddress clientEndPoint = new InetSocketAddress("localhost", 8080);
            log.info("isBound:{}", socket.isBound());
            socket.bind(clientEndPoint);
            log.info("isBound:{}", socket.isBound());
            log.info("isConnected:{}", socket.isConnected());
            socket.connect(serverEndPoint, 5000);
            log.info("isConnected:{}", socket.isConnected());
            log.info("isClosed:{}", socket.isClosed());
            socket.close();
            log.info("isClosed:{}", socket.isClosed());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 服务端shutdowninput，进入半读状态
     */
    @Test
    public void test3() {

        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            log.info("available:{}", inputStream.available());
            byte[] bytes = new byte[3];
            inputStream.read(bytes);
            log.info(new String(bytes));
            // 屏蔽InputStream，到达流结尾
            log.info("isInputShutdown:{}", socket.isInputShutdown());
            socket.shutdownInput();
            log.info("isInputShutdown:{}", socket.isInputShutdown());
            // 静默丢弃其他数据
            byte[] clone = new byte[3];
            log.info("available:{}", inputStream.available());
            inputStream.read(clone);
            log.info(new String(clone));
            // 再次获取InputStream报错
            socket.getInputStream();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        try {
            Socket socket = new Socket("mapbar", 9000);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write("123456".getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test5() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            byte[] bytes = new byte[3];
            inputStream.read(bytes);
            log.info(new String(bytes));
            byte[] clone = new byte[3];
            inputStream.read(clone);
            log.info(new String(clone));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 客户端shutdownoutput，进入半写状态
     */
    @Test
    public void test6() {
        try {
            Socket socket = new Socket("mapbar", 9000);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write("123".getBytes());
            // 终止序列
            log.info("isOutputShutdown:{}", socket.isOutputShutdown());
            socket.shutdownOutput();
            log.info("isOutputShutdown:{}", socket.isOutputShutdown());
            // 出现异常
            socket.getOutputStream();
            outputStream.write("456".getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
