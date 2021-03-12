package chapter9;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.TopologyBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class StreamDemo {
    
    public static Map<String, Object> connection() {
        Map<String, Object> properties = new HashMap<>();
        // 指定一个应用ID，会在指定的目录下创建文件夹，里面存放.lock文件
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "my-stream-processing-application");
        // 指定kafka集群
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "server01:9092");
        // 指定一个路径创建改应用ID所属的文件
        properties.put(StreamsConfig.STATE_DIR_CONFIG, "E:\\kafka-stream");
        // key 序列化 / 反序列化
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        // value 序列化 / 反序列化
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        return properties;
    }
    
    public static void main(String[] args) throws IOException {
        // 创建一个StreamsConfig对象
        StreamsConfig config = new StreamsConfig(StreamDemo.connection());
        // KStreamBuilder builder = new KStreamBuilder();
        // 创建一个TopologyBuilder对象
        Topology builder = new Topology();
        // 添加一个SOURCE，接收两个参数，param1 定义一个名称，param2 从哪一个topic读取消息
        builder.addSource("SOURCE", "topic-input")
                // 添加第一个PROCESSOR，param1 定义一个processor名称，param2 processor实现类，param3 指定一个父名称
                .addProcessor("PROCESS1", MyProcessorA::new, "SOURCE")
                // 添加第二个PROCESSOR，param1 定义一个processor名称， param2 processor实现类，param3 指定一个父名称
                .addProcessor("PROCESS2", MyProcessorB::new, "PROCESS1")
                // 添加第三个PROCESSOR，param1 定义一个processor名称， param2 processor实现类，param3 指定一个父名称
                .addProcessor("PROCESS3", MyProcessorC::new, "PROCESS2")
                
                // 最后添加SINK位置，param1 定义一个sink名称，param2 指定一个输出TOPIC，param3 指定接收哪一个PROCESSOR的数据
                .addSink("SINK1", "topicA", "PROCESS1")
                .addSink("SINK2", "topicB", "PROCESS2")
                .addSink("SINK3", "topicC", "PROCESS3");
        
        // 创建一个KafkaStreams对象，传入TopologyBuilder和StreamsConfig
        KafkaStreams kafkaStreams = new KafkaStreams(builder, config);
        // 启动kafkaStreams
        kafkaStreams.start();
    }
}