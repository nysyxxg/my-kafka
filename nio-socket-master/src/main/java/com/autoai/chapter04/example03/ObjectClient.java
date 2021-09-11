package com.autoai.chapter04.example03;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/19 14:55
 * @Description:
 */
@Slf4j
public class ObjectClient {

    public static void main(String[] args) {
        try (
            Socket socket = new Socket("localhost", 9000)
        ){
            OutputStream outputStream = socket.getOutputStream();
            Userinfo genericUserInfo = Userinfo.builder().id("1").name("haha").age(20).build();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(genericUserInfo);

            InputStream inputStream = socket.getInputStream();
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            Userinfo userinfo = (Userinfo) objectInputStream.readObject();
            log.info("receive:{}", userinfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
