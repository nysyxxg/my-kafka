package kafka.javaapi.message;

import kafka.common.InvalidMessageException;
import kafka.message.MessageAndOffset;

import java.util.Iterator;

public abstract class MessageSet implements Iterable<MessageAndOffset> {
    
    public MessageSet() throws Throwable {
        super();
    }
    
    public abstract java.util.Iterator<MessageAndOffset> iterator();
    
    public abstract Long sizeInBytes();
    
    public void validate() {
        Iterator<MessageAndOffset> thisIterator = this.iterator();
        while (thisIterator.hasNext()) {
            MessageAndOffset messageAndOffset = thisIterator.next();
            if (!messageAndOffset.message.isValid()) {
                throw new InvalidMessageException();
            }
        }
    }
}