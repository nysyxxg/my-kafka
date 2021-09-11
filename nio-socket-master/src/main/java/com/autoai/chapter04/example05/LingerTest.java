package com.autoai.chapter04.example05;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/23 16:40
 * @Description:
 */
@Slf4j
public class LingerTest {

    /**
     * server
     */
    @Test
    public void test1() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            log.info("getSoLinger:{}", socket.getSoLinger());
            InputStream inputStream = socket.getInputStream();
            byte[] bytes = new byte[socket.getReceiveBufferSize()];
            while (inputStream.read(bytes) != -1) {
                log.info(new String(bytes));
                socket.setSoLinger(true, 1);
//                socket.setSoLinger(false, 0);
//                socket.setSoLinger(true, 0);
                bytes = new byte[socket.getReceiveBufferSize()];
            }
            socket.close();
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
            OutputStream outputStream = socket.getOutputStream();
            for (int i = 0; i < 100000; i++) {
                outputStream.write("1".getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
