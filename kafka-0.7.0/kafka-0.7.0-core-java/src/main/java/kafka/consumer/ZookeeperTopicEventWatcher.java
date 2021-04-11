package kafka.consumer;

import kafka.utils.Utils;
import kafka.utils.ZKStringSerializer;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkStateListener;
import org.I0Itec.zkclient.ZkClient;
import org.apache.log4j.Logger;
import org.apache.zookeeper.Watcher;

import java.util.List;

public class ZookeeperTopicEventWatcher {
    private Logger logger = Logger.getLogger(getClass());
    private ConsumerConfig config;
    private TopicEventHandler<String> eventHandler;
    
    private Object lock = new Object();
    
    private ZkClient zkClient = new ZkClient(config.zkConnect, config.zkSessionTimeoutMs,
            config.zkConnectionTimeoutMs, new ZKStringSerializer());
    
    
    public ZookeeperTopicEventWatcher(ConsumerConfig config,
                                      TopicEventHandler<String> eventHandler) {
        this.config = config;
        this.eventHandler = eventHandler;
        startWatchingTopicEvents();
    }
    
    private void startWatchingTopicEvents() {
        ZkTopicEventListener topicEventListener = new ZkTopicEventListener();
        ZkUtils.makeSurePersistentPathExists(zkClient, ZkUtils.BrokerTopicsPath);
        
        zkClient.subscribeStateChanges(new ZkSessionExpireListener(topicEventListener));
    
        List<String> topics = zkClient.subscribeChildChanges(ZkUtils.BrokerTopicsPath, topicEventListener);
        
        // call to bootstrap topic list
        topicEventListener.handleChildChange(ZkUtils.BrokerTopicsPath, topics);
    }
    
    private void stopWatchingTopicEvents() { zkClient.unsubscribeAll(); }
    
    public void shutdown() {
         synchronized(lock) {
            try {
                if (zkClient != null) {
                    stopWatchingTopicEvents();
                    zkClient.close();
                    zkClient = null;
                }
                else
                    logger.warn("Cannot shutdown already shutdown topic event watcher.");
            }
            catch (Exception e){
                    logger.fatal(e);
                    logger.fatal(Utils.stackTrace(e));
            }
        }
    }
    
    class ZkTopicEventListener implements IZkChildListener {
        
        @Override
        public void handleChildChange(String parent, java.util.List<String> children) {
            synchronized (lock) {
                try {
                    if (zkClient != null) {
                        List<String> latestTopics = zkClient.getChildren(ZkUtils.BrokerTopicsPath);
                        logger.debug("all topics: %s".format(latestTopics.toArray().toString()));
                        eventHandler.handleTopicEvent(latestTopics);
                    }
                } catch (Exception e) {
                    logger.fatal(e);
                    logger.fatal(Utils.stackTrace(e));
                }
            }
        }
        
    }
    
    class ZkSessionExpireListener  implements IZkStateListener {
        ZkTopicEventListener topicEventListener;
        public ZkSessionExpireListener(ZkTopicEventListener topicEventListener) {
            this.topicEventListener = topicEventListener;
        }
    
        @Override
        public void handleStateChanged(Watcher.Event.KeeperState state) throws Exception {
        
        }
    
        @Override
        public void handleNewSession() throws Exception {
            synchronized(lock) {
                if (zkClient != null) {
                    logger.info("ZK expired: resubscribing topic event listener to topic registry");
                    zkClient.subscribeChildChanges(ZkUtils.BrokerTopicsPath, topicEventListener);
                }
            }
        }
    }
}
