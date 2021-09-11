package com.autoai.chapter04.example05;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/23 17:23
 * @Description:
 */
@Slf4j
public class OOBInlineTest {

    @Test
    public void server() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            System.out.println("server A getOOBInline=" + socket.getOOBInline());
            socket.setOOBInline(true);
            System.out.println("server B getOOBInline=" + socket.getOOBInline());
            InputStream inputStream = socket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            char[] charArray = new char[1024];
            int readLength = inputStreamReader.read(charArray);
            while (readLength != -1) {
                String newString = new String(charArray, 0, readLength);
                System.out.println(newString);
                readLength = inputStreamReader.read(charArray);
            }
            socket.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client() {
        try {
            Socket socket = new Socket("localhost", 9000);
            OutputStream outputStream = socket.getOutputStream();
            // 必须使用OutputStreamWriter类才出现预期的效果
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            socket.sendUrgentData(97);
            outputStreamWriter.write("+zzzzzzzzzzzzzzzzzzzzzzzzzz+");
            socket.sendUrgentData(98);
            socket.sendUrgentData(99);
            // 必须使用flush()，不然不会出现预期的效果
            outputStreamWriter.flush();
            socket.sendUrgentData(100);
            outputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void server2() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            Thread.sleep(5000);
            socket.close();
            serverSocket.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 用于server2的心跳检测
     */
    @Test
    public void client2() {
        try (Socket socket = new Socket("localhost", 9000)){
            int count = 0;
            while (true) {
                socket.sendUrgentData(97);
                log.info("count:{}", count++);
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
