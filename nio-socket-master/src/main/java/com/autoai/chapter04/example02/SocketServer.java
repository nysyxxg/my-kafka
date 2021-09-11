package com.autoai.chapter04.example02;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/19 14:34
 * @Description:
 */
@Slf4j
public class SocketServer {

    private int port;
    private int corePoolSize;

    private ExecutorService executorService;

    private SocketServer(int port, int corePoolSize) {
        this.port = port;
        this.corePoolSize = corePoolSize;
    }

    private void buildExecutorService() {

        executorService = new ThreadPoolExecutor(corePoolSize, corePoolSize + 5, 5, TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(Integer.MAX_VALUE), new ThreadFactory() {

            private static final String NAMEPREFIX = "worker-";
            AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(NAMEPREFIX + count.getAndIncrement());
                return thread;
            }
        }, new ThreadPoolExecutor.DiscardPolicy());
    }

    class WorkerRunnable implements Runnable {

        private Socket socket;

        WorkerRunnable(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                InputStream inputStream = socket.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String result;
                while (Objects.nonNull(result = bufferedReader.readLine())) {
                    log.info("thread:{},line:{}", Thread.currentThread().getName(), result);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startService() {

        this.buildExecutorService();

        try (
            ServerSocket serverSocket = new ServerSocket(port)

        ){
            while (true) {
                Socket socket = serverSocket.accept();
                executorService.submit(new WorkerRunnable(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        SocketServer socketServer = new SocketServer(9000, 2);
        socketServer.startService();
    }
}
