package com.lun.kafka.demo;

import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;


/**
 * 多线程并行消费topic
 */
public class ConsumerService implements Runnable {
    private KafkaStream<byte[], byte[]> m_stream;
    private int m_threadNumber;
    
    
    public ConsumerService(KafkaStream<byte[], byte[]> a_stream, int a_threadNumber) {
        this.m_threadNumber = a_threadNumber;
        this.m_stream = a_stream;
    }
    
    public void run() {
        ConsumerIterator<byte[], byte[]> it = m_stream.iterator();
        byte[] by = null;
        while (it.hasNext()) {
            by = it.next().message();
            String value = new String(by);
            System.out.println(Thread.currentThread().getName() + " ....Shutting down Thread: " + " Thread " + m_threadNumber + ": " + value);
        }
    }
}  