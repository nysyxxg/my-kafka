package com.lun.kafka.kafkatest;

import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;

/**
 * 具体处理message的线程
 * @author Administrator
 *
 */
class HanldMessageThread implements Runnable {
 
    private KafkaStream<byte[], byte[]> kafkaStream = null;
    private int num = 0;
     
    public HanldMessageThread(KafkaStream<byte[], byte[]> kafkaStream, int num) {
        super();
        this.kafkaStream = kafkaStream;
        this.num = num;
    }
 
    public void run() {
        ConsumerIterator<byte[], byte[]> iterator = kafkaStream.iterator();
        while(iterator.hasNext()) {
            String message = new String(iterator.next().message());
            System.out.println(Thread.currentThread().getName() + "   Thread no: " + num + ", message: " + message);
        }
    }
     
}