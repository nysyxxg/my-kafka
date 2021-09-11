package com.autoai.chapter05.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/25 13:24
 * @Description:
 */
@Slf4j
public class FileChannelTest {

    @Test
    public void test1() throws IOException {
        ServerSocketChannel channel1 = ServerSocketChannel.open();
        SocketChannel socketChannel = null;
        channel1.configureBlocking(false);
        channel1.bind(new InetSocketAddress("localhost", 9000));
        Selector selector = Selector.open();
        channel1.register(selector, SelectionKey.OP_ACCEPT);
        while (true) {
            selector.select();
            Set<SelectionKey> set = selector.selectedKeys();
            Iterator<SelectionKey> iterator = set.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    socketChannel = channel1.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_WRITE);
                }
                if (key.isWritable()) {
                    RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/Desktop/vip第三节\\ Explain关键字详解、查询优化原理分析与实战（二）.mp4", "rw");
                    // 此exe文件大小大约1GB
                    System.out.println("file.length()=" + file.length());
                    FileChannel fileChannel = file.getChannel();
                    fileChannel.transferTo(0, file.length(), socketChannel);
                    fileChannel.close();
                    file.close();
                    socketChannel.close();
                }
            }
        }
    }

    @Test
    public void test2() throws IOException {
        SocketChannel channel1 = SocketChannel.open();
        channel1.configureBlocking(false);
        channel1.connect(new InetSocketAddress("localhost", 9000));
        Selector selector = Selector.open();
        channel1.register(selector, SelectionKey.OP_CONNECT);
        while (true) {
            System.out.println("begin selector");
            if (channel1.isOpen()) {
                selector.select();
                System.out.println("  end selector");
                Set<SelectionKey> set = selector.selectedKeys();

                Iterator<SelectionKey> iterator = set.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isConnectable()) {
                        while (!channel1.finishConnect()) {
                        }
                        channel1.register(selector, SelectionKey.OP_READ);
                    }
                    if (key.isReadable()) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(50000);
                        int readLength = channel1.read(byteBuffer);
                        byteBuffer.flip();
                        long count = 0;
                        while (readLength != -1) {
                            count = count + readLength;
                            readLength = channel1.read(byteBuffer);
                            System.out.println("count=" + count + " readLength=" +
                                    readLength);
                            byteBuffer.clear();
                        }
                        System.out.println("读取结束");
                        channel1.close();
                    }
                }
            } else {
                break;
            }
        }
    }

}
