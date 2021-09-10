package kafka.consumer;

import kafka.serializer.Decoder;
import org.apache.log4j.Logger;

import java.util.concurrent.BlockingQueue;

public class KafkaMessageStream<T> implements Iterable<T> {
    private Logger logger = Logger.getLogger(KafkaMessageStream.class);
    
    public String topic;
    public BlockingQueue<FetchedDataChunk> queue;
    public int consumerTimeoutMs;
    public Decoder<T> decoder;
    
    private ConsumerIterator<T> iter;
    
    public ConsumerIterator<T> iterator() {
        return iter;
    }
    
    public KafkaMessageStream(String topic, BlockingQueue<FetchedDataChunk> chunkQueue,
                              int consumerTimeoutMs, Decoder<T> decoder) {
        this.topic = topic;
        this.queue = chunkQueue;
        this.consumerTimeoutMs = consumerTimeoutMs;
        this.decoder = decoder;
        this.iter = new ConsumerIterator<T>(topic, chunkQueue, consumerTimeoutMs, decoder);
    }
    
    
}
