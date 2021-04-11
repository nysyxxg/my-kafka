package kafka.consumer;

import kafka.cluster.Broker;
import kafka.cluster.Cluster;
import org.I0Itec.zkclient.ZkClient;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.BlockingQueue;

public class Fetcher {
    
    private Logger logger = Logger.getLogger(getClass());
    private FetcherRunnable EMPTY_FETCHER_THREADS[] = new FetcherRunnable[0];
    
    private volatile FetcherRunnable fetcherThreads[] = EMPTY_FETCHER_THREADS;
    
    private  ConsumerConfig config;
    private  ZkClient zkClient;
    public Fetcher(ConsumerConfig config, ZkClient zkClient) {
        this.config = config;
        this.zkClient = zkClient;
    }
    
    
    public void shutdown() {
        // shutdown the old fetcher threads, if any
        for (FetcherRunnable fetcherThread : fetcherThreads) {
            fetcherThread.shutdown();
        }
        fetcherThreads = EMPTY_FETCHER_THREADS;
    }
    
    
    void initConnections(Iterable<PartitionTopicInfo> topicInfos, Cluster cluster,
                         Iterable<BlockingQueue<FetchedDataChunk>> queuesTobeCleared) {
        shutdown();
        
        if (topicInfos == null) {
            return;
        }
        Iterator<BlockingQueue<FetchedDataChunk>> iterator = queuesTobeCleared.iterator();
        while (iterator.hasNext()) {
            BlockingQueue<FetchedDataChunk> blockingQueue = iterator.next();
            blockingQueue.clear();
        }
        
        // re-arrange by broker id
        Map<Integer, List<PartitionTopicInfo>> m = new HashMap<Integer, List<PartitionTopicInfo>>();
        
        Iterator<PartitionTopicInfo> it = topicInfos.iterator();
        while (it.hasNext()) {
            PartitionTopicInfo info = it.next();
            List<PartitionTopicInfo> list = m.get(info.brokerId);
            if (list == null) {
                List<PartitionTopicInfo> listInfo = new ArrayList<>();
                listInfo.add(info);
                m.put(info.brokerId, listInfo);
            } else {
                list.add(info);
                m.put(info.brokerId, list);
            }
        }
        
        // open a new fetcher thread for each broker
        List<Integer>  ids = new ArrayList();
        List<Broker>  brokers = new ArrayList<>();
        
        Iterator<PartitionTopicInfo> it2 = topicInfos.iterator();
        while (it2.hasNext()) {
            PartitionTopicInfo partitionTopicInfo =  it2.next();
            int brokerId = partitionTopicInfo.brokerId;
            ids.add(brokerId);
    
            Broker  broker =  cluster.getBroker(brokerId);
            brokers.add(broker);
        }
        
        fetcherThreads = new FetcherRunnable[brokers.size()];
        int i = 0;
        for (Broker broker : brokers) {
            FetcherRunnable fetcherThread = new FetcherRunnable("FetchRunnable-" + i, zkClient, config, broker, m.get(broker.getId()));
            fetcherThreads[i] = fetcherThread;
            fetcherThread.start();
            i += 1;
        }
    }
    
}
