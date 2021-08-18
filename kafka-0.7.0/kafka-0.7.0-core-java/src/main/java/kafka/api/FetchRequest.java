package kafka.api;

import kafka.network.Request;
import kafka.utils.Utils;

import java.nio.ByteBuffer;

public class FetchRequest extends Request {
    public String topic;
    public int partition;
    public long offset;
    public int maxSize;
    
    public FetchRequest(String topic, int partition, long offset, int size) {
        super(RequestKeys.Fetch);
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.maxSize = size;
    }
    
    public  static FetchRequest readFrom(ByteBuffer buffer) {
        String topic = Utils.readShortString(buffer, "UTF-8");
        int partition = buffer.getInt();
        long offset = buffer.getLong();
        int size = buffer.getInt();
        return new FetchRequest(topic, partition, offset, size);
    }
    
    
    @Override
    public int sizeInBytes() {
        return 2 + topic.length() + 4 + 8 + 4;
    }
    
    @Override
    public void writeTo(ByteBuffer buffer) {
        Utils.writeShortString(buffer, topic, "UTF-8");
        buffer.putInt(partition);
        buffer.putLong(offset);
        buffer.putInt(maxSize);
    }
    
    @Override
    public String toString() {
        return "FetchRequest(topic:" + topic + ", part:" + partition + " offset:" + offset + " maxSize:" + maxSize + ")";
    }
    
}
