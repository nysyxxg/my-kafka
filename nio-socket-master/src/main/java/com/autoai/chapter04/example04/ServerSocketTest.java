package com.autoai.chapter04.example04;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.*;
import java.net.*;
import java.time.Instant;
import java.util.Objects;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/19 19:42
 * @Description:
 */
@Slf4j
public class ServerSocketTest {

    /**
     * server端设置超时
     */
    @Test
    public void test1() {
        long begin = 0;
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            log.info("soTimeout:{}", serverSocket.getSoTimeout());
            serverSocket.setSoTimeout(3000);
            log.info("soTimeout:{}", serverSocket.getSoTimeout());
            begin = Instant.now().toEpochMilli();
            log.info("begin:{}", begin);
            serverSocket.accept();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            long end = Instant.now().toEpochMilli();
            log.info("interval:{}", end - begin);
        }
    }

    /**
     * 3s 内启动
     * @throws IOException
     */
    @Test
    public void test2() throws IOException {
        new Socket("localhost", 9000);
    }

    @Test
    public void test3() {
        try {
            // 默认backlog值是50
            ServerSocket serverSocket = new ServerSocket(8088);
            Thread.sleep(5000);
            for (int i = 0; i < 100; i++) {
                System.out.println("accept1 begin " + (i + 1));
                Socket socket = serverSocket.accept();
                System.out.println("accept1    end" +(i + 1));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        try {
            for (int i = 0; i < 100; i++) {
                Socket socket1 = new Socket("localhost", 8088);
                System.out.println("client发起连接次数：" + (i + 1));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test5() {
        try {
            // 获取主机上任意ip 0.0.0.0
            InetAddress inetAddress = new InetSocketAddress(0).getAddress();
            // 获取回环网络
//            InetAddress inetAddress = InetAddress.getLoopbackAddress();
            // 获取本机
//            InetAddress inetAddress = InetAddress.getLocalHost();
            // 根据hostname获取
//            InetAddress inetAddress = InetAddress.getByName("mapbar");

            String hostName = inetAddress.getHostName();
            log.info("hostname:{}", hostName);
            ServerSocket serverSocket = new ServerSocket(9000, 5, inetAddress);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String result;
            while (Objects.nonNull(result = bufferedReader.readLine())) {
                log.info("thread:{},line:{}", Thread.currentThread().getName(), result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test6() {
        try {
            Socket socket = new Socket("localhost", 9000);
//            Socket socket = new Socket("127.0.0.1", 9000);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write("123".getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test7() {
        try {
            ServerSocket serverSocket = new ServerSocket();
            SocketAddress endpoint = new InetSocketAddress(0);
            serverSocket.bind(endpoint);
            // 获取随机绑定空闲的端口号
            String hostName = ((InetSocketAddress) serverSocket.getLocalSocketAddress()).getHostName();
            int port = serverSocket.getLocalPort();
            log.info("hostname:{},port:{}", hostName, port);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String result;
            while (Objects.nonNull(result = bufferedReader.readLine())) {
                log.info("thread:{},line:{}", Thread.currentThread().getName(), result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test8() {
        try {
            ServerSocket serverSocket = new ServerSocket();
            SocketAddress endpoint = new InetSocketAddress("127.0.0.1", 9000);
            String hostName = ((InetSocketAddress) endpoint).getHostName();
            log.info("hostname:{}", hostName);
            serverSocket.bind(endpoint);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String result;
            while (Objects.nonNull(result = bufferedReader.readLine())) {
                log.info("thread:{},line:{}", Thread.currentThread().getName(), result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test9() {
        try {
            ServerSocket serverSocket = new ServerSocket();
            SocketAddress endpoint = new InetSocketAddress(InetAddress.getByName("mapbar"), 9000);
            String hostName = ((InetSocketAddress) endpoint).getHostName();
            log.info("hostname:{}", hostName);
            serverSocket.bind(endpoint);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String result;
            while (Objects.nonNull(result = bufferedReader.readLine())) {
                log.info("thread:{},line:{}", Thread.currentThread().getName(), result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test10() {
        try {
            Socket socket = new Socket("mapbar", 9000);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write("123".getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test11() throws IOException {
        ServerSocket serverSocket = new ServerSocket(9000);
        boolean b = serverSocket.isClosed();
        log.info("isClosed:{}", b);
        serverSocket.close();
        b = serverSocket.isClosed();
        log.info("isClosed:{}", b);
    }

    @Test
    public void test12() throws IOException {
        ServerSocket serverSocket1 = new ServerSocket();
        // false
        log.info("isBound:{}", serverSocket1.isBound());
        // 能够解析的endpoint
        SocketAddress endpoint = new InetSocketAddress("localhost", 9000);
        serverSocket1.bind(endpoint);
        // true
        log.info("isBound:{}", serverSocket1.isBound());

        ServerSocket serverSocket2 = new ServerSocket(8080);
        // true
        log.info("isBound:{}", serverSocket2.isBound());

        ServerSocket serverSocket3 = new ServerSocket();
        // 不能够解析的endpoint
        endpoint = new InetSocketAddress("https://www.baidu123.com", 9000);
        try {
            serverSocket3.bind(endpoint);
        } catch (BindException e) {
            // false
            log.info("isBound:{}", serverSocket3.isBound());
        }
    }

    @Test
    public void test13() throws IOException {
        ServerSocket serverSocket = new ServerSocket(9000);
        InetAddress inetAddress = serverSocket.getInetAddress();
        String hostAddress = inetAddress.getHostAddress();
        String loopbackAddress = InetAddress.getLoopbackAddress().getHostAddress();
        log.info("hostAddress:{}, loopbackAddress:{}", hostAddress, loopbackAddress);
    }

    /**
     * 服务端tcp端口重用
     * @throws IOException
     */
    @Test
    public void test14() throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        // 默认开启tcp端口重用
        log.info("reuse:{}", serverSocket.getReuseAddress());
        // 关闭端口重用
        serverSocket.setReuseAddress(false);
        log.info("reuse:{}", serverSocket.getReuseAddress());
        SocketAddress endpoint = new InetSocketAddress(9000);
        serverSocket.bind(endpoint);
        serverSocket.accept();

        // 关闭server
        serverSocket.close();

        // 重新创建一个server端
        ServerSocket serverSocket2 = new ServerSocket();
        serverSocket2.setReuseAddress(false);
        serverSocket2.bind(new InetSocketAddress(9000));
        // 关闭server
        serverSocket2.close();
    }

    @Test
    public void test15() throws IOException, InterruptedException {
       new Socket("localhost", 9000);
    }

    @Test
    public void test16() {
       try {
           ServerSocket serverSocket = new ServerSocket();
           SocketAddress endpoint = new InetSocketAddress(9000);
           serverSocket.bind(endpoint);
           serverSocket.accept();
           Thread.sleep(60000);
           // 关闭server
           serverSocket.close();
       } catch (Exception e) {
           e.printStackTrace();
       }
    }

    /**
     * 客户端tcp连接，默认不使用端口复用
     */
    @Test
    public void test17() {
        try {
            SocketAddress server = new InetSocketAddress(9000);

            Socket socket1 = new Socket();
            SocketAddress endpoint1 = new InetSocketAddress(9001);
            socket1.bind(endpoint1);
            log.info("reuse:{}", socket1.getReuseAddress());
            socket1.connect(server);

            socket1.close();

            Socket socket2 = new Socket();
            SocketAddress endpoint2 = new InetSocketAddress(9001);
            socket2.bind(endpoint2);
            socket2.connect(server);
            socket2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
