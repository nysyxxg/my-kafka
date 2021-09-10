package kafka.consumer;

import kafka.api.FetchRequest;
import kafka.api.MultiFetchResponse;
import kafka.api.OffsetRequest;
import kafka.cluster.Broker;
import kafka.cluster.Partition;
import kafka.common.ErrorMapping;
import kafka.message.ByteBufferMessageSet;
import kafka.utils.Utils;
import kafka.utils.ZKGroupTopicDirs;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.ZkClient;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class FetcherRunnable extends Thread {
    private Logger logger = Logger.getLogger(getClass());
    
    String name;
    ZkClient zkClient;
    ConsumerConfig config;
    Broker broker;
    List<PartitionTopicInfo> partitionTopicInfos;
    
    private CountDownLatch shutdownLatch = new CountDownLatch(1);
    private SimpleConsumer simpleConsumer;
    private volatile boolean stopped = false;
    
    
    public FetcherRunnable(String threadName,
                           ZkClient zkClient,
                           ConsumerConfig config,
                           Broker broker,
                           List<PartitionTopicInfo> partitionTopicInfos) {
        this.name = threadName;
        this.zkClient = zkClient;
        this.config = config;
        this.broker = broker;
        this.partitionTopicInfos = partitionTopicInfos;
        this.simpleConsumer = new SimpleConsumer(this.broker.host, this.broker.port, config.socketTimeoutMs, config.socketBufferSize);
    }
    
    
    public FetcherRunnable(ZkClient zkClient, ConsumerConfig config,
                           Broker broker, List<PartitionTopicInfo> partitionTopicInfos) {
        this(null, zkClient, config, broker, partitionTopicInfos);
        
    }
    
    public void shutdown() {
        stopped = true;
        interrupt();
        logger.debug("awaiting shutdown on fetcher " + name);
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.debug("shutdown of fetcher " + name + " thread complete");
    }
    
    public void run() {
        for (PartitionTopicInfo info : partitionTopicInfos) {
            logger.info(name + " start fetching topic: " + info.topic + " part: " + info.partition.partId + " offset: "
                    + info.getFetchOffset() + " from " + broker.host + ":" + broker.port);
        }
        try {
            while (!stopped) {
                List<FetchRequest> fetches = partitionTopicInfos.stream().map(info -> {
                    return new FetchRequest(info.topic, info.partition.partId, info.getFetchOffset(), config.fetchSize);
                }).collect(Collectors.toList());
                
                if (logger.isTraceEnabled()) {
                    logger.trace("fetch request: " + fetches.toString());
                }
                System.out.println(Thread.currentThread().getName() + "-----------fetches =  " + fetches.size());
                MultiFetchResponse response = simpleConsumer.multifetch(fetches);
                System.out.println(Thread.currentThread().getName() + "-----------fetches =  " +" + numSets = " + response.numSets);
                long read = 0L;
                int index = 0;
                //  for ((messages, info) <- response.zip(partitionTopicInfos)) {  scala 使用zip拉链操作，需要转化
                for (ByteBufferMessageSet messages : response) {
                    PartitionTopicInfo info = partitionTopicInfos.get(index);
                    try {
                        boolean done = false;
                        if (messages.getErrorCode() == ErrorMapping.OffsetOutOfRangeCode) {
                            logger.info("offset for " + info + " out of range");
                            // see if we can fix this error
                            Long resetOffset = resetConsumerOffsets(info.topic, info.partition);
                            if (resetOffset >= 0) {
                                info.resetFetchOffset(resetOffset);
                                info.resetConsumeOffset(resetOffset);
                                done = true;
                            }
                        }
                        if (!done) {
                            System.out.println(Thread.currentThread().getName() + "-------FetcherRunnable----添加消息到队列中-----run---");
                            // 将拉取的数据，转化为数据块对象，存储到队列中BlockingQueue
                            read += info.enqueue(messages, info.getFetchOffset());
                        }
                    } catch (Exception e2) {
                        if (!stopped) {
                            logger.error("error in FetcherRunnable for " + info, e2);
                            info.enqueueError(e2, info.getFetchOffset());
                        }
                        throw e2;
                    }
                    index += 1;
                }
                
                if (logger.isTraceEnabled()) {
                    logger.trace("fetched bytes: " + read);
                }
                if (read == 0) {
                    logger.debug("backing off " + config.backoffIncrementMs + " ms");
                    Thread.sleep(config.backoffIncrementMs);
                }
            }
        } catch (Exception e) {
            if (stopped) {
                logger.info("FecherRunnable " + this + " interrupted");
            } else {
                logger.error("error in FetcherRunnable ", e);
            }
        }
        
        logger.info("stopping fetcher " + name + " to host " + broker.host);
        //Utils.swallow(logger.info, simpleConsumer.close);
        simpleConsumer.close();
        shutdownComplete();
    }
    
    public void shutdownComplete() {
        shutdownLatch.countDown();
    }
    
    //  重新设置消费者的 offset
    public Long resetConsumerOffsets(String topic, Partition partition) {
        Long offset = 0L;
        String autoOffsetReset = config.autoOffsetReset;
        if (OffsetRequest.SmallestTimeString.equalsIgnoreCase(autoOffsetReset)) {
            offset = OffsetRequest.EarliestTime;
        } else if (OffsetRequest.LargestTimeString.equalsIgnoreCase(OffsetRequest.LargestTimeString)) {
            offset = OffsetRequest.LatestTime;
        } else {
            return -1l;
        }
        
        Long offsets[] = simpleConsumer.getOffsetsBefore(topic, partition.partId, offset, 1);
        ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(config.groupId, topic);
        
        String signStr = "";
        if (offset == OffsetRequest.EarliestTime) {
            signStr = "earliest";
        } else {
            signStr = "latest";
        }
        
        // reset manually in zookeeper
        logger.info("updating partition " + partition.name + " for topic " + topic + " with " + signStr + "offset " + offsets[0]);
        // 更新消费者的offset
        ZkUtils.updatePersistentPath(zkClient, topicDirs.getConsumerOffsetDir() + "/" + partition.name, String.valueOf(offsets[0]));
        
        return offsets[0];
    }
    
    
}
