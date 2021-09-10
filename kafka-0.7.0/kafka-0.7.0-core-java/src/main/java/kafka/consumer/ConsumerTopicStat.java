package kafka.consumer;


import kafka.utils.Pool;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

interface ConsumerTopicStatMBean {
    long getMessagesPerTopic();
}


public class ConsumerTopicStat   implements  ConsumerTopicStatMBean{
    private AtomicLong numCumulatedMessagesPerTopic = new AtomicLong(0);
    private static Logger logger = Logger.getLogger(ConsumerTopicStat.class);
    private static Pool<String,ConsumerTopicStat> stats = new Pool<String, ConsumerTopicStat>();
    
    static ConsumerTopicStat getConsumerTopicStat(String topic) {
        ConsumerTopicStat stat = stats.get(topic);
        if (stat == null) {
            stat = new ConsumerTopicStat();
            if (stats.putIfNotExists(topic, stat) == null) {
                try {
                    Utils.registerMBean(stat, "kafka:type=kafka.ConsumerTopicStat." + topic);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.warn(e.getMessage(),e);
                }
            }
            else
                stat = stats.get(topic);
        }
        return stat;
    }
    
    
    Long recordMessagesPerTopic(int nMessages) {
        return  numCumulatedMessagesPerTopic.getAndAdd(nMessages);
    }
    
    @Override
    public long getMessagesPerTopic() {
        return numCumulatedMessagesPerTopic.get();
    }
}
