package com.autoai.chapter04.example04;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/23 10:22
 * @Description: 无法验证，系统会调优
 */
@Slf4j
public class ReceiveBufferSizeTest {

    @Test
    public void server() {
        try (
            ServerSocket serverSocket = new ServerSocket()
        ) {
            log.info("server initial size:{}", serverSocket.getReceiveBufferSize());
            // 必须大于1024
            serverSocket.setReceiveBufferSize(1024);
            SocketAddress endpoint = new InetSocketAddress("localhost", 9000);
            serverSocket.bind(endpoint);
            Socket socket = serverSocket.accept();
            log.info("server changed size:{}", serverSocket.getReceiveBufferSize());
            InputStream inputStream = socket.getInputStream();
            InputStreamReader reader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                log.info(line);
            }
        } catch (Exception e) {
            log.error("error occur",e);
        }
    }

    @Test
    public void client() {
        try (
            Socket socket = new Socket()
        ){
            log.info("client initial size:{}", socket.getReceiveBufferSize());
            socket.setReceiveBufferSize(1024);
            log.info("client changed size:{}", socket.getReceiveBufferSize());
            SocketAddress clientEndPoint = new InetSocketAddress("localhost", 8080);
            SocketAddress serverEndPoint = new InetSocketAddress("localhost", 9000);
            socket.bind(clientEndPoint);
            socket.connect(serverEndPoint);

            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(("实际上，多维度数据模型和强大的查询语言这两个特性，正是时序数据库所要求的，所以 Prometheus 不仅仅是一个监控系统，" +
                    "同时也是一个时序数据库。那为什么 Prometheus 不直接使用现有的时序数据库作为后端存储呢？这是因为 SoundCloud 不仅希望他们的监控系" +
                    "统有着时序数据库的特点，而且还需要部署和维护非常方便。纵观比较流行的时序数据库（参见下面的附录），他们要么组件太多，要么外部依赖繁重，" +
                    "比如：Druid 有 Historical、MiddleManager、Broker、Coordinator、Overlord、Router 一堆的组件，而且还依赖于 ZooKeeper、Deep storage" +
                    "（HDFS 或 S3 等），Metadata store（PostgreSQL 或 MySQL），部署和维护起来成本非常高。而 Prometheus 采用去中心化架构，可以独立部署，" +
                    "不依赖于外部的分布式存储，你可以在几分钟的时间里就可以搭建出一套监控系统。此外，Prometheus 数据采集方式也非常灵活。要采集目标的监控数据，" +
                    "首先需要在目标处安装数据采集组件，这被称之为 Exporter，它会在目标处收集监控数据，并暴露出一个 HTTP 接口供 Prometheus 查询，" +
                    "Prometheus 通过 Pull 的方式来采集数据，这和传统的 Push 模式不同。不过 Prometheus 也提供了一种方式来支持 Push 模式，" +
                    "你可以将你的数据推送到 Push Gateway，Prometheus 通过 Pull 的方式从 Push Gateway 获取数据。目前的 Exporter 已经可以采集绝大多数的第三方数据，" +
                    "比如 Docker、HAProxy、StatsD、JMX 等等，官网有一份 Exporter 的列表。").getBytes());
        } catch (Exception e) {
            log.error("error occur", e);
        }
    }
}
