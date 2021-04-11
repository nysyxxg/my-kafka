package kafka.api;

import kafka.network.Request;

import java.nio.ByteBuffer;

public class MultiProducerRequest extends Request {
    
    public  ProducerRequest[] produces;
    
    public MultiProducerRequest() {
        super(RequestKeys.MultiProduce);
    }
    
    public MultiProducerRequest(ProducerRequest[] produces) {
        this.produces = produces;
    }
    
    public static MultiProducerRequest readFrom(ByteBuffer buffer) {
        short count = buffer.getShort();
        ProducerRequest produces[] = new ProducerRequest[count];
        for (int i = 0; i < produces.length; i++) {
            produces[i] = ProducerRequest.readFrom(buffer);
        }
        return new MultiProducerRequest(produces);
    }
    
    @Override
    public int sizeInBytes() {
        int size = 2;
        for (ProducerRequest produce : produces) {
            size += produce.sizeInBytes();
        }
        return size;
    }
    
    @Override
    public void writeTo(ByteBuffer buffer) {
        if (produces.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Number of requests in MultiProducer exceeds " + Short.MAX_VALUE + ".");
        }
        buffer.putShort((short) produces.length);
        for (ProducerRequest produce : produces) {
            produce.writeTo(buffer);
        }
    }
    
    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        for (ProducerRequest produce : produces) {
            buffer.append(produce.toString());
            buffer.append(",");
        }
        return buffer.toString();
    }
    
}
