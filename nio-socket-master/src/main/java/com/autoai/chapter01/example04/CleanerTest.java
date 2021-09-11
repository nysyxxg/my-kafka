package com.autoai.chapter01.example04;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class CleanerTest {


    public static void main(String[] args) throws NoSuchMethodException, InterruptedException, InvocationTargetException, IllegalAccessException {

        log.info("A");
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.MAX_VALUE);
        log.info("B");
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            byteBuffer.put((byte) i);
        }
        log.info("C");
        Thread.sleep(10000);
        Method getCleanerMethod = byteBuffer.getClass().getMethod("cleaner");
        getCleanerMethod.setAccessible(true);
        Object cleaner = getCleanerMethod.invoke(byteBuffer);

        Method cleanMethod = cleaner.getClass().getMethod("clean");
        cleanMethod.setAccessible(true);
        cleanMethod.invoke(cleaner);
        log.info("D");
        Thread.sleep(10000);
        log.info("E");
    }
}
