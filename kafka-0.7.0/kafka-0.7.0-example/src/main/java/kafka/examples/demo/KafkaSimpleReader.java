package kafka.examples.demo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import kafka.consumer.KafkaMessageStream;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.Message;

public class KafkaSimpleReader {
    private static final Logger Logger = LoggerFactory
            .getLogger(KafkaSimpleWriter.class);
    
    // private static final String KAFKA_TOPIC = "kafka.topic";
    // private static final String KAFKA_BROKERS = "kafka.brokers";
    // private static final String KAFKA_BUNDLE_SIZE = "kafka.bundle.size";
    // private static final String KAFKA_BUNDLE_INTERVAL_MS =
    // "kafka.bundle.interval.ms";
    // private static final String KAFKA_MESSAGE_SIZE = "kafka.message.size";
    // private static final String KAFKA_HEARTBEAT_MS = "kafka.heartbeat.ms";
    
    private String topic;
    private String zkString;
    
    private ConsumerConfig consumerConfig;
    private ConsumerConnector consumerConnector;
    KafkaMessageStream<Message> stream;
    
    private long totalCnt = 0;
    
    public KafkaSimpleReader(String file_path, String topic, String zkString)
            throws IOException {
        this.topic = topic;
        this.zkString = zkString;
        
        Properties properties = new Properties();
        if (file_path != null && file_path.isEmpty() == false) {
            properties.load(Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(file_path));
        }
        
        SetDefaultProperty(properties, "zk.connect", this.zkString);
        SetDefaultProperty(properties, "zk.connectiontimeout.ms", "30000");
        SetDefaultProperty(properties, "groupid", "commonreader");
        
        System.out.println(properties.toString());
        
        this.consumerConfig = new ConsumerConfig(properties);
        
        this.consumerConnector = Consumer.createJavaConsumerConnector(consumerConfig);
        
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        map.put(topic, 1);
        Map<String, List<KafkaMessageStream<Message>>> topicMessageStreams = consumerConnector.createMessageStreams(map);
        stream = topicMessageStreams.get(topic).get(0);
    }
    
    private void SetDefaultProperty(Properties prop, String key,
                                    String defaultValue) {
        if (prop.getProperty(key) == null) {
            prop.setProperty(key, defaultValue);
        }
    }
    
    public void readMessage() {
        for (Message message : stream) {
//            System.out.println("topic: " + msgAndMetadata.topic());
//            Message message = (Message) msgAndMetadata.message();
            ByteBuffer buffer = message.payload();
            byte[] bytes = buffer.array();
            String tmp = new String(bytes);
            System.out.println("message content: " + tmp);
            totalCnt++;
            if (totalCnt % 1000000 == 0) {
                Logger.info("Total send messages:{} to topic:{}", totalCnt,
                        this.topic);
            }
        }
    }
    
    public void close() {
    }
    
    public long getCount() {
        return this.totalCnt;
    }
    
    public static void main(String[] args) throws IOException, ParseException {
        String topic = null;
        String zkString = null;
        String config_path = null;
        
        Options opts = new Options();
        opts.addOption("h", false, "help message");
        opts.addOption("config", true, "config file path");
        opts.addOption("topic", true, "kafka topic");
        opts.addOption("zookeeper", true, "zookeeper list");
        
        BasicParser parser = new BasicParser();
        CommandLine cl = parser.parse(opts, args);
        
        if (cl.hasOption('h') || cl.hasOption("topic") == false
                || cl.hasOption("zookeeper") == false) {
            HelpFormatter hf = new HelpFormatter();
            hf.printHelp("OptionsTip", opts);
            return;
        } else {
            topic = cl.getOptionValue("topic");
            zkString = cl.getOptionValue("zookeeper");
            config_path = cl.getOptionValue("config", null);
        }
        Logger.info("topic={}, zookeeper={}, config={}, data={}", topic, zkString, config_path);
        
        KafkaSimpleReader reader = new KafkaSimpleReader(config_path, topic, zkString);
        
        try {
            reader.readMessage();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("Successfully read records:" + reader.getCount());
    }
}