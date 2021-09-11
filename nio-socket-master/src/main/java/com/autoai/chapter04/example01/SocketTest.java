package com.autoai.chapter04.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/7 15:42
 * @Description:
 */
@Slf4j
public class SocketTest {

    /**
     * 创建一个web服务器
     */
    @Test
    public void test1() {
        try (
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            OutputStream outputStream = socket.getOutputStream()
        ) {
            String getString;
            while (! "".equals(getString = bufferedReader.readLine())) {
                System.out.println(getString);
            }
            outputStream.write("HTTP/1.1 200 OK \r\n\r\n".getBytes());
            outputStream.write("<html><body><a href='http://www.google.com'>Google</a></body></html>".getBytes());
            outputStream.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        log.info("wait for client");
        try (
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream()
        ){
            byte[] bytes = new byte[64];
            log.info("wait for client data");
            inputStream.read(bytes);
            log.info(new String(bytes));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test3() {
        try (Socket socket = new Socket("localhost", 9000)){
            log.info("socket 连接成功。。。");
            Thread.sleep(10000);
            log.info("client 关闭连接。。。");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void server() {
        try (
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream()
        ){
            byte[] bytes = new byte[9];
            while (inputStream.read(bytes) != -1) {
                String result = new String(bytes, Charset.forName("utf-8"));
                log.info(result);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void client() {
        try (
            Socket socket = new Socket("localhost", 9000);
            OutputStream outputStream = socket.getOutputStream()
        ){
            String message = "朱开生";
            outputStream.write(message.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(5000);
            outputStream.write(message.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test6() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write("哈哈哈".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test7() {

        try {
            Socket socket = new Socket("localhost", 9000);
            InputStream inputStream = socket.getInputStream();
            byte[] bytes = new byte[3];
            while (inputStream.read(bytes) != -1) {
                String result = new String(bytes, Charset.forName("utf-8"));
                log.info(result);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test8() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            byte[] bytes = new byte[3];
            while (inputStream.read(bytes) != -1) {
                String result = new String(bytes, Charset.forName("utf-8"));
                log.info(result);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test9() {
        try {
            Socket socket = new Socket("localhost", 9000);
            OutputStream outputStream = socket.getOutputStream();
            String line1 = "朱开生";
            String line2 = "呼呼";
            String line3 = "嘻嘻";
            outputStream.write(line1.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(3000);
            outputStream.write(line2.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(3000);
            outputStream.write(line3.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test10() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);

            Socket socket = serverSocket.accept();
            // 输入开始
            InputStream inputStream = socket.getInputStream();
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            int byteLength = objectInputStream.readInt();
            byte[] byteArray = new byte[byteLength];
            objectInputStream.readFully(byteArray);
            String newString = new String(byteArray);
            System.out.println(newString);
            // 输入结束
            // 输出开始
            OutputStream outputStream = socket.getOutputStream();
            String strA = "客户端你好A\n";
            String strB = "客户端你好B\n";
            String strC = "客户端你好C\n";
            int allStrByteLength = (strA + strB + strC).getBytes().length;
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeInt(allStrByteLength);
            objectOutputStream.flush();
            objectOutputStream.write(strA.getBytes());
            objectOutputStream.write(strB.getBytes());
            objectOutputStream.write(strC.getBytes());
            objectOutputStream.flush();
            // 输出结束
            // 输入开始
            byteLength = objectInputStream.readInt();
            byteArray = new byte[byteLength];
            objectInputStream.readFully(byteArray);
            newString = new String(byteArray);
            System.out.println(newString);
            // 输入结束
            // 输出开始
            strA = "客户端你好D\n";
            strB = "客户端你好E\n";
            strC = "客户端你好F\n";
            allStrByteLength = (strA + strB + strC).getBytes().length;
            objectOutputStream.writeInt(allStrByteLength);
            objectOutputStream.flush();
            objectOutputStream.write(strA.getBytes());
            objectOutputStream.write(strB.getBytes());
            objectOutputStream.write(strC.getBytes());
            objectOutputStream.flush();
            // 输出结束
            inputStream.close();

            socket.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test11() {
        try {
            Socket socket = new Socket("localhost", 9000);
            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();
            // 输出开始
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            String strA = "服务端你好A\n";
            String strB = "服务端你好B\n";
            String strC = "服务端你好C\n";
            int allStrByteLength = (strA + strB + strC).getBytes().length;
            objectOutputStream.writeInt(allStrByteLength);
            objectOutputStream.flush();
            objectOutputStream.write(strA.getBytes());
            objectOutputStream.write(strB.getBytes());
            objectOutputStream.write(strC.getBytes());
            objectOutputStream.flush();
            // 输出结束
            // 输入开始
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            int byteLength = objectInputStream.readInt();
            byte[] byteArray = new byte[byteLength];
            objectInputStream.readFully(byteArray);
            String newString = new String(byteArray);
            System.out.println(newString);
            // 输入结束
            // 输出开始
            strA = "服务端你好D\n";
            strB = "服务端你好E\n";
            strC = "服务端你好F\n";
            allStrByteLength = (strA + strB + strC).getBytes().length;
            objectOutputStream.writeInt(allStrByteLength);
            objectOutputStream.flush();
            objectOutputStream.write(strA.getBytes());
            objectOutputStream.write(strB.getBytes());
            objectOutputStream.write(strC.getBytes());
            objectOutputStream.flush();
            // 输出结束
            // 输入开始
            byteLength = objectInputStream.readInt();

            byteArray = new byte[byteLength];
            objectInputStream.readFully(byteArray);
            newString = new String(byteArray);
            System.out.println(newString);
            // 输入结束
            objectOutputStream.close();
            outputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test12() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            Socket socket = serverSocket.accept();

            RandomAccessFile randomAccessFile = new RandomAccessFile(
                    new File("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter04/file/b.jpg"),
                    "rw");
            FileChannel channel = randomAccessFile.getChannel();

            InputStream inputStream = socket.getInputStream();

            byte[] bytes = new byte[24];
            while (inputStream.read(bytes) != -1) {
                channel.write(ByteBuffer.wrap(bytes));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test13() {
        try {
            Socket socket = new Socket("localhost", 9000);
            RandomAccessFile randomAccessFile = new RandomAccessFile(
                    new File("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter04/file/a.jpg"),
                    "rw");
            FileChannel channel = randomAccessFile.getChannel();
            byte[] bytes = new byte[(int)channel.size()];
            channel.read(ByteBuffer.wrap(bytes));

            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(bytes);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test14() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
//            serverSocket.accept();
            Thread.sleep(50000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test15() {
        try {
            Socket socket = new Socket("127.0.0.1", 9000);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write("111".getBytes());
            outputStream.write("222".getBytes());
            outputStream.write("333".getBytes());
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class WorkerThread extends Thread {

        private Socket socket;

        public WorkerThread(Socket socket) {
            super();
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
                   log.info("result:{}", result);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void test16() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);

            while (true) {
                Socket socket = serverSocket.accept();
                WorkerThread workerThread = new WorkerThread(socket);
                workerThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test17() {
        try (
            Socket socket1 = new Socket("127.0.0.1", 9000);
            Socket socket2 = new Socket("localhost", 9000);
            Socket socket3 = new Socket("localhost", 9000);
            Socket socket4 = new Socket("localhost", 9000);
            Socket socket5 = new Socket("localhost", 9000);

        ){
            OutputStream outputStream1 = socket1.getOutputStream();
            outputStream1.write("a".getBytes());
            OutputStream outputStream2 = socket2.getOutputStream();
            outputStream2.write("b".getBytes());
            OutputStream outputStream3 = socket3.getOutputStream();
            outputStream3.write("c".getBytes());
            OutputStream outputStream4 = socket4.getOutputStream();
            outputStream4.write("d".getBytes());
            OutputStream outputStream5 = socket5.getOutputStream();
            outputStream5.write("e".getBytes());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
