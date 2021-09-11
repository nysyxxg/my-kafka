package com.autoai.chapter04.example01;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/7 15:32
 * @Description:
 */
@Slf4j
public class Server {

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080);
        log.info("server start...");
        serverSocket.accept();
        log.info("server accept...");
        serverSocket.close();
    }
}
