package kafka;

import kafka.consumer.ConsumerConfig;
import kafka.producer.ProducerConfig;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.server.KafkaServerStartable;
import kafka.utils.Utils;
import org.apache.log4j.Logger;
import org.apache.log4j.jmx.LoggerDynamicMBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Kafka {
    
    private static Logger logger = Logger.getLogger(Kafka.class);
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[]{"D:\\my-kafka\\kafka-0.7.0\\config\\server.properties"
            };
//            "D:\\my-kafka\\kafka-0.7.0\\config\\consumer.properties",
//            "D:\\my-kafka\\kafka-0.7.0\\config\\producer.properties"
        }
        
        String kafkaLog4jMBeanName = "kafka:type=kafka.KafkaLog4j";
        try {
            Utils.registerMBean(new LoggerDynamicMBean(Logger.getRootLogger()), kafkaLog4jMBeanName);
        } catch (Exception e) {
            e.printStackTrace();
            logger.warn(e.getMessage(), e);
        }
        List<Integer> listLen = new ArrayList();
        listLen.add(1);
        listLen.add(3);
        
        if (!listLen.contains(args.length)) {
            System.out.println("USAGE: java [options] %s server.properties [consumer.properties producer.properties]".format(KafkaServer.class.getSimpleName()));
            System.exit(1);
        }
        
        try {
            Properties props = Utils.loadProps(args[0]);
            
            KafkaConfig serverConfig = new KafkaConfig(props);
            
            int len = args.length;
            KafkaServerStartable kafkaServerStartble = null;
            if (len == 3) {
                ConsumerConfig consumerConfig = new ConsumerConfig(Utils.loadProps(args[1]));
                ProducerConfig producerConfig = new ProducerConfig(Utils.loadProps(args[2]));
                kafkaServerStartble = new KafkaServerStartable(serverConfig, consumerConfig, producerConfig);
            } else if (len == 1) {
                kafkaServerStartble = new KafkaServerStartable(serverConfig);
            }
            
            /**
             * 这个方法的意思就是在jvm中增加一个关闭的钩子，
             * 当jvm关闭的时候，会执行系统中已经设置的所有通过方法addShutdownHook添加的钩子，
             * 当系统执行完这些钩子后，jvm才会关闭。所以这些钩子可以在jvm关闭的时候进行内存清理、对象销毁等操作。
             */
            // attach shutdown handler to catch control-c
            final KafkaServerStartable finalKafkaServerStartble = kafkaServerStartble;
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    finalKafkaServerStartble.shutdown();
                    finalKafkaServerStartble.awaitShutdown();
                }
            });
            kafkaServerStartble.startup();
            kafkaServerStartble.awaitShutdown();
        } catch (Exception e) {
            e.printStackTrace();
            logger.fatal(e);
        }
        System.exit(0);
    }
    
}
