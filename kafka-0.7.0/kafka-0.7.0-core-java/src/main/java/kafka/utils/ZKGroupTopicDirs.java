package kafka.utils;


public class ZKGroupTopicDirs extends ZKGroupDirs {
    
    private String group;
    private String topic;
    
    private String consumerOffsetDir;
    private String consumerOwnerDir;
    
    public ZKGroupTopicDirs(String groupId, String topic) {
        super(groupId);
        this.group = groupId;
        this.topic = topic;
        this.consumerOffsetDir = this.getConsumerGroupDir() + "/offsets/" + topic;
        this.consumerOwnerDir = this.getConsumerGroupDir() + "/owners/" + topic;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getTopic() {
        return topic;
    }
    
    public void setTopic(String topic) {
        this.topic = topic;
    }
    
    public String getConsumerOffsetDir() {
        return consumerOffsetDir;
    }
    
    public void setConsumerOffsetDir(String consumerOffsetDir) {
        this.consumerOffsetDir = consumerOffsetDir;
    }
    
    public String getConsumerOwnerDir() {
        return consumerOwnerDir;
    }
    
    public void setConsumerOwnerDir(String consumerOwnerDir) {
        this.consumerOwnerDir = consumerOwnerDir;
    }
}
