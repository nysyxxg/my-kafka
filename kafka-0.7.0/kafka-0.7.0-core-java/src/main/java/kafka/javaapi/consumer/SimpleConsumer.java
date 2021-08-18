package kafka.javaapi.consumer;

import kafka.api.FetchRequest;
import kafka.javaapi.Implicits;
import kafka.javaapi.MultiFetchResponse;
import kafka.javaapi.message.ByteBufferMessageSet;

public class SimpleConsumer {
    
    String host;
    Integer port;
    Integer soTimeout;
    Integer bufferSize;
    
    kafka.consumer.SimpleConsumer underlying = null;
    
    public SimpleConsumer(String host, Integer port, Integer soTimeout, Integer bufferSize) {
        this.host = host;
        this.port = port;
        this.soTimeout = soTimeout;
        this.bufferSize = bufferSize;
        this.underlying = new kafka.consumer.SimpleConsumer(host, port, soTimeout, bufferSize);
    }
    
    public ByteBufferMessageSet fetch(FetchRequest request)   {
        try {
            // 转化为： kafka.javaapi.message.ByteBufferMessageSet
            return  Implicits.scalaMessageSetToJavaMessageSet(underlying.fetch(request));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return null;
    }
    
    public MultiFetchResponse multifetch(java.util.List<FetchRequest> fetches) {
        kafka.api.MultiFetchResponse  multiFetchResponse =  underlying.multifetch(fetches);
        // 需要进行转换，如果是scala语言，就是隐式转换
        return Implicits.toJavaMultiFetchResponse(multiFetchResponse);
    }
    
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public Integer getSoTimeout() {
        return soTimeout;
    }
    
    public void setSoTimeout(Integer soTimeout) {
        this.soTimeout = soTimeout;
    }
    
    public Integer getBufferSize() {
        return bufferSize;
    }
    
    public void setBufferSize(Integer bufferSize) {
        this.bufferSize = bufferSize;
    }
}
