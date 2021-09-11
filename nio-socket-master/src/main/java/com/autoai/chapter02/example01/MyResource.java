package com.autoai.chapter02.example01;

import lombok.extern.slf4j.Slf4j;

/**
 * @Author: zhukaishengy
 * @Date: 2020/4/30 13:19
 * @Description:
 */
@Slf4j
public class MyResource implements AutoCloseable {

    public void work() {
        log.info("work...");
    }

    @Override
    public void close() throws Exception {
        log.info("resource auto closed...");
    }
}
