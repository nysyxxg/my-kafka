package chapter9;

import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

/**
 *
 * 自定义处理器，实现processor接口
 * 1. 在init方法中做初始化
 * 2. process中接收到key / value pair，对value做处理，最后可以在里面做forward。
 * 3. punctuate
 */
public class MyProcessorA implements Processor<String, String> {

    private ProcessorContext context;

    @Override
    public void init(ProcessorContext processorContext) {
        this.context = processorContext;
      
//        this.context.schedule(1000);
    }

    /**
     * @param key 消息的key
     * @param value 消息的value
     */
    @Override
    public void process(String key, String value) {
        String line = value + "MyProcessor A  ----   ";
        // 将处理完成的数据转发到downstream processor，比如当前是processor1处理器，通过forward流向到processor2处理器
        context.forward(key, line);
    }

    
    public void punctuate(long timestamp) {
    }

    @Override
    public void close() {

    }
}