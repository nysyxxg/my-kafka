package kafka.message;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;

public abstract class AbstractMessageSet implements Iterable<MessageAndOffset> {
    
    abstract public Long writeTo(WritableByteChannel channel, Long offset, Long maxSize) throws IOException;
    
    abstract public Iterator<MessageAndOffset> iterator();
    
    abstract public Long sizeInBytes();
    
    
    public  void validate()   {
        for(MessageAndOffset messageAndOffset : this) {
            if (!messageAndOffset.getMessage().isValid()) {
                throw new InvalidMessageException();
            }
        }
    }
    
}
