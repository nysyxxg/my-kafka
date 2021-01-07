package demo;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class JavaConsumer extends Thread {
    private LinkedBlockingQueue linkedBlockingQueue;
    private ArrayBlockingQueue arrayBlockingQueue;
    
    public JavaConsumer(ArrayBlockingQueue arrayBlockingQueue) {
        this.arrayBlockingQueue = arrayBlockingQueue;
    }
    
    public JavaConsumer(LinkedBlockingQueue linkedBlockingQueue) {
        this.linkedBlockingQueue = linkedBlockingQueue;
    }
    
    @Override
    public void run() {
        
        while (true) {
            try {
//                String data = (String) arrayBlockingQueue.take();// 阻塞
                String  data = (String) linkedBlockingQueue.take();
                System.out.println("消费者---> 开始消费的数据为： " + data);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
        }
    }
}
