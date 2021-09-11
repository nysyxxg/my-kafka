package com.autoai.chapter04.example05;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/23 15:59
 * @Description:
 */
@Slf4j
public class NagleTest {

    /**
     * server
     */
    @Test
    public void test1() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            log.info("getTcpNoDelay:{}", socket.getTcpNoDelay());
            // 不使用nagle
            socket.setTcpNoDelay(true);
            log.info("getTcpNoDelay:{}", socket.getTcpNoDelay());
            InputStream inputStream = socket.getInputStream();
            byte[] bytes = new byte[socket.getReceiveBufferSize()];
            while (inputStream.read(bytes) != -1) {
                log.info(new String(bytes));
                bytes = new byte[socket.getReceiveBufferSize()];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * client
     */
    @Test
    public void test2() {
        try {
            Socket socket = new Socket("mapbar", 9000);
            log.info("getTcpNoDelay:{}", socket.getTcpNoDelay());
            OutputStream outputStream = socket.getOutputStream();
            for (int i = 0; i < 5000; i++) {
                outputStream.write("1".getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
