package kafka.consumer;

import kafka.message.ByteBufferMessageSet;

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
    
    
}
