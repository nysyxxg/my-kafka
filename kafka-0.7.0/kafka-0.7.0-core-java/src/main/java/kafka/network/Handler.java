package kafka.network;


public abstract class Handler {
    
    private Short requestTypeId;
    private Receive request;
    
    public Handler(Short requestTypeId, Receive request) {
        this.requestTypeId = requestTypeId;
        this.request = request;
    }
    
    public abstract Send Handler(Receive request);
    
    public abstract Handler HandlerMapping(Short requestTypeId, Receive request);
}
