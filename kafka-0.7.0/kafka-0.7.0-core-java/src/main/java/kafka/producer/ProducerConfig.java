package kafka.producer;

import kafka.utils.ZKConfig;

import java.util.Properties;

public class ProducerConfig  extends ZKConfig {
    
    public ProducerConfig(Properties props) {
        super(props);
    }
}
