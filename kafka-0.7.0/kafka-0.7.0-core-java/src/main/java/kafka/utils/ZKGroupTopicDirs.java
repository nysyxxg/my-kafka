package kafka.utils;



public class ZKGroupTopicDirs extends ZKGroupDirs {
    public String group;
    public String topic;
    
    public String consumerOffsetDir = consumerGroupDir + "/offsets/" + topic;
    public String consumerOwnerDir = consumerGroupDir + "/owners/" + topic;
    
    public ZKGroupTopicDirs(String groupId, String topic) {
        super(groupId);
        this.group = groupId;
        this.topic = topic;
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
