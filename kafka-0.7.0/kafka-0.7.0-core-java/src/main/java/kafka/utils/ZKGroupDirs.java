package kafka.utils;

public class ZKGroupDirs {
    String group;
    String consumerDir = ZkUtils.ConsumersPath;
    String consumerGroupDir = consumerDir + "/" + group;
    public String consumerRegistryDir = consumerGroupDir + "/ids";
    
    public ZKGroupDirs(String group) {
        this.group = group;
    }
}