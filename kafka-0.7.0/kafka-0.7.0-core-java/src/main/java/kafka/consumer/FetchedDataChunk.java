package kafka.consumer;

import kafka.message.ByteBufferMessageSet;

import java.util.Objects;

public class FetchedDataChunk {
    ByteBufferMessageSet messages;
    PartitionTopicInfo topicInfo;
    Long fetchOffset;
    
    public FetchedDataChunk(ByteBufferMessageSet messages,
                            PartitionTopicInfo topicInfo,
                            Long fetchOffset) {
        this.messages = messages;
        this.topicInfo = topicInfo;
        this.fetchOffset = fetchOffset;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FetchedDataChunk)) return false;
        FetchedDataChunk that = (FetchedDataChunk) o;
        return Objects.equals(messages, that.messages) &&
                Objects.equals(topicInfo, that.topicInfo) &&
                Objects.equals(fetchOffset, that.fetchOffset);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(messages, topicInfo, fetchOffset);
    }
}
