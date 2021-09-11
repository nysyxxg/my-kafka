package com.autoai.chapter03.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/2 20:27
 * @Description:
 */
@Slf4j
public class BasicInfoTest {

    @Test
    public void test1() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                // 网卡设备名称
                String name = networkInterface.getName();
                // 显示名称
                String displayName = networkInterface.getDisplayName();
                // 索引
                int index = networkInterface.getIndex();
                // 是否启动
                boolean isUp = networkInterface.isUp();
                // 是否为回环网络
                boolean isLoopback = networkInterface.isLoopback();
                log.info("name:{},displayName:{},index:{},isUp:{},isLoopback:{}", name, displayName, index, isUp, isLoopback);
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取MTU（最大传输单元）大小
     * 2020-06-02 21:06:09 [INFO] name:en4,MTU:1500
     * 2020-06-02 21:06:09 [INFO] name:en0,MTU:1500
     * @throws SocketException
     */
    @Test
    public void test2() throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            String name = networkInterface.getName();
            int mtu = networkInterface.getMTU();
            log.info("name:{},MTU:{}", name, mtu);
        }
    }

    /**
     * 子接口的处理
     */
    @Test
    public void test3() throws SocketException {

        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            String name = networkInterface.getName();
            boolean isVirtual = networkInterface.isVirtual();
            log.info("name:{},isVirtual:{}", name, isVirtual);
            if (isVirtual) {
                NetworkInterface parent = networkInterface.getParent();
                log.info("parent:{}", parent.getName());
            } else {
                Enumeration<NetworkInterface> subInterfaces = networkInterface.getSubInterfaces();
                while (subInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface1 = subInterfaces.nextElement();
                    log.info("child:{}", networkInterface1.getName());
                }
            }
        }
    }

    @Test
    public void test4() throws SocketException {

        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            String name = networkInterface.getName();
            byte[] hardwareAddress = networkInterface.getHardwareAddress();
            log.info("name:{},hardwareAddress:{}", name, hardwareAddress);
        }
    }

    @Test
    public void test5() throws SocketException {

        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            String name = networkInterface.getName();
            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress inetAddress = inetAddresses.nextElement();
                String canonicalHostName = inetAddress.getCanonicalHostName();
                String hostName = inetAddress.getHostName();
                String hostAddress = inetAddress.getHostAddress();
                byte[] address = inetAddress.getAddress();
                log.info("name:{} canonicalHostName:{} hostName:{} hostAddress:{} address:{}",
                        name, canonicalHostName, hostName, hostAddress, address);
            }
        }
    }

    @Test
    public void test6() throws UnknownHostException {

        InetAddress localHost = InetAddress.getLocalHost();
        log.info("localhost address:{}", localHost.getHostAddress());

        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        log.info("loopback address:{}", loopbackAddress.getHostAddress());
    }

    @Test
    public void test7() throws UnknownHostException {
        InetAddress mapbar = InetAddress.getByName("mapbar");
        InetAddress google = InetAddress.getByName("www.google.com");
        InetAddress server73 = InetAddress.getByName("10.30.28.73");
        log.info("mapbar:{} google:{} server73:{}", mapbar.getHostAddress(), google.getHostAddress(), server73.getHostAddress());
    }

    @Test
    public void test8() throws UnknownHostException {
        InetAddress[] mapbars = InetAddress.getAllByName("mapbar");
        InetAddress[] googles = InetAddress.getAllByName("www.google.com");
        InetAddress[] server73s = InetAddress.getAllByName("10.30.28.73");

        Arrays.stream(mapbars).forEach(mapbar -> {
            log.info("mapbar :{}", mapbar.getHostAddress());
        });
        Arrays.stream(googles).forEach(google -> {
            log.info("google :{} address:{}", google.getHostAddress(), google.getAddress());
        });
        Arrays.stream(server73s).forEach(server73 -> {
            log.info("server73 :{}", server73.getHostAddress());
        });
    }

    @Test
    public void test9() throws UnknownHostException {
        byte[] bytes = new byte[]{-83, -4, 102, -15};
        InetAddress address = InetAddress.getByAddress(bytes);
        log.info("hostname:{}, getCanonicalHostName:{}", address.getHostName(), address.getCanonicalHostName());
    }

    @Test
    public void test10() throws UnknownHostException {
        byte[] bytes = new byte[]{-83, -4, 102, -15};
        InetAddress address = InetAddress.getByAddress("google", bytes);
        log.info("hostname:{}, CanonicalHostName:{}", address.getHostName(), address.getCanonicalHostName());
    }

    @Test
    public void test11() throws UnknownHostException {
        // 使用主机名创建 InetAddress
        InetAddress mapbar = InetAddress.getByName("mapbar");
        log.info("hostname:{}, CanonicalHostName:{}", mapbar.getHostName(), mapbar.getCanonicalHostName());
        // 使用域名创建 InetAddress
        InetAddress google = InetAddress.getByName("www.google.com");
        log.info("hostname:{}, CanonicalHostName:{}", google.getHostName(), google.getCanonicalHostName());
        // 使用IP创建 InetAddress
        InetAddress server73 = InetAddress.getByName("10.30.28.73");
        log.info("hostname:{}, CanonicalHostName:{}", server73.getHostName(), server73.getCanonicalHostName());
    }

    @Test
    public void test12() throws SocketException {

        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            String name = networkInterface.getName();
            List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
            for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                String hostAddress = interfaceAddress.getAddress() != null ? interfaceAddress.getAddress().getHostAddress() : null;
                String broadcast = interfaceAddress.getBroadcast() != null ?  interfaceAddress.getBroadcast().getHostAddress() : null;
                short networkPrefixLength = interfaceAddress.getNetworkPrefixLength();
                log.info("name:{}, hostAddress:{}, broadcast:{}, networkPrefixLength:{}",
                        name, hostAddress, broadcast, networkPrefixLength);
            }
        }
    }

    @Test
    public void test13() throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            String name = networkInterface.getName();
            boolean pointToPoint = networkInterface.isPointToPoint();
            log.info("name:{},pointToPoint:{}", name, pointToPoint);
        }
    }

    @Test
    public void test14() throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            String name = networkInterface.getName();
            boolean supportsMulticast = networkInterface.supportsMulticast();
            log.info("name:{},supportsMulticast:{}", name, supportsMulticast);
        }
    }

    /**
     * 2020-06-07 14:47:18 [INFO] name:en0,displayName:en0,index:7,isUp:true,isLoopback:false
     */
    @Test
    public void test15() throws SocketException {
        NetworkInterface networkInterface = NetworkInterface.getByIndex(7);
        String name = networkInterface.getName();
        log.info("name:{}", name);
    }

    @Test
    public void test16() throws SocketException {
        NetworkInterface networkInterface = NetworkInterface.getByName("en0");
        log.info("name:{},IsUp:{}", networkInterface.getName(), networkInterface.isUp());
    }

    /**
     * 如果指定的IP地址绑定到多个网络接口，则不确定返回哪个网络接口，这个功能是可以实现的。
     * 在Linux中，bonding的含义是将多个物理的网卡抽象成1块网卡，能够提升网络吞吐量，实现网络冗余、负载等功能，有很大的好处。
     * @throws UnknownHostException
     * @throws SocketException
     */
    @Test
    public void test17() throws UnknownHostException, SocketException {
        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
        log.info("name:{}", networkInterface.getName());
    }



}
