package demo;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * ArrayBlockingQueue  类中的方法：
 * 抛出异常	 特殊值	   阻塞	    超时
 * 插入	add(e)	    offer(e)   put(e)	offer(e, time, unit)
 * 移除	remove()	poll()	  take()	poll(time, unit)
 * 检查	element()	peek()	  不可用	不可用
 *
 *
 * LinkedBlockingQueue是一个阻塞队列，内部由两个ReentrantLock来实现出入队列的线程安全，
 * 由各自的Condition对象的await和signal来实现等待和唤醒功能。它和ArrayBlockingQueue的不同点在于：
 *
 * 队列大小有所不同，ArrayBlockingQueue是有界的初始化必须指定大小，
 * 而LinkedBlockingQueue可以是有界的也可以是无界的(Integer.MAX_VALUE)，对于后者而言，当添加速度大于移除速度时，
 * 在无界的情况下，可能会造成内存溢出等问题。
 * 数据存储容器不同，ArrayBlockingQueue采用的是数组作为数据存储容器，而LinkedBlockingQueue采用的则是以Node节点作为连接对象的链表。
 * 由于ArrayBlockingQueue采用的是数组的存储容器，因此在插入或删除元素时不会产生或销毁任何额外的对象实例，
 * 而LinkedBlockingQueue则会生成一个额外的Node对象。这可能在长时间内需要高效并发地处理大批量数据的时，对于GC可能存在较大影响。
 * 两者的实现队列添加或移除的锁不一样，ArrayBlockingQueue实现的队列中的锁是没有分离的，
 * 即添加操作和移除操作采用的同一个ReenterLock锁，而LinkedBlockingQueue实现的队列中的锁是分离的，
 * 其添加采用的是putLock，移除采用的则是takeLock，这样能大大提高队列的吞吐量，
 * 也意味着在高并发的情况下生产者和消费者可以并行地操作队列中的数据，以此来提高整个队列的并发性能。
 *
 */
public class MyTest {
    
    public static void main(String[] args) {
        // ArrayBlockingQueue 是有界的
        ArrayBlockingQueue arrayBlockingQueue = new ArrayBlockingQueue(5);
//
//        JavaProducer producer = new JavaProducer(arrayBlockingQueue);
//        new Thread(producer).start();
//
//        JavaConsumer consumer = new JavaConsumer(arrayBlockingQueue);
//        new Thread(consumer).start();
        
        
        // LinkedBlockingQueue可以是有界的也可以是无界的(Integer.MAX_VALUE)
        LinkedBlockingQueue linkedBlockingQueue = new LinkedBlockingQueue();
        
        JavaProducer producer = new JavaProducer(linkedBlockingQueue);
        new Thread(producer).start();
        
        JavaConsumer consumer = new JavaConsumer(linkedBlockingQueue);
        new Thread(consumer).start();
    }
}
