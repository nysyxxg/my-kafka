package kafka.network;

import org.apache.log4j.Logger;

public abstract class Transmission {
    
    public Logger logger = Logger.getLogger(Transmission.class);
    
    public Boolean complete;
    
    public void expectIncomplete() {
        if (complete)
            throw new IllegalStateException("This operation cannot be completed on a complete request.");
    }
    
    protected void expectComplete() {
        if (!complete)
            throw new IllegalStateException("This operation cannot be completed on an incomplete request.");
    }
    
}
