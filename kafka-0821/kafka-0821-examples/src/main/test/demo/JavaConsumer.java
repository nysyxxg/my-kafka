package demo;

import java.util.concurrent.ArrayBlockingQueue;

public class JavaConsumer extends Thread {
    
    private ArrayBlockingQueue arrayBlockingQueue;
    
    public JavaConsumer(ArrayBlockingQueue arrayBlockingQueue) {
        this.arrayBlockingQueue = arrayBlockingQueue;
    }
    
    @Override
    public void run() {
        
        while (true) {
            try {
                String data = (String) arrayBlockingQueue.take();// 阻塞
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
