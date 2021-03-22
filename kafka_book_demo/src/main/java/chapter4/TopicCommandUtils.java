package chapter4;

/**
 * 代码清单4-1 & 4-2
 * Created by 朱小厮 on 2018/9/9.
 */
public class TopicCommandUtils {

    public static void main(String[] args) {
//        createTopic();
//        describeTopic();
        listTopic();
    }

    /**
     * 代码清单4-1
     */
    public static void createTopic(){
        String[] options = new String[]{
                "--zookeeper", "xxg.kafka.cn:2181",
                "--create",
                "--replication-factor", "1",  // 指定副本
                "--partitions", "1",  // 指定分区
                "--topic", "topic-create-api" // 指定topic名称
        };
        kafka.admin.TopicCommand.main(options);
    }

    /**
     * 代码清单4-2
     */
    public static void describeTopic(){
        String[] options = new String[]{
                "--zookeeper", "xxg.kafka.cn:2181",
                "--describe",
                "--topic", "topic-create-api"
        };
        kafka.admin.TopicCommand.main(options);
    }

    public static void listTopic(){
        String[] options = new String[]{
                "--zookeeper", "xxg.kafka.cn:2182", "--list"
        };
        kafka.admin.TopicCommand.main(options);
    }
}
