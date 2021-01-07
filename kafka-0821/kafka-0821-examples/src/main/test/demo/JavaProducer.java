package demo;

import java.util.concurrent.ArrayBlockingQueue;

public class JavaProducer extends Thread {
    
    private ArrayBlockingQueue arrayBlockingQueue;
    
    public JavaProducer(ArrayBlockingQueue arrayBlockingQueue) {
        this.arrayBlockingQueue = arrayBlockingQueue;
    }
    
    @Override
    public void run() {
        int index = 0;
        while (true) {
            index++;
            String data = Thread.currentThread().getName() + "--->" + index;
            try {
                arrayBlockingQueue.put(data);// 阻塞
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("生产者---开始生产数据为： " + data + " 元素中的个数：" + arrayBlockingQueue.size());
            
        }
    }
}
