package kafka.producer;

import kafka.utils.Utils;

import java.util.Properties;

public abstract class SyncProducerConfigShared {
    Properties props;
    int bufferSize;
    int connectTimeoutMs;
    int socketTimeoutMs;
    int reconnectInterval;
    int maxMessageSize;
    
    public SyncProducerConfigShared() {
    }
    
    public SyncProducerConfigShared(Properties props) {
        this.props = props;
        this.bufferSize = Utils.getInt(props, "buffer.size", 100 * 1024);
        this.connectTimeoutMs = Utils.getInt(props, "connect.timeout.ms", 5000);
        this.socketTimeoutMs = Utils.getInt(props, "socket.timeout.ms", 30000);
        this.reconnectInterval = Utils.getInt(props, "reconnect.interval", 30000);
        this.maxMessageSize = Utils.getInt(props, "max.message.size", 1000000);
    }
    
    
}