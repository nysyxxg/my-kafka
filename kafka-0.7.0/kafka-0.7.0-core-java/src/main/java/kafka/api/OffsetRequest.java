package kafka.api;

import kafka.network.Request;
import kafka.utils.Utils;

import java.nio.ByteBuffer;

public class OffsetRequest extends Request {
    
    public static String SmallestTimeString = "smallest";
    public static String LargestTimeString = "largest";
    public static Long LatestTime = -1L;  // 从最新的offset开始消息
    public static Long EarliestTime = -2L;// 从最早的offset开始消息
    
    public String topic;
    public int partition;
    public long offset;
    public int maxNumOffsets;
    
    public OffsetRequest(String topic, int partition, long offset, int maxNumOffsets) {
        super(RequestKeys.Offsets);
        this.partition = partition;
        this.topic = topic;
        this.offset = offset;
        this.maxNumOffsets = maxNumOffsets;
    }
    
    public static OffsetRequest readFrom(ByteBuffer buffer) {
        String topic = Utils.readShortString(buffer, "UTF-8");
        int partition = buffer.getInt();
        long offset = buffer.getLong();
        int maxNumOffsets = buffer.getInt();
        return new OffsetRequest(topic, partition, offset, maxNumOffsets);
    }
    
    static ByteBuffer serializeOffsetArray(Long offsets[]) {
        int size = 4 + 8 * offsets.length;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putInt(offsets.length);
        for (int i = 0; i < offsets.length; i++) {
            buffer.putLong(offsets[i]);
        }
        buffer.rewind();
        System.out.println(OffsetRequest.class.getName() + "-----------serializeOffsetArray------------buffer:" + buffer);
        return buffer;
    }
    
    public static Long[] deserializeOffsetArray(ByteBuffer buffer) {
        System.out.println(OffsetRequest.class.getName() + "-----------deserializeOffsetArray------------buffer:" + buffer);
        int size = buffer.getInt();
        Long offsets[] = new Long[size];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = buffer.getLong();
        }
        return offsets;
    }
    
    
    public void writeTo(ByteBuffer buffer) {
        Utils.writeShortString(buffer, topic, "UTF-8");
        buffer.putInt(partition);
        buffer.putLong(offset);
        buffer.putInt(maxNumOffsets);
    }
    
    public int sizeInBytes() {
        return 2 + topic.length() + 4 + 8 + 4;
    }
    
    public String toString() {
        return "OffsetRequest(topic:" + topic + ", part:" + partition + ", time:" + offset +
                ", maxNumOffsets:" + maxNumOffsets + ")";
    }
    
}
