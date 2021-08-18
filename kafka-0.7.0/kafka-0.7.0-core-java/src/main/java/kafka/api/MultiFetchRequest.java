package kafka.api;

import kafka.network.Request;

import java.nio.ByteBuffer;

public class MultiFetchRequest extends Request {
    
    public FetchRequest fetches[];
    
    public MultiFetchRequest(FetchRequest fetches[]) {
        super(RequestKeys.MultiFetch);
        this.fetches = fetches;
    }
    
    public static MultiFetchRequest readFrom(ByteBuffer buffer) {
        short count = buffer.getShort();
        FetchRequest fetches[] = new FetchRequest[count];
        for (int i = 0; i < fetches.length; i++) {
            fetches[i] = FetchRequest.readFrom(buffer);
        }
        return new MultiFetchRequest(fetches);
    }
    
    
    @Override
    public int sizeInBytes() {
        int size = 2;
        for (FetchRequest fetch : fetches) {
            size += fetch.sizeInBytes();
        }
        return size;
    }
    
    @Override
    public void writeTo(ByteBuffer buffer) {
        if (fetches.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Number of requests in MultiFetchRequest exceeds " + Short.MAX_VALUE + ".");
        }
        buffer.putShort((short) fetches.length);
        for (FetchRequest fetch : fetches) {
            fetch.writeTo(buffer);
        }
    }
    
    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        for (FetchRequest fetch : fetches) {
            buffer.append(fetch.toString());
            buffer.append(",");
        }
        return buffer.toString();
    }
    
    
}
