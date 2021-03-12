package chapter9;

import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

public class MyProcessorB  implements Processor<String, String> {
    @Override
    public void init(ProcessorContext processorContext) {
    
    }
    
    @Override
    public void process(String s, String s2) {
    
    }
    
    @Override
    public void close() {
    
    }
}
