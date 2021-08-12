package kafka.producer.async;

import kafka.api.ProducerRequest;
import kafka.message.ByteBufferMessageSet;
import kafka.message.CompressionCodec;
import kafka.message.Message;
import kafka.message.NoCompressionCodec;
import kafka.producer.ProducerConfig;
import kafka.producer.QueueItem;
import kafka.producer.SyncProducer;
import kafka.serializer.Encoder;
import org.apache.log4j.Logger;
import scala.Tuple2;

import java.util.*;
import java.util.stream.Collectors;

public class DefaultEventHandler<T> implements EventHandler<T> {
    private Logger logger = Logger.getLogger(DefaultEventHandler.class);
    
    private ProducerConfig config;
    private kafka.producer.async.CallbackHandler<T> cbkHandler = null;
    
    public DefaultEventHandler(ProducerConfig config, kafka.producer.async.CallbackHandler<T> cbkHandler) {
        this.config = config;
        this.cbkHandler = cbkHandler;
    }
    
    @Override
    public void init(Properties props) {
    }
    
    @Override
    public void close() {
    }
    
    @Override
    public void handle(List<QueueItem<T>> events, SyncProducer syncProducer, Encoder<T> serializer) {
        System.out.println("-------------DefaultEventHandler-------------------------handle--------------------------");
        List<QueueItem<T>> processedEvents = events;
        if (cbkHandler != null) {
            processedEvents = cbkHandler.beforeSendingData(events);
        }
        if (logger.isTraceEnabled()) {
            processedEvents.stream().forEach(event -> logger.trace("Handling event for Topic: %s, Partition: %d"
                    .format(event.getTopic(), event.getPartition())));
        }
        Map<Tuple2<String, Integer>, ByteBufferMessageSet> serializeData = serialize(collate(processedEvents), serializer); // 序列化数据，转化为ByteBufferMessageSet
        send(serializeData, syncProducer);// 发送数据
    }
    
    private void send(Map<Tuple2<String, Integer>, ByteBufferMessageSet> messagesPerTopic, SyncProducer syncProducer) {
        System.out.println("-------------DefaultEventHandler-------------------------send--------------------------");
        if (messagesPerTopic.size() > 0) {
            ProducerRequest requests[] = new ProducerRequest[messagesPerTopic.size()];
            List<ProducerRequest> list = messagesPerTopic.entrySet().stream().map(f -> {
                Tuple2<String, Integer> key = f.getKey();
                ByteBufferMessageSet value = f.getValue();
                return new ProducerRequest(key._1, key._2, value);
            }).collect(Collectors.toList());  // 将发送的消息，转化为ProducerRequest发送请求
            list.toArray(requests);
            syncProducer.multiSend(requests); // 批量发送
            if (logger.isTraceEnabled()) {
                logger.trace("kafka producer sent messages for topics " + messagesPerTopic);
            }
        }
    }
    
