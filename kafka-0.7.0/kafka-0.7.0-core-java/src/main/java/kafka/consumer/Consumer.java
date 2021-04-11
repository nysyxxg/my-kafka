package kafka.consumer;

import kafka.utils.Utils;
import org.apache.log4j.Logger;

public class Consumer {
    
    private  static Logger logger = Logger.getLogger(Consumer.class);
    
    private static String consumerStatsMBeanName = "kafka:type=kafka.ConsumerStats";
    
    public static ConsumerConnector create(ConsumerConfig consumerConfig) {
        ZookeeperConsumerConnector consumerConnect = new ZookeeperConsumerConnector(consumerConfig);
    
        try {
            Utils.registerMBean(consumerConnect, consumerStatsMBeanName);
        } catch (Exception e) {
            e.printStackTrace();
            logger.warn(e.getMessage(),e);
        }
        return  consumerConnect;
        
    }
    
    public kafka.javaapi.consumer.ConsumerConnector  createJavaConsumerConnector(ConsumerConfig config)  {
        kafka.javaapi.consumer.ZookeeperConsumerConnector consumerConnect = new   kafka.javaapi.consumer.ZookeeperConsumerConnector(config);
    
//        Utils.registerMBean(consumerConnect.underlying, consumerStatsMBeanName);
//        Utils.swallow(logger.warn, );
       return consumerConnect;
    }
    
}
