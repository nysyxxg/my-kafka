package kafka.server;

import kafka.consumer.*;
import kafka.message.Message;
import kafka.producer.Producer;
import kafka.producer.ProducerConfig;
import kafka.serializer.DefaultDecoder;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class KafkaServerStartable {
    private static KafkaConfig serverConfig;
    private static ConsumerConfig consumerConfig;
    private static ProducerConfig producerConfig;
    private static KafkaServer server = null;
    
    private static EmbeddedConsumer embeddedConsumer = null;
    
    public KafkaServerStartable(KafkaConfig serverConfig, ConsumerConfig consumerConfig, ProducerConfig producerConfig) {
        this.serverConfig = serverConfig;
        this.consumerConfig = consumerConfig;
        this.producerConfig = producerConfig;
        
        init();
    }
    
    public KafkaServerStartable(KafkaConfig serverConfig) {
        this(serverConfig, null, null);
        this.serverConfig = serverConfig;
    }
    
    
    private static void init() {
        server = new KafkaServer(serverConfig);
        if (consumerConfig != null)
            embeddedConsumer = new EmbeddedConsumer(consumerConfig, producerConfig, server);
    }
    
    
    public void shutdown() {
        
        if (embeddedConsumer != null) {
            embeddedConsumer.shutdown();
        }
        server.shutdown();
    }
    
    public void awaitShutdown() {
        server.awaitShutdown();
    }
    
    public void startup() {
        server.startup();
        if (embeddedConsumer != null) {
            embeddedConsumer.startup();
        }
    }
}

class EmbeddedConsumer implements TopicEventHandler {
    
    private Logger logger = Logger.getLogger(EmbeddedConsumer.class);
    
    private ConsumerConfig consumerConfig;
    private ProducerConfig producerConfig;
    private KafkaServer server;
    private List<String> whiteListTopics;
    private List<String> blackListTopics;
    private List<String> mirrorTopics = new ArrayList<>();
    
    private ConsumerConnector consumerConnector = null;
    private ZookeeperTopicEventWatcher topicEventWatcher = null;
    
    private Producer producer = new Producer(producerConfig);
    
    private List<MirroringThread> threadList = new ArrayList<MirroringThread>();
    
    
    public EmbeddedConsumer(ConsumerConfig consumerConfig, ProducerConfig producerConfig, KafkaServer server) {
        this.consumerConfig = consumerConfig;
        this.producerConfig = producerConfig;
        this.server = server;
        this.whiteListTopics = Arrays.asList(consumerConfig.mirrorTopicsWhitelist.split(","));
        this.blackListTopics = Arrays.asList(consumerConfig.mirrorTopicsBlackList.split(","));
        
        
    }
    
    public void startup() {
        topicEventWatcher = new ZookeeperTopicEventWatcher(consumerConfig, this);
    }
    
    public void shutdown() {
        
        // first shutdown the topic watcher to prevent creating new consumer streams
        if (topicEventWatcher != null)
            topicEventWatcher.shutdown();
        logger.info("Stopped the ZK watcher for new topics, now stopping the Kafka consumers");
        // stop pulling more data for mirroring
        if (consumerConnector != null)
            consumerConnector.shutdown();
        logger.info("Stopped the kafka consumer threads for existing topics, now stopping the existing mirroring threads");
        // wait for all mirroring threads to stop
        for (MirroringThread mirroringThread : threadList) {
            mirroringThread.shutdown();
        }
        logger.info("Stopped all existing mirroring threads, now stopping the producer");
        // only then, shutdown the producer
        producer.close();
        logger.info("Successfully shutdown this Kafka mirror");
    }
    