    private Map<Tuple2<String, Integer>, ByteBufferMessageSet> serialize(Map<Tuple2<String, Integer>, List<T>> eventsPerTopic,
                                                                         Encoder<T> serializer) {
        
        Map<Tuple2<String, Integer>, ByteBufferMessageSet> messagesPerTopicPartition = new HashMap<>();
        
        Map<Tuple2<String, Integer>, List<Message>> eventsPerTopicMap = new HashMap<>();
        // 转化为 Message
        // val eventsPerTopicMap = eventsPerTopic.map(e = > ((e._1._1, e._1._2),e._2.map(l = > serializer.toMessage(l))))
        for (Tuple2<String, Integer> tuple2 : eventsPerTopic.keySet()) {
            String t1 = tuple2._1;
            Integer t2 = tuple2._2;
            Tuple2<String, Integer> key = new Tuple2<String, Integer>(t1, t2);
            List<T> values = eventsPerTopic.get(tuple2);
            List<Message> messageSet = values.stream().map(l -> serializer.toMessage(l)).collect(Collectors.toList());// 转化为 Message
            eventsPerTopicMap.put(key, messageSet);
        }
        
        eventsPerTopicMap.entrySet().stream().forEach(topicAndEvents -> {
            Tuple2<String, Integer> key = topicAndEvents.getKey();
            List<Message> messageList = topicAndEvents.getValue();
            CompressionCodec compressionCodec = config.compressionCodec;
            if (compressionCodec instanceof NoCompressionCodec) { // 如果没有压缩
                if (logger.isTraceEnabled()) {
                    logger.trace("Sending %d messages with no compression to topic %s on partition %d"
                            .format(String.valueOf(messageList.size()), key._1, key._2));
                }
                try {
                    ByteBufferMessageSet byteBufferMessageSet = new ByteBufferMessageSet(compressionCodec, messageList); // 将Message 封装为  ByteBufferMessageSet
                    messagesPerTopicPartition.put(key, byteBufferMessageSet);
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            } else {
                int size = config.compressedTopics.size();
                if (size == 0) {
                    if (logger.isTraceEnabled())
                        logger.trace("Sending %d messages with compression codec %d to topic %s on partition %d"
                                .format(String.valueOf(messageList.size()), config.compressionCodec.codec, key._1, key._2));
                    try {
                        ByteBufferMessageSet byteBufferMessageSet = new ByteBufferMessageSet(config.compressionCodec, messageList);
                        messagesPerTopicPartition.put(key, byteBufferMessageSet);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                } else {
                    if (config.compressedTopics.contains(key._1)) {
                        if (logger.isTraceEnabled())
                            logger.trace("Sending %d messages with compression codec %d to topic %s on partition %d"
                                    .format(String.valueOf(messageList.size()), config.compressionCodec.codec, key._1, key._2));
                        try {
                            ByteBufferMessageSet byteBufferMessageSet = new ByteBufferMessageSet(config.compressionCodec, messageList);
                            messagesPerTopicPartition.put(key, byteBufferMessageSet);
                        } catch (Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    } else {
                        if (logger.isTraceEnabled())
                            logger.trace("Sending %d messages to topic %s and partition %d with no compression as %s is not in compressed.topics - %s"
                                    .format(String.valueOf(messageList.size()), key._1, key._2, key._1, config.compressedTopics.toString()));
                        try {
                            ByteBufferMessageSet byteBufferMessageSet = new ByteBufferMessageSet(compressionCodec, messageList);
                            messagesPerTopicPartition.put(key, byteBufferMessageSet);
                        } catch (Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    }
                }
            }
        });
        
        return messagesPerTopicPartition;
    }
    
    //  对每一条event对象进行转换
    private Map<Tuple2<String, Integer>, List<T>> collate(List<QueueItem<T>> events) {
        Map<Tuple2<String, Integer>, List<T>> collatedEvents = new HashMap<Tuple2<String, Integer>, List<T>>(); // [(topic,ppartition),数据集合]
        List<String> distinctTopics = events.stream().map(e -> e.getTopic()).distinct().collect(Collectors.toList());// 获取event中所有的topic
        List<Integer> distinctPartitions = events.stream().map(e -> e.getPartition()).distinct().collect(Collectors.toList()); // 获取event中所有的partition
        
        List<QueueItem<T>> remainingEvents = events;
        distinctTopics.stream().forEach(topic -> { // 开始遍历所有的topic
            // 对所有的envents 进行分区
            List<QueueItem<T>> topicEvents = remainingEvents.stream().filter(e -> e.getTopic().equals(topic)).collect(Collectors.toList());
            // 不满足条件的数据
            List<QueueItem<T>> remainingEvents2 = remainingEvents.stream().filter(e -> !e.getTopic().equals(topic)).collect(Collectors.toList());
            
            distinctPartitions.stream().forEach(p -> { // 遍历event中所有的分区
                List<QueueItem<T>> topicPartitionEvents = topicEvents.stream().filter(e -> e.getPartition() == p).collect(Collectors.toList()); // 得到相同topic的相同分区的events对象集合
                if (topicPartitionEvents.size() > 0) { // 如果集合对象大于0
                    Tuple2<String, Integer> tuple2 = new Tuple2<String, Integer>(topic, p);
                    List<T> data = topicPartitionEvents.stream().map(q -> q.getData()).collect(Collectors.toList());
                    collatedEvents.put(tuple2, data); //相同topic的相同分区的--->events对象集合
                }
            });
        });
        return collatedEvents;
    }
    
}
