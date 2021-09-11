package com.autoai.chapter04.example02;

import org.junit.Test;

import java.io.OutputStream;
import java.net.Socket;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/19 14:55
 * @Description:
 */
public class Clients {

    public static void main(String[] args) {
        try (
            Socket socket1 = new Socket("localhost", 9000);
            Socket socket2 = new Socket("localhost", 9000);
            Socket socket3 = new Socket("localhost", 9000);
            Socket socket4 = new Socket("localhost", 9000);
            Socket socket5 = new Socket("localhost", 9000)
        ){
            OutputStream outputStream1 = socket1.getOutputStream();
            outputStream1.write("1".getBytes());
            OutputStream outputStream2 = socket2.getOutputStream();
            outputStream2.write("2".getBytes());
            OutputStream outputStream3 = socket3.getOutputStream();
            outputStream3.write("3".getBytes());
            OutputStream outputStream4 = socket4.getOutputStream();
            outputStream4.write("4".getBytes());
            OutputStream outputStream5 = socket5.getOutputStream();
            outputStream5.write("5".getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
