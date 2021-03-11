package demo;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class JavaProducer extends Thread {
    private LinkedBlockingQueue linkedBlockingQueue;
    private ArrayBlockingQueue arrayBlockingQueue;
    
    public JavaProducer(ArrayBlockingQueue arrayBlockingQueue) {
        this.arrayBlockingQueue = arrayBlockingQueue;
    }
    
    public JavaProducer(LinkedBlockingQueue linkedBlockingQueue) {
        this.linkedBlockingQueue = linkedBlockingQueue;
    }
    
    @Override
    public void run() {
        int index = 0;
        while (true) {
            index++;
            String data = Thread.currentThread().getName() + "--->" + index;
            try {
//                arrayBlockingQueue.put(data);// 阻塞
                linkedBlockingQueue.put(data);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            System.out.println("生产者---开始生产数据为： " + data + " 元素中的个数：" + arrayBlockingQueue.size());
            System.out.println("生产者---开始生产数据为： " + data + " 元素中的个数：" + linkedBlockingQueue.size());
    
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
