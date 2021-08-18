package kafka.producer.async;

import kafka.producer.QueueItem;
import kafka.producer.SyncProducer;
import kafka.serializer.Encoder;
import kafka.utils.SystemTime;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class ProducerSendThread<T> extends Thread {
    
    private Logger logger = Logger.getLogger(ProducerSendThread.class);
    private CountDownLatch shutdownLatch = new CountDownLatch(1);
    
    private String threadName;
    private LinkedBlockingQueue<QueueItem<T>> queue;
    private Encoder serializer;
    private SyncProducer underlyingProducer;
    private EventHandler handler;
    private CallbackHandler cbkHandler;
    private int queueTime;
    private int batchSize;
    private Object shutdownCommand;
    
    public <T> ProducerSendThread(String threadName,
                                  LinkedBlockingQueue queue,
                                  Encoder<T> serializer,
                                  SyncProducer underlyingProducer,
                                  EventHandler<T> handler,
                                  CallbackHandler<T> cbkHandler,
                                  int queueTime,
                                  int batchSize,
                                  Object shutdownCommand) {
        super(threadName);
        this.threadName = threadName;
        this.queue = queue;
        this.serializer = serializer;
        this.underlyingProducer = underlyingProducer;
        this.handler = handler;
        this.cbkHandler = cbkHandler;
        this.queueTime = queueTime;
        this.batchSize = batchSize;
        this.shutdownCommand = shutdownCommand;
    }
    
    // 启动异步发送线程之后，会运行run方法
    public void run() {
        try {
            List<QueueItem<T>> remainingEvents = processEvents(); // 处理需要发送的event对象，返回剩余的event对象
            if (logger.isDebugEnabled()) {
                logger.debug("Remaining events = " + remainingEvents.size());
            }
            
            // handle remaining events
            if (remainingEvents.size() > 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Dispatching last batch of %d events to the event handler".format(String.valueOf(remainingEvents.size())));
                }
                tryToHandle(remainingEvents);
            }
        } catch (Exception e) {
            logger.error("Error in sending events: ", e);
        } finally {
            shutdownLatch.countDown();
        }
    }
    
    private void tryToHandle(List<QueueItem<T>> events) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Handling " + events.size() + " events");
            }
            if (events.size() > 0) {
                handler.handle(events, underlyingProducer, serializer); // 处理数据，使用underlyingProducer 发送，其实还是同步发送对象
            }
        } catch (Exception e) {
            logger.error("Error in handling batch of " + events.size() + " events", e);
        }
    }
    
    private List<QueueItem<T>> processEvents() {
        List<QueueItem<T>> queueItemList = new ArrayList<>();
        SystemTime  systemTime = new SystemTime();
        Long lastSend = systemTime.milliseconds();
        List<QueueItem<T>> events = new ArrayList<QueueItem<T>>();
        Boolean full = false;
        
        List<QueueItem<T>> queueItemList1 = queue.stream().filter(item -> {
            if (item != null) {
                return item != shutdownCommand;
            } else {
                return true;
            }
        }).collect(Collectors.toList());
        
        for (QueueItem<T> currentQueueItem : queueItemList1) {
            Long elapsed = systemTime.milliseconds() - lastSend;
            boolean expired = (currentQueueItem == null);
            if (currentQueueItem != null) {
                if (logger.isTraceEnabled())
                    logger.trace("Dequeued item for topic %s and partition %d".format(currentQueueItem.getTopic(), currentQueueItem.getPartition()));
                // handle the dequeued current item
                if (cbkHandler != null) {
                    events.addAll(cbkHandler.afterDequeuingExistingData(currentQueueItem));
                }
            } else {
                events.add(currentQueueItem);
                // check if the batch size is reached
                full = events.size() >= batchSize;
            }
            if (full || expired) { // 如果 events 这个集合满了，或者超出发送时间，就开始调用 tryToHandle 发送处理数据
                if (logger.isDebugEnabled()) {
                    if (expired) logger.debug(elapsed + " ms elapsed. Queue time reached. Sending..");
                    if (full) logger.debug("Batch full. Sending..");
                }
                // if either queue time has reached or batch size has reached, dispatch to event handler
                tryToHandle(events);// 真正的发送event数据进行处理
                lastSend = systemTime.milliseconds();
                events = new ArrayList<QueueItem<T>>();
            }
        }
        return queueItemList;
    }
    
    
    public void shutdown() {
        handler.close();
        logger.info("Shutdown thread complete");
    }
    
    public void awaitShutdown() {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    
    private void logEvents(String tag, Iterable<QueueItem<T>> events) {
        if (logger.isTraceEnabled()) {
            logger.trace("events for " + tag + ":");
            for (QueueItem<T> event : events)
                logger.trace(event.getData().toString());
        }
    }
    
}
