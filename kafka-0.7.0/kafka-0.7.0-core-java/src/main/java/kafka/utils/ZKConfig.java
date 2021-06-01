package kafka.utils;

import kafka.producer.async.AsyncProducerConfigShared;

import java.util.Properties;

public class ZKConfig extends AsyncProducerConfigShared {
    
    private Properties props;
    public String zkConnect;
    public int zkSessionTimeoutMs;
    public int zkConnectionTimeoutMs;
    public int zkSyncTimeMs;
    
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
    
    public Properties getProps() {
        return props;
    }
    
    public void setProps(Properties props) {
        this.props = props;
    }
    
    public String getZkConnect() {
        return zkConnect;
    }
    
    public void setZkConnect(String zkConnect) {
        this.zkConnect = zkConnect;
    }
    
    public int getZkSessionTimeoutMs() {
        return zkSessionTimeoutMs;
    }
    
    public void setZkSessionTimeoutMs(int zkSessionTimeoutMs) {
        this.zkSessionTimeoutMs = zkSessionTimeoutMs;
    }
    
    public int getZkConnectionTimeoutMs() {
        return zkConnectionTimeoutMs;
    }
    
    public void setZkConnectionTimeoutMs(int zkConnectionTimeoutMs) {
        this.zkConnectionTimeoutMs = zkConnectionTimeoutMs;
    }
    
    public int getZkSyncTimeMs() {
        return zkSyncTimeMs;
    }
    
    public void setZkSyncTimeMs(int zkSyncTimeMs) {
        this.zkSyncTimeMs = zkSyncTimeMs;
    }
}
