package kafka.javaapi.producer;

import kafka.javaapi.Implicits;
import kafka.javaapi.message.ByteBufferMessageSet;
import kafka.producer.SyncProducerConfig;

public class SyncProducer {
    
    private kafka.producer.SyncProducer syncProducer;
    public kafka.producer.SyncProducer underlying;
    
    public SyncProducer(kafka.producer.SyncProducer producer) {
        this.syncProducer = producer;
        this.underlying = producer;
    }
    
    
    public SyncProducer(SyncProducerConfig config) {
        this(new kafka.producer.SyncProducer(config));
    }
    
    
    public void send(String topic  , int partition, ByteBufferMessageSet messages  ) {
        // import kafka.javaapi.Implicits._
        kafka.message.ByteBufferMessageSet  messagesNew = Implicits.javaMessageSetToScalaMessageSet(messages);  // 需要进行转换
        underlying.send(topic, partition, messagesNew);
    }
    
    public void  send(String topic  , ByteBufferMessageSet messages  ) {
        send(topic, kafka.api.ProducerRequest.RandomPartition, messages);
    }
    
    public void  multiSend(kafka.javaapi.ProducerRequest produces[]) {
      //  import kafka.javaapi.Implicits._
        kafka.api.ProducerRequest  produceRequests[] = new kafka.api.ProducerRequest[produces.length];
        for(int i = 0 ; i< produces.length; i++){
            kafka.message.ByteBufferMessageSet  messages = Implicits.javaMessageSetToScalaMessageSet(produces[i].messages);  // 需要进行转换
            produceRequests[i] = new kafka.api.ProducerRequest(produces[i].topic, produces[i].partition,messages);
        }
        underlying.multiSend(produceRequests);
    }
    
    public void close() {
        underlying.close();
    }
    
}
