package kafka.network;

public interface ConnectionConfig {
    public  String host = null;
    public int port = 0;
    public int sendBufferSize = -1;
    public int receiveBufferSize = -1;
    public boolean tcpNoDelay = true;
    public boolean keepAlive = false;
    
}
