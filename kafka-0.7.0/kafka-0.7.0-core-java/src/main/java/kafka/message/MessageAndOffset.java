package kafka.message;

public class MessageAndOffset {
    
    public Message message;
    public Long offset;
    
    MessageAndOffset(Message message, Long offset) {
        this.message = message;
        this.offset = offset;
    }
    
    public Message getMessage() {
        return message;
    }
    
    public void setMessage(Message message) {
        this.message = message;
    }
    
    public Long getOffset() {
        return offset;
    }
    
    public void setOffset(Long offset) {
        this.offset = offset;
    }
}
