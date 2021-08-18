import com.alibaba.fastjson.JSON;
import kafka.consumer.TopicCount;

public class TestJson {
    
    public static void main(String[] args) {
        String jsonString ="{\"test-xxg-demo\":1}";
//        Object obj  = JSON.parseFull(jsonString).get();
        TopicCount newPerson = JSON.parseObject(jsonString, TopicCount.class);
        System.out.println(newPerson);
    }
}
