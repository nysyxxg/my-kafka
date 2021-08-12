package kafka.utils;

public class ZKGroupDirs {
    
    private String group;
    private String consumerDir;
    private String consumerGroupDir;
    private String consumerRegistryDir;
    
    public ZKGroupDirs(String group) {
        this.group = group;
        this.consumerDir = ZkUtils.ConsumersPath;
        this.consumerGroupDir = consumerDir + "/" + group;
        this.consumerRegistryDir = consumerGroupDir + "/ids";
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getConsumerDir() {
        return consumerDir;
    }
    
    public void setConsumerDir(String consumerDir) {
        this.consumerDir = consumerDir;
    }
    
    public String getConsumerGroupDir() {
        return consumerGroupDir;
    }
    
    public void setConsumerGroupDir(String consumerGroupDir) {
        this.consumerGroupDir = consumerGroupDir;
    }
    
    public String getConsumerRegistryDir() {
        return consumerRegistryDir;
    }
    
    public void setConsumerRegistryDir(String consumerRegistryDir) {
        this.consumerRegistryDir = consumerRegistryDir;
    }
}