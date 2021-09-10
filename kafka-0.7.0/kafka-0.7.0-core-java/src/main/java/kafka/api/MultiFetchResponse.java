package kafka.api;

import kafka.message.ByteBufferMessageSet;
import kafka.utils.IteratorTemplate;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

public class MultiFetchResponse implements Iterable<ByteBufferMessageSet> {
    
    public ByteBuffer buffer;
    public int numSets;
    public Long offsets[];
    
    private List<ByteBufferMessageSet> messageSets = new ArrayList<>();
    
    public MultiFetchResponse(ByteBuffer buffer, int numSets, Long offsets[]) {
        this.buffer = buffer;
        this.numSets = numSets;
        this.offsets = offsets;
        
        for (int i = 0; i < numSets; i++) {
            int size = buffer.getInt();
            int errorCode = buffer.getShort();
            ByteBuffer copy = buffer.slice();
            int payloadSize = size - 2;
            copy.limit(payloadSize);
            buffer.position(buffer.position() + payloadSize);
            try {
                messageSets.add(new ByteBufferMessageSet(copy, offsets[i], errorCode));
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }
    
    @Override
    public Iterator<ByteBufferMessageSet> iterator() {
        IteratorTemplate iterator =  new IteratorTemplate<ByteBufferMessageSet>() {
            Iterator<ByteBufferMessageSet> iter = messageSets.iterator();
            protected ByteBufferMessageSet makeNext() {
                if (iter.hasNext())
                    return iter.next();
                else
                    return allDone();
            }
        };
        return iterator;
    }
    
    @Override
    public String toString(){
       return   this.messageSets.toString();
    }
}
