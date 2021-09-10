import kafka.utils.ZKStringSerializer;
import org.I0Itec.zkclient.ZkClient;

public class TestZkClient {
    public static void main(String[] args) {
        ZkClient  zkClient = new ZkClient("localhost:2181",  10000 ,  10000 ,null);
        boolean  bl = zkClient.exists("/brokers");
        System.out.println(bl);
    }
}
