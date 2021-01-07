package demo;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * 	    抛出异常	 特殊值	   阻塞	    超时
 * 插入	add(e)	    offer(e)   put(e)	offer(e, time, unit)
 * 移除	remove()	poll()	  take()	poll(time, unit)
 * 检查	element()	peek()	  不可用	不可用
 *
 */
public class MyTest {
    
    public static void main(String[] args) {
    
        ArrayBlockingQueue arrayBlockingQueue = new ArrayBlockingQueue(5);
        
        JavaProducer producer = new JavaProducer(arrayBlockingQueue);
        new Thread(producer).start();
        
        JavaConsumer consumer = new JavaConsumer(arrayBlockingQueue);
        new Thread(consumer).start();
    }
}
