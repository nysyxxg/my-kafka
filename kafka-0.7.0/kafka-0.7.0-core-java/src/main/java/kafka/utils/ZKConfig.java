package kafka.utils;

import kafka.producer.async.AsyncProducerConfigShared;

import java.util.Properties;

public class ZKConfig  extends AsyncProducerConfigShared {
    
    private Properties props;
    public static String zkConnect;
    public static int zkSessionTimeoutMs;
    public static int zkConnectionTimeoutMs;
    public static  int zkSyncTimeMs;
    
    public ZKConfig(Properties props) {
        super();
        this.props = props;
        
        /** ZK host string */
        this.zkConnect = Utils.getString(props, "zk.connect", null);
        
        /** zookeeper session timeout */
        this.zkSessionTimeoutMs = Utils.getInt(props, "zk.sessiontimeout.ms", 6000);
        
        /** the max time that the client waits to establish a connection to zookeeper */
        this.zkConnectionTimeoutMs = Utils.getInt(props, "zk.connectiontimeout.ms", zkSessionTimeoutMs);
        
        /** how far a ZK follower can be behind a ZK leader */
        this.zkSyncTimeMs = Utils.getInt(props, "zk.synctime.ms", 2000);
    }
    
}
