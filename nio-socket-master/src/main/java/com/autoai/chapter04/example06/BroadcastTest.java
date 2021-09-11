package com.autoai.chapter04.example06;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.*;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/23 20:15
 * @Description: 广播
 */
@Slf4j
public class BroadcastTest {

    @Test
    public void server1() {
        try (DatagramSocket datagramSocket = new DatagramSocket(null)) {

            // 定义接收端口号
            int udpPort = 9000;
            datagramSocket.bind(new InetSocketAddress(udpPort));
            // 接收
            byte[] bytes = new byte[10];
            DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length);
            datagramSocket.receive(datagramPacket);
            String message = new String(datagramPacket.getData());
            log.info(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client() {
        try (DatagramSocket datagramSocket = new DatagramSocket()){
            // 设置开启广播
            boolean broadcast = datagramSocket.getBroadcast();
            log.info("broadcast:{}", broadcast);
            datagramSocket.setBroadcast(true);
            // 定义广播地址和发送端口号
            String broadcastAddress = "255.255.255.255";
            int udpPort = 9000;
            datagramSocket.connect(new InetSocketAddress(broadcastAddress, udpPort));
            // 发送
            datagramSocket.send(new DatagramPacket("123".getBytes(), 3));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
