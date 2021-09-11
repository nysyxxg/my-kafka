package com.autoai.chapter05.example04;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

/**
 * @Author: zhukaishengy
 * @Date: 2020/7/13 14:59
 * @Description:
 */
@Slf4j
public class SelectorProviderTest {

    @Test
    public void test() throws IOException {

        SelectorProvider provider = SelectorProvider.provider();
        log.info("SelectorProvider:{}", provider);
        AbstractSelector selector = provider.openSelector();
        log.info("AbstractSelector:{}", selector);
        SocketChannel socketChannel = provider.openSocketChannel();
        log.info("SocketChannel:{}", socketChannel);
        ServerSocketChannel serverSocketChannel = provider.openServerSocketChannel();
        log.info("ServerSocketChannel:{}", serverSocketChannel);
        DatagramChannel datagramChannel = provider.openDatagramChannel(StandardProtocolFamily.INET);
        log.info("DatagramChannel:{}", datagramChannel);
        Pipe pipe = provider.openPipe();
        log.info("Pipe:{}", pipe);
    }
}
