package kafka.producer;

import kafka.utils.Utils;

import java.util.Properties;

public class SyncProducerConfig  extends SyncProducerConfigShared{
    Properties props;
    String host;
    int port;
    
    public SyncProducerConfig(Properties props) {
        super(props);
        this.props = props;
        this.host = Utils.getString(props, "host");
        this.port = Utils.getInt(props, "port");
    }
}


