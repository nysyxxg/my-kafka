package kafka.javaapi;

import kafka.javaapi.message.ByteBufferMessageSet;
import kafka.network.Request;

import java.nio.ByteBuffer;

public class ProducerRequest extends Request {
    public String topic;
    public int partition;
    public ByteBufferMessageSet messages;
    
    
    private kafka.api.ProducerRequest underlying;
    
    public ProducerRequest(String topic,
                           int partition,
                           ByteBufferMessageSet messages) {
        this.topic = topic;
        this.partition = partition;
        this.messages = messages;
        //需要进行转换
        kafka.message.ByteBufferMessageSet  msgs = Implicits.javaMessageSetToScalaMessageSet(messages);
        this.underlying = new kafka.api.ProducerRequest(topic, partition,msgs );
    }
    
    @Override
    public int sizeInBytes() {
        return underlying.sizeInBytes();
    }
    
    @Override
    public void writeTo(ByteBuffer buffer) {
        underlying.writeTo(buffer);
    }

//    int getTranslatedPartition(randomSelector: String => Int) {
//        underlying.getTranslatedPartition(randomSelector)
//    }
    
    public String toString() {
        return underlying.toString();
    }
    
    
    public boolean equals(Object other) {
        if (other instanceof kafka.api.ProducerRequest) {
            kafka.api.ProducerRequest that = (kafka.api.ProducerRequest) other;
            return (that.equals(this) && topic == that.topic && partition == that.partition && messages.equals(that.messages));
        } else {
            return false;
        }
    }
    
    public Boolean canEqual(Object other) {
        return other instanceof kafka.api.ProducerRequest;
    }
    
    public int hashCode() {
        return 31 + (17 * partition) + topic.hashCode() + messages.hashCode();
    }
    
}
