package com.autoai.chapter04.example06;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/23 19:09
 * @Description: 单播
 */
@Slf4j
public class DatagramSocketTest {

    @Test
    public void server() {
        try (DatagramSocket datagramSocket = new DatagramSocket(null)){

            datagramSocket.bind(new InetSocketAddress(9000));

            byte[] bytes = new byte[10];
            DatagramPacket datagramPacket = new DatagramPacket(bytes, 0, bytes.length);
            datagramSocket.receive(datagramPacket);

            String result = new String(datagramPacket.getData());
            log.info(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client() {
        try (DatagramSocket datagramSocket = new DatagramSocket(null)){

            datagramSocket.bind(new InetSocketAddress(8080));
            datagramSocket.connect(new InetSocketAddress("localhost",9000));
            /// 正常发送
            String message = "1234567890";
            // 测试发送超大数据量的包导致数据截断的情况
//            for (int i = 0; i < 65508; i++) {
//                message = message + 1;
//            }
            DatagramPacket datagramPacket = new DatagramPacket(message.getBytes(), 0, message.length());
            datagramSocket.send(datagramPacket);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
