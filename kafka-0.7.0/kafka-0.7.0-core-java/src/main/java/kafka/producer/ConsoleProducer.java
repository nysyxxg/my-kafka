package kafka.producer;

import clover.org.apache.commons.codec.StringEncoder;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import kafka.message.DefaultCompressionCodec;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class ConsoleProducer {
    
    private Logger logger = Logger.getLogger(ConsoleProducer.class);
    
    public void main(String[] args) throws Throwable {
        OptionParser parser = new OptionParser();
        ArgumentAcceptingOptionSpec<String> topicOpt = parser.accepts("topic", "REQUIRED: The topic id to produce messages to.")
                .withRequiredArg()
                .describedAs("topic")
                .ofType(String.class);
        ArgumentAcceptingOptionSpec<String> zkConnectOpt = parser.accepts("zookeeper", "REQUIRED: The zookeeper connection string for the kafka zookeeper instance in the form HOST:PORT[/CHROOT].")
                .withRequiredArg()
                .describedAs("connection_string")
                .ofType(String.class);
        OptionSpecBuilder asyncOpt = parser.accepts("sync", "If set message send requests to the brokers are synchronously, one at a time as they arrive.");
        
        OptionSpecBuilder compressOpt = parser.accepts("compress", "If set, messages batches are sent compressed");
        ArgumentAcceptingOptionSpec<Integer> batchSizeOpt = parser.accepts("batch-size", "Number of messages to send in a single batch if they are not being sent synchronously.")
                .withRequiredArg()
                .describedAs("size")
                .ofType(Integer.class)
                .defaultsTo(200);
        ArgumentAcceptingOptionSpec<Long> sendTimeoutOpt = parser.accepts("timeout", "If set and the producer is running in asynchronous mode, this gives the maximum amount of time" +
                " a message will queue awaiting suffient batch size. The value is given in ms.")
                .withRequiredArg()
                .describedAs("timeout_ms")
                .ofType(Long.class)
                .defaultsTo(1000L);
        ArgumentAcceptingOptionSpec<String> messageEncoderOpt = parser.accepts("message-encoder", "The class name of the message encoder implementation to use.")
                .withRequiredArg()
                .describedAs("encoder_class")
                .ofType(String.class)
                .defaultsTo(StringEncoder.class.getName());
        ArgumentAcceptingOptionSpec<String> messageReaderOpt = parser.accepts("line-reader", "The class name of the class to use for reading lines from standard in. " +
                "By default each line is read as a seperate message.")
                .withRequiredArg()
                .describedAs("reader_class")
                .ofType(String.class)
                .defaultsTo(LineMessageReader.class.getName());
        ArgumentAcceptingOptionSpec<String> propertyOpt = parser.accepts("property", "A mechanism to pass user-defined properties in the form key=value to the message reader. " +
                "This allows custom configuration for a user-defined message reader.")
                .withRequiredArg()
                .describedAs("prop")
                .ofType(String.class);
        
        OptionSet options = parser.parse(args);
        ArgumentAcceptingOptionSpec obj[] = {topicOpt, zkConnectOpt};
        for (ArgumentAcceptingOptionSpec arg : obj) {
            if (!options.has(arg)) {
                System.err.println("Missing required argument \"" + arg + "\"");
                try {
                    parser.printHelpOn(System.err);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.exit(1);
            }
        }
        
        String topic = options.valueOf(topicOpt);
        String zkConnect = options.valueOf(zkConnectOpt);
        boolean async = options.has(asyncOpt);
        boolean compress = options.has(compressOpt);
        Integer batchSize = options.valueOf(batchSizeOpt);
        Long sendTimeout = options.valueOf(sendTimeoutOpt);
        String encoderClass = options.valueOf(messageEncoderOpt);
        String readerClass = options.valueOf(messageReaderOpt);
        Properties cmdLineProps = parseLineReaderArgs(options.valuesOf(propertyOpt));
        
        Properties props = new Properties();
        props.put("zk.connect", zkConnect);
        props.put("compression.codec", DefaultCompressionCodec.codec);
        String producerType;
        if (async) {
            producerType = "async";
        } else {
            producerType = "sync";
        }
        ;
        props.put("producer.type", producerType);
        if (options.has(batchSizeOpt)) {
            props.put("batch.size", batchSize);
        }
        props.put("queue.enqueueTimeout.ms", sendTimeout);
        props.put("serializer.class", encoderClass);
        
        MessageReader reader = null;
        try {
            reader = (MessageReader) Class.forName(readerClass).newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        reader.init(System.in, cmdLineProps);
        
        Producer<Object, Object> producer = new Producer<Object, Object>(new ProducerConfig(props));
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                producer.close();
            }
        });
        
        Object message = null;
        do {
            message = reader.readMessage();
            if (message != null)
                producer.send(new ProducerData(topic, message));
        } while (message != null);
    }
    
    public Properties parseLineReaderArgs(Iterable<String> args) {
        List<String[]> splits = new ArrayList<>();
        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String valu = iterator.next();
            if (valu != null) {
                String array[] = valu.split("=");
                if (array.length > 0) {
                    splits.add(array);
                }
            }
        }
        for (String[] val : splits) {
            if (val.length != 2) {
                System.err.println("Invalid line reader properties: " + args.toString());
                System.exit(1);
            }
        }
        Properties props = new Properties();
        for (String a[] : splits) {
            props.put(a[0], a[1]);
        }
        return props;
    }
    
    
    interface MessageReader {
        void init(InputStream inputStream, Properties props);
        
        Object readMessage() throws IOException;
        
        void close();
    }
    
    class LineMessageReader implements MessageReader {
        BufferedReader reader = null;
        
        public void init(InputStream inputStream, Properties props) {
            reader = new BufferedReader(new InputStreamReader(inputStream));
        }
        
        public Object readMessage() throws IOException {
            return reader.readLine();
        }
        
        @Override
        public void close() {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
}
