package com.autoai.chapter05.example04;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

/**
 * @Author: zhukaishengy
 * @Date: 2020/7/13 10:47
 * @Description: 组播
 */
@Slf4j
public class DatagramChannel2Test {

    @Test
    public void server() {

        try (
            DatagramChannel datagramChannel = DatagramChannel.open(StandardProtocolFamily.INET);
            Selector selector = Selector.open()
        ){
            datagramChannel.bind(new InetSocketAddress(9000));
            // IP协议规定多点广播地址的范围是224.0.0.0 ~ 239.255.255.255
            datagramChannel.join(InetAddress.getByName("224.0.0.1"), NetworkInterface.getByInetAddress(InetAddress.getLocalHost()));
            datagramChannel.configureBlocking(false);
            datagramChannel.register(selector, SelectionKey.OP_READ);

            while (true) {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    if (selectionKey.isReadable()) {
                        DatagramChannel datagramChannel1 = (DatagramChannel)selectionKey.channel();
                        // 初始化ReceiveBufferSize
                        ByteBuffer byteBuffer = ByteBuffer.allocate(datagramChannel1.socket().getReceiveBufferSize());
                        // 用来存储最终message
                        datagramChannel1.receive(byteBuffer);
                        String message = new String(byteBuffer.array(), 0, byteBuffer.position(), Charset.forName("utf-8"));
                        log.info("message:{}, read end...", message);
                    }
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client() {
        try (
            DatagramChannel datagramChannel = DatagramChannel.open(StandardProtocolFamily.INET);
            Selector selector = Selector.open()
        ){
            datagramChannel.configureBlocking(false);
            datagramChannel.register(selector, SelectionKey.OP_WRITE);
            // 创建连接
            datagramChannel.connect(new InetSocketAddress("224.0.0.1", 9000));

            while (true) {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    if (selectionKey.isWritable()) {
                        DatagramChannel datagramChannel1 = (DatagramChannel)selectionKey.channel();
                        datagramChannel1.write(ByteBuffer.wrap("123".getBytes()));
                        log.info("send:123" );
                        // 断开连接
                        datagramChannel1.close();
                    }
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
