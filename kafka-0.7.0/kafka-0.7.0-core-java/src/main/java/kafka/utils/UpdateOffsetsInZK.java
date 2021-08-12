package kafka.utils;

import kafka.api.OffsetRequest;
import kafka.cluster.Broker;
import kafka.cluster.Cluster;
import kafka.cluster.Partition;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.SimpleConsumer;
import org.I0Itec.zkclient.ZkClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UpdateOffsetsInZK {
    
    static String Earliest = "earliest";
    static String Latest = "latest";
    
    public static void main(String[] args) {
        if (args.length < 3) {
            usage();
        }
        ConsumerConfig config = new ConsumerConfig(Utils.loadProps(args[1]));
        ZkClient zkClient = new ZkClient(config.zkConnect, config.zkSessionTimeoutMs,
                config.zkConnectionTimeoutMs, new ZKStringSerializer());
        if (args[0].equalsIgnoreCase(Earliest)) {
            getAndSetOffsets(zkClient, OffsetRequest.EarliestTime, config, args[2]);
        } else if (args[0].equalsIgnoreCase(Latest)) {
            getAndSetOffsets(zkClient, OffsetRequest.LatestTime, config, args[2]);
        } else {
            usage();
        }
    }
    
    private static void getAndSetOffsets(ZkClient zkClient, Long offsetOption, ConsumerConfig config, String topic) {
        Cluster cluster = ZkUtils.getCluster(zkClient);
        Map<String, List<String>> partitionsPerTopicMap = ZkUtils.getPartitionsForTopics(zkClient, Arrays.asList(topic).iterator());
        List<String> partitions = null;
        List<String> partitionsForTopicsList = partitionsPerTopicMap.get(topic);
        if (partitionsForTopicsList == null) {
            Collections.sort(partitionsForTopicsList);// 进行排序  old : partitions = l.sortWith((s,t) => s < t)
            partitions = partitionsForTopicsList;
        } else {
            throw new RuntimeException("Can't find topic " + topic);
        }
        
        int numParts = 0;
        for (String partString : partitions) {
            Partition part = Partition.parse(partString);
            Broker broker = cluster.getBroker(part.getBrokerId());
            SimpleConsumer consumer = new SimpleConsumer(broker.host, broker.port, 10000, 100 * 1024);
            Long[] offsets = consumer.getOffsetsBefore(topic, part.partId, offsetOption, 1);
            ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(config.getGroupId(), topic);
    
            System.out.println("updating partition " + part.name + " with new offset: " + offsets[0]);
            ZkUtils.updatePersistentPath(zkClient, topicDirs.getConsumerOffsetDir() + "/" + part.name, offsets[0].toString());
            numParts += 1;
        }
        System.out.println("updated the offset for " + numParts + " partitions");
    }
    
    private static void usage() {
        System.out.println("USAGE: " + UpdateOffsetsInZK.class.getName() + " [earliest | latest] consumer.properties topic");
        System.exit(1);
    }
    
}
