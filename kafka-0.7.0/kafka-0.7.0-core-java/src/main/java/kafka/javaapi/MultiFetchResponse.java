package kafka.javaapi;

import kafka.message.ByteBufferMessageSet;
import kafka.utils.IteratorTemplate;

import java.nio.ByteBuffer;
import java.util.Iterator;

public class MultiFetchResponse implements java.lang.Iterable<ByteBufferMessageSet> {
    
    private ByteBuffer buffer;
    private int numSets;
    private Long offsets[];
    
    ByteBuffer underlyingBuffer;
    short errorCode;
    kafka.api.MultiFetchResponse underlying;
    
    MultiFetchResponse(ByteBuffer buffer, int numSets, Long offsets[]) {
        this.buffer = buffer;
        this.numSets = numSets;
        this.offsets = offsets;
        this.underlyingBuffer = ByteBuffer.wrap(buffer.array());
        this.errorCode = underlyingBuffer.getShort();
        this.underlying = new kafka.api.MultiFetchResponse(underlyingBuffer, numSets, offsets);
        
    }
    @Override
    public String toString() {
        return underlying.toString();
    }
    @Override
    public java.util.Iterator<ByteBufferMessageSet> iterator() {
        return new IteratorTemplate<ByteBufferMessageSet>() {
            Iterator<ByteBufferMessageSet>  iter = underlying.iterator();
            @Override
            public  ByteBufferMessageSet makeNext() {
                if (iter.hasNext()) {
                    iter.next();
                } else {
                    return allDone();
                }
                return null;
            }
        };
    }
    
}
