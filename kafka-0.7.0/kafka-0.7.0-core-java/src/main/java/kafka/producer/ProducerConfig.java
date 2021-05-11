package kafka.producer;

import kafka.common.InvalidConfigException;
import kafka.message.CompressionCodec;
import kafka.utils.Utils;
import kafka.utils.ZKConfig;

import java.util.List;
import java.util.Properties;

public class ProducerConfig extends ZKConfig {
    Properties props;
    String brokerList;
    
    String partitionerClass;
    
    String producerType;
    
    CompressionCodec compressionCodec;
    
    List<String> compressedTopics;
    
    int zkReadRetries = 0;
    
    public ProducerConfig(Properties props) {
        super(props);
        this.props = props;
        
        this.brokerList = Utils.getString(props, "broker.list", null);
        if (brokerList != null && Utils.getString(props, "partitioner.class", null) != null) {
            throw new InvalidConfigException("partitioner.class cannot be used when broker.list is set");
        }
        
        this.partitionerClass = Utils.getString(props, "partitioner.class", "kafka.producer.DefaultPartitioner");
        
        this.producerType = Utils.getString(props, "producer.type", "sync");
        
        this.compressionCodec = Utils.getCompressionCodec(props, "compression.codec");
        
        this.compressedTopics = Utils.getCSVList(Utils.getString(props, "compressed.topics", null));
        
        this.zkReadRetries = Utils.getInt(props, "zk.read.num.retries", 3);
        
    }
}
