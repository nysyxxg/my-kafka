package com.autoai.chapter04.example01;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Socket;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/7 15:35
 * @Description:
 */
@Slf4j
public class Client {

    public static void main(String[] args) {
        log.info("client 连接准备...");
        try (Socket socket = new Socket("localhost", 8080)){
            log.info("client 连接完成...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
