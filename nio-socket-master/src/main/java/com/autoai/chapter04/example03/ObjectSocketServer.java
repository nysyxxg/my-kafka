package com.autoai.chapter04.example03;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
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
public class ObjectSocketServer {

    private int port;
    private int corePoolSize;

    private ExecutorService executorService;

    private ObjectSocketServer(int port, int corePoolSize) {
        this.port = port;
        this.corePoolSize = corePoolSize;
    }

    private void buildExecutorService() {

        executorService = new ThreadPoolExecutor(corePoolSize, corePoolSize + 5, 5, TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(Integer.MAX_VALUE), new ThreadFactory() {

            private static final String NAME_PREFIX = "worker-";
            AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(NAME_PREFIX + count.getAndIncrement());
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
                // 读取客户端传来的userinfo
                InputStream inputStream = socket.getInputStream();
                ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                Userinfo userinfo = (Userinfo) objectInputStream.readObject();
                log.info("receive:{}", userinfo);
                OutputStream outputStream = socket.getOutputStream();
                // 返回通用的userinfo
                Userinfo genericUserInfo = Userinfo.builder().id("0").name("zks").age(26).build();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                objectOutputStream.writeObject(genericUserInfo);
            } catch (IOException | ClassNotFoundException e) {
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
        ObjectSocketServer socketServer = new ObjectSocketServer(9000, 2);
        socketServer.startService();
    }
}
