package com.autoai.chapter02.example01;

import lombok.extern.slf4j.Slf4j;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/30 13:19
 * @Description:
 */
@Slf4j
public class MainTest {

    public static void main(String[] args) {

        // 如果try后的小括号中有多条语句，则最后一条后是没有分号的
        // 并且小括号中的变量都要实现AutoCloseable接口
        try (MyResource myResource = new MyResource()){
            myResource.work();
            throw new RuntimeException("xixi");
        } catch (Exception e) {
            log.error("error occur..");
        }
    }
}
