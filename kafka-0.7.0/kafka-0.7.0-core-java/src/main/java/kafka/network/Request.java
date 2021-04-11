package kafka.network;

import java.nio.ByteBuffer;

public abstract class Request {
    public Short id;
    
    public Request(Short id) {
        this.id = id;
    }
    
    public Request() {
    }
    
    public abstract int sizeInBytes();
    
    public abstract  void writeTo(ByteBuffer buffer) ;
    
}
