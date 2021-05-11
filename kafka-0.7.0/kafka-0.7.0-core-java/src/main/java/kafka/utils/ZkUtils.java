package kafka.utils;

import kafka.cluster.Broker;
import kafka.cluster.Cluster;
import kafka.consumer.TopicCount;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkNoNodeException;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.apache.log4j.Logger;

import java.util.*;

public class ZkUtils {
    private static Logger logger = Logger.getLogger(ZkUtils.class);
    public static String ConsumersPath = "/consumers";
    public static String BrokerIdsPath = "/brokers/ids";
    public static String BrokerTopicsPath = "/brokers/topics";
    
    /**
     * make sure a persistent path exists in ZK. Create the path if not exist.
     */
    public static void makeSurePersistentPathExists(ZkClient client, String path) {
        if (!client.exists(path)) {
            client.createPersistent(path, true);
        }// won't throw NoNodeException or NodeExistsException
    }
    
    //  create the parent path
    private static void createParentPath(ZkClient client, String path) {
        String parentDir = path.substring(0, path.lastIndexOf('/'));
        if (parentDir.length() != 0) {
            client.createPersistent(parentDir, true);
        }
    }
    
    // Create an ephemeral node with the given path and data. Create parents if necessary.
    private static void createEphemeralPath(ZkClient client, String path, String data) {
        try {
            logger.info("createEphemeralPath使用给定的路径和数据创建临时节点....................path=" + path );
            client.createEphemeral(path, data);
        } catch (ZkNoNodeException e) {
            createParentPath(client, path);
            client.createEphemeral(path, data);
        }
    }
    
    
    /**
     * Create an ephemeral node with the given path and data. 使用给定的路径和数据创建临时节点
     * Throw NodeExistException if node already exists.
     */
    public static void createEphemeralPathExpectConflict(ZkClient client, String path, String data) {
        try {
            logger.info("使用给定的路径和数据创建临时节点.....................");
            createEphemeralPath(client, path, data);
        } catch (ZkNodeExistsException e) {
            // this can happen when there is connection loss; make sure the data is what we intend to write
            String storedData = null;
            try {
                storedData = readData(client, path);
            } catch (ZkNoNodeException e1) {
                // the node disappeared; treat as if node existed and let caller handles this
                throw e1;
            }
            if (storedData == null || storedData != data) {
                logger.info("conflict in " + path + " data: " + data + " stored data: " + storedData);
                throw e;
            } else {
                // otherwise, the creation succeeded, return normally
                logger.info(path + " exists with value " + data + " during connection loss; this is ok");
            }
        }
    }
    
    
    public static void updatePersistentPath(ZkClient client, String path, String data) {
        try {
            client.writeData(path, data);
        } catch (ZkNoNodeException e) {
            createParentPath(client, path);
            try {
                client.createPersistent(path, data);
            } catch (ZkNodeExistsException ex) {
                client.writeData(path, data);
                throw ex;
            }
        }
    }
    
    public static void updateEphemeralPath(ZkClient client, String path, String data) {
        try {
            client.writeData(path, data);
        } catch (ZkNoNodeException e) {
            createParentPath(client, path);
            client.createEphemeral(path, data);
        }
    }
    
    
    public static void deletePath(ZkClient client, String path) {
        try {
            client.delete(path);
        } catch (ZkNoNodeException e) {
            // this can happen during a connection loss event, return normally
            logger.info(path + " deleted during connection loss; this is ok");
            throw e;
        }
    }
    
    
    static void deletePathRecursive(ZkClient client, String path) {
        try {
            client.deleteRecursive(path);
        } catch (ZkNoNodeException e2) {
            // this can happen during a connection loss event, return normally
            logger.info(path + " deleted during connection loss; this is ok");
            throw e2;
        }
    }
    
    public static String readData(ZkClient client, String path) {
        return client.readData(path);
    }
    
    
    public static String readDataMaybeNull(ZkClient client, String path) {
        return client.readData(path, true);
    }
    
    List<String> getChildren(ZkClient client, String path) {
        // triggers implicit conversion from java list to scala Seq
        return client.getChildren(path);
    }
    
    public  static List<String> getChildrenParentMayNotExist(ZkClient client, String path) {
        java.util.List<String> ret = null;
        try {
            ret = client.getChildren(path);
        } catch (ZkNoNodeException e) {
            return null;
        }
        return ret;
    }
    
    /**
     * Check if the given path exists
     */
    Boolean pathExists(ZkClient client, String path) {
        return client.exists(path);
    }
    
    String getLastPart(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
    
    public static Cluster getCluster(ZkClient zkClient) {
        Cluster cluster = new Cluster();
        List<String> nodes = getChildrenParentMayNotExist(zkClient, BrokerIdsPath);
        for (String node : nodes) {
            String brokerZKString = readData(zkClient, BrokerIdsPath + "/" + node);
            cluster.add(Broker.createBroker(Integer.valueOf(node), brokerZKString));
        }
        return cluster;
    }
    
    public static Map<String, List<String>> getPartitionsForTopics(ZkClient zkClient, Iterator<String> topics) {
        Map<String, List<String>> ret = new HashMap<String, List<String>>();
        while (topics.hasNext()) {
            String topic = topics.next();
            List<String> partList = new ArrayList<>();
            List<String> brokers = getChildrenParentMayNotExist(zkClient, BrokerTopicsPath + "/" + topic);
            for (String broker : brokers) {
                int nParts = Integer.parseInt(readData(zkClient, BrokerTopicsPath + "/" + topic + "/" + broker));
                for (int part = 0; part < nParts; part++)
                    partList.add(broker + "-" + part);
            }
            Collections.sort(partList);
            ret.put(topic, partList);
        }
        return ret;
    }
    
    static void setupPartition(ZkClient zkClient, int brokerId, String host, int port, String topic, int nParts) {
        String brokerIdPath = BrokerIdsPath + "/" + brokerId;
        Broker broker = new Broker(brokerId, Integer.toString(brokerId), host, port);
        createEphemeralPathExpectConflict(zkClient, brokerIdPath, broker.getZKString());
        String brokerPartTopicPath = BrokerTopicsPath + "/" + topic + "/" + brokerId;
        createEphemeralPathExpectConflict(zkClient, brokerPartTopicPath, Integer.toString(nParts));
    }
    
    static void deletePartition(ZkClient zkClient, int brokerId, String topic) {
        String brokerIdPath = BrokerIdsPath + "/" + brokerId;
        zkClient.delete(brokerIdPath);
        String brokerPartTopicPath = BrokerTopicsPath + "/" + topic + "/" + brokerId;
        zkClient.delete(brokerPartTopicPath);
    }
    
    
    public static void registerConsumerInZK(ZkClient zkClient,
                                            ZKGroupDirs dirs, String consumerIdString, TopicCount topicCount) {
        logger.info("begin registering consumer " + consumerIdString + " in ZK");
        ZkUtils.createEphemeralPathExpectConflict(zkClient, dirs.consumerRegistryDir + "/" + consumerIdString, topicCount.toJsonString());
        logger.info("end registering consumer " + consumerIdString + " in ZK");
    }
    
    
}
