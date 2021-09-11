package com.autoai.chapter04.example04;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/22 13:55
 * @Description:
 */
@Slf4j
public class InetSocketAddressTest {

    @Test
    public void test1() {
        InetSocketAddress inetSocketAddress = new InetSocketAddress("10.30.28.207", 9000);
        String hostName = inetSocketAddress.getHostName();
        String hostStr = inetSocketAddress.getHostString();
        log.info("hostname:{}, hoststr:{}", hostName, hostStr);
    }

    @Test
    public void test2() {
        InetSocketAddress inetSocketAddress = new InetSocketAddress("mapbar", 9000);
        byte[] bytes = inetSocketAddress.getAddress().getAddress();
        log.info("address:{}", Arrays.toString(bytes));
    }

    @Test
    public void test3() {
        InetSocketAddress inetSocketAddress1 = new InetSocketAddress("https://www.baidu.com", 80);
        InetSocketAddress inetSocketAddress2 = new InetSocketAddress("https://www.baidu123.com", 80);
        InetSocketAddress inetSocketAddress3 = InetSocketAddress.createUnresolved("https://www.baidu.com", 80);
        InetSocketAddress inetSocketAddress4 = InetSocketAddress.createUnresolved("https://www.baidu123.com", 80);
        log.info("{}, {}, {}, {}", inetSocketAddress1.isUnresolved(), inetSocketAddress2.isUnresolved(),
                inetSocketAddress3.isUnresolved(), inetSocketAddress4.isUnresolved());
    }
}
