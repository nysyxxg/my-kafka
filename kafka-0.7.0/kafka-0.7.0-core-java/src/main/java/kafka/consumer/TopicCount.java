package kafka.consumer;

import com.alibaba.fastjson.JSON;
import org.apache.log4j.Logger;

import java.util.*;

public class TopicCount {
    private static Logger logger = Logger.getLogger(TopicCount.class);
    private String consumerIdString;
    private Map<String, Integer> topicCountMap;
    
    public TopicCount(String consumerIdString, Map<String, Integer> topicCountMap) {
        this.consumerIdString = consumerIdString;
        this.topicCountMap = topicCountMap;
    }
    
    int myConversionFunc(String input) {
        return Integer.valueOf(input);
    }
    
    public static TopicCount constructTopicCount(String consumerIdSting, String jsonString) {
        Map<String, Integer> topMap = null;
        try {
//            Object obj = JSON.parseFull(jsonString).get();
            Object obj = JSON.parseObject(jsonString, Map.class);
            if (obj instanceof Map) {
                topMap = (Map<String, Integer>) obj;
            } else {
                throw new RuntimeException("error constructing TopicCount : " + jsonString);
            }
        } catch (Exception e) {
            logger.error("error parsing consumer json string " + jsonString, e);
            throw e;
        }
        return new TopicCount(consumerIdSting, topMap);
    }
    
    public void isAssert(Boolean assertion) {
        if (!assertion) {
            throw new java.lang.AssertionError("assertion failed");
        }
    }
    
    public Map<String, Set<String>> getConsumerThreadIdsPerTopic() {
        Map<String, Set<String>> consumerThreadIdsPerTopicMap = new HashMap<String, Set<String>>();
        for (String topic : topicCountMap.keySet()) {
            int nConsumers = topicCountMap.get(topic);
            Set<String> consumerSet = new HashSet<String>();
            assert (nConsumers >= 1);
            for (int i = 0; i < nConsumers; i++) {
                consumerSet.add(consumerIdString + "-" + i);
            }
            consumerThreadIdsPerTopicMap.put(topic, consumerSet);
        }
        return consumerThreadIdsPerTopicMap;
    }
    
    @Override
    public boolean equals(Object o) {
        System.out.println("-------------------equals--------------------");
        if (this == o) return true;
        if (!(o instanceof TopicCount)) return false;
        TopicCount that = (TopicCount) o;
        return Objects.equals(consumerIdString, that.consumerIdString) &&
                Objects.equals(topicCountMap, that.topicCountMap);
    }
    
//    @Override
//    public int hashCode() {
//        return Objects.hash(consumerIdString, topicCountMap);
//    }
    
//    @Override
//    public boolean equals(TopicCount obj) {
//        if (obj == null) {
//            return false;
//        }else {
//            TopicCount n = (TopicCount) obj;
//            return consumerIdString == n.consumerIdString && topicCountMap == n.topicCountMap;
//        }
//        return false;
//    }
    
    public String toJsonString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{ ");
        int i = 0;
        for (String topic : topicCountMap.keySet()) {
            int nConsumers = topicCountMap.get(topic);
            if (i > 0) {
                builder.append(",");
            }
            builder.append("\"" + topic + "\": " + nConsumers);
            i += 1;
        }
        builder.append(" }");
        return builder.toString();
    }
}
