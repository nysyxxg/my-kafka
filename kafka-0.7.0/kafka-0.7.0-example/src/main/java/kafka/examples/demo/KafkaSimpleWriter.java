package kafka.examples.demo;
 
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
 
import kafka.javaapi.producer.ProducerData;
import kafka.producer.ProducerConfig;
import kafka.javaapi.producer.Producer;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
import java.util.Properties; 
 
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
 
 
/**
 * Flume sink that propogates data to Kafka cluster
 * 
 */
public class KafkaSimpleWriter {
 
    private static final Logger Logger = LoggerFactory.getLogger(KafkaSimpleWriter.class);
 
//    private static final String KAFKA_TOPIC = "kafka.topic";
//    private static final String KAFKA_BROKERS = "kafka.brokers";
//    private static final String KAFKA_BUNDLE_SIZE = "kafka.bundle.size";
//    private static final String KAFKA_BUNDLE_INTERVAL_MS = "kafka.bundle.interval.ms";
//    private static final String KAFKA_MESSAGE_SIZE = "kafka.message.size";
//    private static final String KAFKA_HEARTBEAT_MS = "kafka.heartbeat.ms";
 
    private String topic;
    private String zkString;
    private Producer<String, String> producer;
    private ProducerConfig config;
 
    private long totalCnt = 0;
    
    
    public KafkaSimpleWriter(String file_path, String topic, String zkString) throws IOException {
    	this.topic = topic;
    	this.zkString = zkString;
    	Properties properties = new Properties();
    	if(file_path != null && file_path.isEmpty()== false ){
    		properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream(file_path));
    	}
    	
    	SetDefaultProperty(properties, "serializer.class", "kafka.serializer.StringEncoder");
    	SetDefaultProperty(properties, "zk.connect", this.zkString);
    	SetDefaultProperty(properties, "zk.connectiontimeout.ms", "65000");
    	SetDefaultProperty(properties, "producer.type", "async");
    	SetDefaultProperty(properties, "queue.time", "10000");
    	SetDefaultProperty(properties, "queue.size", "10000");
        
        this.config = new ProducerConfig(properties);
 
        this.producer = new Producer<String, String>(this.config);
    }
 
	private void SetDefaultProperty(Properties prop, String key, String defaultValue){
		if(prop.getProperty(key) == null){
			prop.setProperty(key, defaultValue);
		}
	}
	
	public void sendMessage(String message){
		this.producer.send(new ProducerData<String, String>(topic, message));
		totalCnt++;
		if(totalCnt%1000000 == 0){
			Logger.info("Total send messages:{} to topic:{}", totalCnt, this.topic);
		}
	}
	
	public long getCount(){ return this.totalCnt; }
	
	
	static public void main(String[] args) throws IOException, ParseException{
		String topic = null;
		String zkString = null;
		String config_path = null;
		String data_path = null;
		
		Options opts = new Options();
        opts.addOption("h", false, "help message");
        opts.addOption("data", true, "Data file path");
        opts.addOption("config", true, "config file path");
        opts.addOption("topic", true, "kafka topic");
        opts.addOption("zookeeper", true, "zookeeper list");
        
        BasicParser parser = new BasicParser();
        CommandLine cl = parser.parse(opts, args);
        
        if (cl.hasOption('h') || cl.hasOption("topic") == false 
        		|| cl.hasOption("zookeeper") == false ) {
            HelpFormatter hf = new HelpFormatter();
            hf.printHelp("OptionsTip", opts);
            return ;
        } else {
        	topic = cl.getOptionValue("topic");
            zkString = cl.getOptionValue("zookeeper");
            config_path = cl.getOptionValue("config", null);
            data_path = cl.getOptionValue("data", null);
        }
        Logger.info("topic={}, zookeeper={}, config={}, data={}", topic, zkString, config_path, data_path);
        
        
		KafkaSimpleWriter writer = new KafkaSimpleWriter(config_path, topic, zkString);
		
		if(data_path == null || data_path.isEmpty()){
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	        String s = null;
	        try {
	            while((s = in.readLine()) != null){
	                writer.sendMessage(s);
	            }
	            
	        } catch(IOException e) {
	            e.printStackTrace();
	        } finally {
	        	in.close();
	        }
		}else{
			BufferedReader in = new BufferedReader(new FileReader(data_path));
			try{
					String s = null;
					while((s = in.readLine()) != null){
						writer.sendMessage(s);
					}
			}catch(IOException e){
				e.printStackTrace();
			}finally{
				in.close();
			}
		}
 
		System.out.println("Successfully write records:" + writer.getCount());
	}
   
}