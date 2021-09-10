package kafka.consumer;

import kafka.cluster.Partition;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertTrue;


public class TopicCountTest {
    
    @Test
    public void testBasic() {
        String  consumer = "conusmer1";
        String json = "{\"topic1\": 2, \"topic2\": 3 }";
        TopicCount topicCount = TopicCount.constructTopicCount(consumer, json);
        
        Map<String,Integer> topicCountMap = new HashMap<>();
        topicCountMap.put("topic1" , 2);
        topicCountMap.put("topic2" , 3);
    
        TopicCount expectedTopicCount = new TopicCount(consumer, topicCountMap);
        System.out.println(expectedTopicCount == topicCount);
        System.out.println(expectedTopicCount.equals(topicCount));
    
        TopicCount topicCount2 = TopicCount.constructTopicCount(consumer, expectedTopicCount.toJsonString());
        System.out.println(expectedTopicCount == topicCount2);
        System.out.println(expectedTopicCount.equals(topicCount2));
    }
    
    @Test
    public void  testPartition() {
        assertTrue(new Partition(10, 0) == new Partition(10, 0));
        assertTrue(new Partition(10, 1) != new Partition(10, 0));
    }
    
}
