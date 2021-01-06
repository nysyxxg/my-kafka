package demo02;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
   

/**
 * 多线程并行消费topic
 * @author admin
 *
 */
public class ConsumerService implements Runnable {  
    private KafkaStream<byte[], byte[]> m_stream;  
    private int m_threadNumber;
	  
   
    public ConsumerService(KafkaStream<byte[], byte[]> a_stream, int a_threadNumber) {  
        m_threadNumber = a_threadNumber;  
        m_stream = a_stream;  
    }  
   
    public void run() {  
        ConsumerIterator<byte[], byte[]> it = m_stream.iterator();  
        byte[]  by = null;
        while (it.hasNext())  
        	by = it.next().message();
        	String value = new String(by);
//            System.out.println("Thread " + m_threadNumber + ": " + value);  
//        	System.out.println(Thread.currentThread().getName() + " ....Shutting down Thread: " + m_threadNumber);  
    }  
}  