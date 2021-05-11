package kafka.api;

import kafka.message.ByteBufferMessageSet;
import kafka.network.Request;
import kafka.utils.Utils;

import java.nio.ByteBuffer;
import java.util.function.Function;

public class ProducerRequest extends Request {
    public static int RandomPartition = -1;
    
    public String topic;
    public int partition;
    public  ByteBufferMessageSet messages;
    
    public ProducerRequest(String topic, int partition, ByteBufferMessageSet messages) {
        this.topic = topic;
        this.partition = partition;
        this.messages = messages;
    }
    
    
    public static ProducerRequest readFrom(ByteBuffer buffer) {
        String topic = Utils.readShortString(buffer, "UTF-8");
        int partition = buffer.getInt();
        int messageSetSize = buffer.getInt();
        ByteBuffer messageSetBuffer = buffer.slice();
        messageSetBuffer.limit(messageSetSize);
        buffer.position(buffer.position() + messageSetSize);
        try {
            return new ProducerRequest(topic, partition, new ByteBufferMessageSet(messageSetBuffer));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return null;
    }
    
    public void writeTo(ByteBuffer buffer) {
        
        Utils.writeShortString(buffer, topic, "UTF-8");
        buffer.putInt(partition);
        buffer.putInt(messages.serialized().limit());
        buffer.put(messages.serialized());
        messages.serialized().rewind();
    }
    
    public int sizeInBytes() {
        return 2 + topic.length() + 4 + 4 + Integer.valueOf(messages.sizeInBytes() + "");
    }
    
    
    public int getTranslatedPartition(int randomSelector) {
        if (partition == ProducerRequest.RandomPartition) {
            return randomSelector;
        } else {
            return partition;
        }
    }
    
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ProducerRequest(");
        builder.append(topic + ",");
        builder.append(partition + ",");
        builder.append(messages.sizeInBytes());
        builder.append(")");
        builder.toString();
        return builder.toString();
    }
    
    public boolean equals(Object other) {
        if (other instanceof ProducerRequest) {
            ProducerRequest that = (ProducerRequest) other;
            return (that.equals(this) && topic == that.topic && partition == that.partition && messages.equals(that.messages));
        } else {
            return false;
        }
    }
    
    public Boolean  canEqual(Object other ){
       return other instanceof  ProducerRequest ;
    }
    
    public   int hashCode() {
        return  31+(17*partition)+topic.hashCode() +messages.hashCode();
    }
    
}
