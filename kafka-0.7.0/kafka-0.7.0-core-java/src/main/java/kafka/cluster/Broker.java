package kafka.cluster;

import kafka.utils.Utils;

public class Broker {
    
    public int id;
    private String creatorId;
    public String host;
    public int port;
    
    public Broker(int id, String creatorId, String host, int port) {
        this.id = id;
        this.creatorId = creatorId;
        this.host = host;
        this.port = port;
    }
    
    public static Broker createBroker(int id, String brokerInfoString) {
        String brokerInfo[] = brokerInfoString.split(":");
        return new Broker(id, brokerInfo[0], brokerInfo[1], Integer.parseInt(brokerInfo[2]));
    }
    
    @Override
    public String toString() {
        return new String("id:" + id + ",creatorId:" + creatorId + ",host:" + host + ",port:" + port);
    }
    
    public String getZKString() {
        return new String(creatorId + ":" + host + ":" + port);
    }
    
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else if (obj instanceof Broker) {
            Broker n = (Broker) obj;
            return id == n.id && host == n.host && port == n.port;
        } else {
            return false;
        }
    }
    
    public int hashCode() {
        return Utils.hashcode(id, host, port);
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getCreatorId() {
        return creatorId;
    }
    
    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
}