    private boolean isTopicAllowed(String topic) {
        if (!consumerConfig.mirrorTopicsWhitelist.isEmpty())
            return whiteListTopics.contains(topic);
        else
            return !blackListTopics.contains(topic);
    }
    
    
    @Override
    public void handleTopicEvent(List allTopics) {
        List<String> newMirrorTopics = new ArrayList();
        
        List<String> addedTopics = new ArrayList<>();
        for (Object object : allTopics) {
            String topic = (String) object;
            boolean bl = isTopicAllowed(topic);
            if (bl) {
                newMirrorTopics.add(topic);
            }
        }
        
        for (String topic : newMirrorTopics) {
            if (!mirrorTopics.contains(topic)) {
                addedTopics.add(topic);
            }
        }
        
        if (!addedTopics.isEmpty()) {
            String arry[] = addedTopics.toArray(new String[0]);
//            String arry[] = (String[]) addedTopics.toArray();
            logger.info("topic event: added topics = %s".format(String.join(",", arry)));
        }
        
        List<String> deletedTopics = new ArrayList<>();
        for (String topic : mirrorTopics) {
            if (!newMirrorTopics.contains(topic)) {
                deletedTopics.add(topic);
            }
        }
        
        if (!deletedTopics.isEmpty()) {
            logger.info("topic event: deleted topics = %s".format(String.valueOf(deletedTopics.toArray())));
        }
        
        mirrorTopics = newMirrorTopics;
        
        if (!addedTopics.isEmpty() || !deletedTopics.isEmpty()) {
            logger.info("mirror topics = %s".format(String.valueOf(mirrorTopics)));
            try {
                startNewConsumerThreads(makeTopicMap(mirrorTopics));
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
    }
    
    private Map makeTopicMap(List<String> mirrorTopics) {
        if (!mirrorTopics.isEmpty()) {
            String s1 = ":%d,".format(consumerConfig.mirrorConsumerNumThreads + "");
            String s2 = ":%d".format(consumerConfig.mirrorConsumerNumThreads + "");

//            String arry[] = (String[]) mirrorTopics.toArray();
            
            String arry[] = mirrorTopics.toArray(new String[0]);
            
            String dataS = String.join("", arry) + "" + s1 + "" + s2;
            return Utils.getConsumerTopicMap(dataS);
        } else {
            return Utils.getConsumerTopicMap("");
        }
    }
    
    private void startNewConsumerThreads(Map<String, Integer> topicMap) throws UnknownHostException {
        if (!topicMap.isEmpty()) {
            if (consumerConnector != null) {
                consumerConnector.shutdown();
            }
            
            /**
             * Before starting new consumer threads for the updated set of topics,
             * shutdown the existing mirroring threads. Since the consumer connector
             * is already shutdown, the mirroring threads should finish their task almost
             * instantaneously. If they don't, this points to an error that needs to be looked
             * into, and further mirroring should stop
             */
            for (MirroringThread mirroringThread : threadList) {
                mirroringThread.shutdown();
            }
            
            consumerConnector = Consumer.create(consumerConfig);
            Map<String, List<KafkaMessageStream<?>>> topicMessageStreams = consumerConnector.createMessageStreams(topicMap, new DefaultDecoder());
            
            for (String topic : topicMessageStreams.keySet()) {
                List<KafkaMessageStream<?>> streamList = topicMessageStreams.get(topic);
                for (int i = 0; i < streamList.size(); i++) {
                    KafkaMessageStream kafkaMessageStream = streamList.get(i);
                    threadList.add(new MirroringThread(kafkaMessageStream, topic, i));
                }
            }
            for (MirroringThread mirroringThread : threadList) {
                mirroringThread.start();
            }
        } else {
            logger.info("Not starting mirroring threads (mirror topic list is empty)");
        }
    }
    
}


class MirroringThread extends Thread {
    
    KafkaMessageStream<Message> stream;
    String topic;
    int threadId;
    String name;
    private Logger logger = Logger.getLogger(name);
    
    public MirroringThread(String topic, int threadId) {
        this.topic = topic;
        this.threadId = threadId;
        this.name = "kafka-embedded-consumer-%s-%d".format(topic, threadId);
        this.setDaemon(false);
        this.setName(name);
    }
    
    CountDownLatch shutdownComplete = new CountDownLatch(1);
    
    public MirroringThread(KafkaMessageStream kafkaMessageStream, String topic, int i) {
    }
    
    public void shutdown() {
        try {
            shutdownComplete.await();
        } catch (InterruptedException e) {
            logger.fatal("Shutdown of thread " + name + " interrupted. " +
                    "Mirroring thread might leak data!");
        }
    }
}



