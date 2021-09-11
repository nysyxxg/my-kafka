package com.autoai.chapter04.example06;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/24 10:49
 * @Description: 组播
 *
 * mac下报错问题：添加jvm参数 -Djava.net.preferIPv4Stack=true
 */
@Slf4j
public class MulticastSocketTest {

    @Test
    public void server() {
        try {
            // IP协议规定多点广播地址的范围是224.0.0.0 ~ 239.255.255.255
            String multicastAddress = "239.255.255.255";

            MulticastSocket multicastSocket = new MulticastSocket(null);
            multicastSocket.bind(new InetSocketAddress(9000));
            multicastSocket.joinGroup(InetAddress.getByName(multicastAddress));
            // 接收
            byte[] bytes = new byte[10];
            DatagramPacket datagramPacket = new DatagramPacket(bytes, 0, bytes.length);
            multicastSocket.receive(datagramPacket);
            log.info("message:{}", datagramPacket.getData());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Test
    public void client() {
        try {
            // IP协议规定多点广播地址的范围是224.0.0.0 ~ 239.255.255.255
            String multicastAddress = "239.255.255.255";

            MulticastSocket multicastSocket = new MulticastSocket(null);
            multicastSocket.connect(new InetSocketAddress(multicastAddress, 9000));
            // 发送
            DatagramPacket datagramPacket = new DatagramPacket("123".getBytes(), 0, 3);
            multicastSocket.send(datagramPacket);
            log.info("message:{}", datagramPacket.getData());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
