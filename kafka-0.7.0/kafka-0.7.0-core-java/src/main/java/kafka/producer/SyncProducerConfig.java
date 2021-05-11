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
        host = Utils.getString(props, "host");
        port = Utils.getInt(props, "port");
    }
}


