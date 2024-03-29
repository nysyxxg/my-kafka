package kafka.server;

import kafka.cluster.Broker;
import kafka.log.LogManager;
import kafka.utils.ZKStringSerializer;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.IZkStateListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.apache.log4j.Logger;
import org.apache.zookeeper.Watcher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KafkaZooKeeper {
    private KafkaConfig config;
    private LogManager logManager;
    
    private Logger logger = Logger.getLogger(KafkaZooKeeper.class);
    
    String brokerIdPath;
    ZkClient zkClient ;
    List<String> topics = new ArrayList<String>();
    Object lock = new Object();
    
    
    public KafkaZooKeeper(KafkaConfig config, LogManager logManager) {
        this.config = config;
        this.logManager = logManager;
        this.brokerIdPath = ZkUtils.BrokerIdsPath + "/" + config.brokerId;
    }
    
    public void startup() {
        logger.info("开始连接Zk服务器端: connecting to ZK: " + config.getZkConnect());
        zkClient = new ZkClient(config.getZkConnect(), config.getZkSessionTimeoutMs(), config.getZkConnectionTimeoutMs(),
                new ZKStringSerializer());
        zkClient.subscribeStateChanges(new SessionExpireListener());
    }
    
    public void registerTopicInZk(String topic) {
        registerTopicInZkInternal(topic);
        synchronized (lock) {
            topics.add(topic);
        }
    }
    
    private void registerTopicInZkInternal(String topic) {
        String brokerTopicPath = ZkUtils.BrokerTopicsPath + "/" + topic + "/" + config.brokerId;
        int numParts = 0;
        Map<String, Integer> map = logManager.getTopicPartitionsMap();
        Integer num = map.get(topic);
        if (num != null) {
            numParts = num;
        } else {
            numParts = config.numPartitions;
        }
        logger.info("Begin registering broker topic " + brokerTopicPath + " with " + numParts + " partitions");
        ZkUtils.createEphemeralPathExpectConflict(zkClient, brokerTopicPath, String.valueOf(numParts));
        logger.info("End registering broker topic " + brokerTopicPath);
    }
    
    public void registerBrokerInZk() {
        logger.info("开始注册Broker.... Registering broker " + brokerIdPath);
        String hostName = "";
        if (config.hostName == null) {
            try {
                hostName = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        } else {
            hostName = config.hostName;
        }
        
        String creatorId = hostName + "-" + System.currentTimeMillis();
     
        Broker broker = new Broker(config.brokerId, creatorId, hostName, config.port);
        try {
            logger.info("开始创建临时节点...... " + brokerIdPath);
            ZkUtils.createEphemeralPathExpectConflict(zkClient, brokerIdPath, broker.getZKString());
        } catch (ZkNodeExistsException e) {
            throw new RuntimeException("A broker is already registered on the path " + brokerIdPath + ". This probably " +
                    "indicates that you either have configured a brokerid that is already in use, or " +
                    "else you have shutdown this broker and restarted it faster than the zookeeper " +
                    "timeout so it appears to be re-registering.");
        }
        logger.info("注册Broker成功......Registering broker " + brokerIdPath + " succeeded with " + broker);
    }
    
    public void close() {
        if (zkClient != null) {
            logger.info("Closing zookeeper client...");
            zkClient.close();
        }
    }
    
    class SessionExpireListener implements IZkStateListener {
        public SessionExpireListener() {
            logger.info("初始化 SessionExpireListener........");
        }
        
        @Override
        public void handleStateChanged(Watcher.Event.KeeperState state) {
        }
        
        @Override
        public void handleNewSession() {
            logger.info("re-registering broker info in ZK for broker " + config.brokerId);
            registerBrokerInZk();
            synchronized (lock) {
                logger.info("re-registering broker topics in ZK for broker " + config.brokerId);
                for (String topic : topics) {
                    registerTopicInZkInternal(topic);
                }
            }
            logger.info("done re-registering broker");
        }
    }
    
}
