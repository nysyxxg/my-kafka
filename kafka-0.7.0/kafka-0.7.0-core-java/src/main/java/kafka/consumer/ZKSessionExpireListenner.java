package kafka.consumer;

import kafka.utils.ZKGroupDirs;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.IZkStateListener;
import org.I0Itec.zkclient.ZkClient;
import org.apache.log4j.Logger;
import org.apache.zookeeper.Watcher;

public class ZKSessionExpireListenner  implements IZkStateListener {
    
    private Logger logger = Logger.getLogger(ZKSessionExpireListenner.class);
    
    ZkClient zkClient;
    ZKGroupDirs dirs;
    String consumerIdString;
    TopicCount topicCount;
    ZKRebalancerListener loadBalancerListener;
    public ZKSessionExpireListenner(ZkClient zkClient, ZKGroupDirs dirs, String consumerIdString,
                                    TopicCount topicCount, ZKRebalancerListener loadBalancerListener) {
        this.zkClient = zkClient;
        this.dirs = dirs;
        this.consumerIdString = consumerIdString;
        this.loadBalancerListener = loadBalancerListener;
        this.topicCount = topicCount;
    }
    
    @Override
    public void handleStateChanged(Watcher.Event.KeeperState keeperState) throws Exception {
    
    }
    
    @Override
    public void handleNewSession() throws Exception {
        logger.info("ZK expired; release old broker parition ownership; re-register consumer " + consumerIdString);
        loadBalancerListener.resetState();
        ZkUtils.registerConsumerInZK(zkClient,dirs, consumerIdString, topicCount);
        // explicitly trigger load balancing for this consumer
        loadBalancerListener.syncedRebalance();
    }
    
    
}