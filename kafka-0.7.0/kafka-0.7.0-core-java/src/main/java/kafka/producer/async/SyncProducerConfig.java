package kafka.producer.async;

import kafka.utils.Utils;

import java.util.Properties;

public class SyncProducerConfig extends SyncProducerConfigShared {
    Properties props;
    String host;
    int port;
    
    SyncProducerConfig(Properties props) {
        super(props);
        this.props = props;
        host = Utils.getString(props, "host");
        port = Utils.getInt(props, "port");
    }
}
abstract class SyncProducerConfigShared {
    Properties props;
    int bufferSize;
    int connectTimeoutMs;
    int socketTimeoutMs;
    int reconnectInterval;
    int maxMessageSize;
    
    SyncProducerConfigShared(Properties props) {
        this.props = props;
        
        bufferSize = Utils.getInt(props, "buffer.size", 100 * 1024);
        
        connectTimeoutMs = Utils.getInt(props, "connect.timeout.ms", 5000);
        
        socketTimeoutMs = Utils.getInt(props, "socket.timeout.ms", 30000);
        
        reconnectInterval = Utils.getInt(props, "reconnect.interval", 30000);
        
        maxMessageSize = Utils.getInt(props, "max.message.size", 1000000);
    }
}

