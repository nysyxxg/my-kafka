package com.lun.kafka.util;

import java.util.UUID;

public class RandomUtil {
    
    public static int getRandom() {
        int max = 10000, min = 1000;
        int ran2 = (int) (Math.random() * (max - min) + min);
        System.out.println(ran2);
        return ran2;
    }
    
    public static String getUUID() {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        System.out.println(uuid);
        return uuid;
    }
    
    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            //注意replaceAll前面的是正则表达式
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            System.out.println(uuid);
//            System.out.println(uuid.length());
        }
    }
}
