package com.autoai.chapter05.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/25 13:55
 * @Description:
 */
@Slf4j
public class BigFileTest {

    @Test
    public void test1() throws IOException {
        ServerSocketChannel channel1 = ServerSocketChannel.open();
        channel1.configureBlocking(false);
        channel1.bind(new InetSocketAddress("localhost", 8088));
        Selector selector = Selector.open();
        channel1.register(selector, SelectionKey.OP_ACCEPT);
        boolean isRun = true;
        while (isRun) {
            selector.select();
            Set<SelectionKey> set = selector.selectedKeys();
            Iterator<SelectionKey> iterator = set.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    SocketChannel socketChannel = channel1.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_WRITE);
                }
                if (key.isWritable()) {
                    SocketChannel socketChannel = (SocketChannel) key.channel();
                    FileInputStream file = new FileInputStream("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter05/imgs/image-20200624112349386.png");
                    FileChannel fileChannel = file.getChannel();
                    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(524288000);
                    // 500MB空间
                    while (fileChannel.position() < fileChannel.size()) {
                        fileChannel.read(byteBuffer);
                        byteBuffer.flip();

                        while (byteBuffer.hasRemaining()) {
                            socketChannel.write(byteBuffer);
                        }
                        byteBuffer.clear();
                        System.out.println(fileChannel.position() + " " + fileChannel.size());
                    }
                    System.out.println("结束写操作");
                    socketChannel.close();
                }
            }
        }
        channel1.close();

    }

    @Test
    public void test2() throws IOException {
        SocketChannel channel1 = SocketChannel.open();
        channel1.configureBlocking(false);
        channel1.connect(new InetSocketAddress("localhost", 8088));
        Selector selector = Selector.open();
        channel1.register(selector, SelectionKey.OP_CONNECT);
        boolean isRun = true;
        while (isRun) {
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
                        while (! channel1.finishConnect()) {
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
                            System.out.println("count=" + count + " readLength=" +
                                    readLength);
                            readLength = channel1.read(byteBuffer);
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
