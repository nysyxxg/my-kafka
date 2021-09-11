package com.autoai.chapter02.example00;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: zhukaishengy
 * @Date: 2020/5/11 13:56
 * @Description: 读取出access_fm.log中的url，添加请求头，发送请求
 */
@Slf4j
public class MyWork {

    static String urlPrefix = "http://localhost:2001";

    private static CloseableHttpClient createHttpclient() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        // 最大连接数
        cm.setMaxTotal(10);
        cm.setDefaultMaxPerRoute(cm.getMaxTotal());
        CookieStore cookieStore = new BasicCookieStore();
        RequestConfig requestConfig = RequestConfig.custom()
                // 请求超时时间
                .setConnectTimeout(3 * 1000)
                // 等待数据超时时间
                .setSocketTimeout(60 * 1000)
                // 连接超时时间
                .setConnectionRequestTimeout(500)
                .build();
        return HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .setDefaultCookieStore(cookieStore)
                .build();
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        BufferedReader bufferedReader = Files.newBufferedReader(FileSystems.getDefault()
                .getPath("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file", "ng_1730.log"));

        String line ;
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 5, 1, TimeUnit.MINUTES,
                new LinkedBlockingDeque<>(Integer.MAX_VALUE), r -> {
                    Thread thread = new Thread(r);
                    thread.setName("th-");
                    return thread;
                }, (r, executor) -> log.error("discard..."));

        // CloseableHttpClient类线程安全
        CloseableHttpClient client = MyWork.createHttpclient();

        List<Callable<String>> callableList = new ArrayList<>();

        AtomicInteger counter = new AtomicInteger(0);

        while ((line = bufferedReader.readLine()) != null) {
            String[] items = line.split(" ");
            String urlSuffix = items[8];
            String url = urlPrefix + urlSuffix;

            Callable<String> callable = () -> {
                HttpGet get = new HttpGet(url);
                CloseableHttpResponse response = client.execute(get);
                // 请求体内容
                String content = EntityUtils.toString(response.getEntity(), "UTF-8");
                log.info("[count:{}] {} {}", counter.addAndGet(1), url, content);
                response.close();
                return null;
            };
            callableList.add(callable);
        }

        threadPoolExecutor.invokeAll(callableList);
        log.info("end...count:{}", counter.get());
        threadPoolExecutor.shutdown();
    }


}
