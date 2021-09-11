package com.autoai.chapter04.example05;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/23 16:28
 * @Description:
 */
@Slf4j
public class BufferSizeTest {

    @Test
    public void test1() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            char[] charArray = new char[1024];
            int readLength = inputStreamReader.read(charArray);
            long beginTime = System.currentTimeMillis();
            while (readLength != -1) {
                String newString = new String(charArray, 0, readLength);
                System.out.println(newString);
                readLength = inputStreamReader.read(charArray);
            }
            long endTime = System.currentTimeMillis();
            // 240 -> 166
            System.out.println(endTime - beginTime);
            socket.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        try {
            Socket socket = new Socket();
            System.out.println("A client socket.getSendBufferSize()=" + socket.
                    getSendBufferSize());
            socket.setSendBufferSize(5 * 1024);
//            socket.setSendBufferSize(1);
            System.out.println("B client socket.getSendBufferSize()=" + socket.
                    getSendBufferSize());
            socket.connect(new InetSocketAddress("localhost", 9000));
            OutputStream outputStream = socket.getOutputStream();

            for (int i = 0; i < 5000; i++) {
                outputStream.write("123456789123456789123456789123456789123456789".getBytes());
                        System.out.println(i + 1);
            }
            outputStream.write("end! ".getBytes());
            outputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
