package kafka.consumer;

import kafka.api.FetchRequest;
import kafka.api.MultiFetchRequest;
import kafka.api.MultiFetchResponse;
import kafka.api.OffsetRequest;
import kafka.message.ByteBufferMessageSet;
import kafka.network.*;
import kafka.utils.SystemTime;
import org.apache.log4j.Logger;
import scala.Tuple2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.List;

public class SimpleConsumer {
    
    private Logger logger = Logger.getLogger(SimpleConsumer.class);
    private SocketChannel channel = null;
    private Object lock = new Object();
    
    String host;
    int port;
    int soTimeout;
    int bufferSize;
    
    public SimpleConsumer(String host, int port, int socketTimeoutMs, int socketBufferSize) {
        this.host = host;
        this.port = port;
        this.soTimeout = socketTimeoutMs;
        this.bufferSize = socketBufferSize;
    }
    
    private SocketChannel connect() throws SocketException {
        close();
        InetSocketAddress address = new InetSocketAddress(host, port);
        
        SocketChannel channel = null;
        
        if (logger.isDebugEnabled()) {
            logger.debug("Connected to " + address + " for fetching.");
        }
        try {
            channel = SocketChannel.open();
            channel.configureBlocking(true);
            channel.socket().setReceiveBufferSize(bufferSize);
            channel.socket().setSoTimeout(soTimeout);
            channel.socket().setKeepAlive(true);
            channel.connect(address);
            System.out.println("-------------init  初始化连接服务端---------------channel： " + channel);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (logger.isTraceEnabled()) {
            logger.trace("requested receive buffer size=" + bufferSize + " actual receive buffer size= " + channel.socket().getReceiveBufferSize());
            logger.trace("soTimeout=" + soTimeout + " actual soTimeout= " + channel.socket().getSoTimeout());
        }
        return channel;
    }
    
    private void close(SocketChannel channel) {
        if (logger.isDebugEnabled()) {
            logger.debug("Disconnecting from " + channel.socket().getRemoteSocketAddress());
        }
        
        try {
            channel.socket().close();
        } catch (IOException e) {
            e.printStackTrace();
            logger.warn(e.getMessage(), e);
        }
        
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
            logger.warn(e.getMessage(), e);
        }
        
        
    }
    
    public void close() {
        synchronized (lock) {
            if (channel != null) {
                System.out.println(this.getClass().getName() + "-----------close()------------------" + channel);
                close(channel);
            }
            channel = null;
        }
    }
    
    public ByteBufferMessageSet fetch(FetchRequest request) throws Throwable {
        synchronized (lock) {
            Long startTime = SystemTime.getMilliseconds();
            getOrMakeConnection();
            Tuple2<Receive, Integer> response = null;
            try {
                sendRequest(request);
                response = getResponse();
            } catch (Exception e) {
                logger.info("fetch reconnect due to " + e);
                // retry once
                try {
                    channel = connect();
                    sendRequest(request);
                    response = getResponse();  // 得到返回的数据
                } catch (SocketException e1) {
                    e1.printStackTrace();
                }
            }
            Long endTime = SystemTime.getMilliseconds();
            SimpleConsumerStats.recordFetchRequest(endTime - startTime);
            SimpleConsumerStats.recordConsumptionThroughput((long) response._1.buffer().limit());
            return new ByteBufferMessageSet(response._1.buffer(), request.offset, response._2);
        }
    }
    
    public MultiFetchResponse multifetch(List<FetchRequest> fetches) {
        synchronized (lock) {
            Long startTime = SystemTime.getMilliseconds();
            getOrMakeConnection();
            Tuple2<Receive, Integer> response = null;
    
            FetchRequest[] fetchRequests = new FetchRequest[fetches.size()];
            fetches.toArray(fetchRequests);
            Request request = new MultiFetchRequest(fetchRequests);
            try {
                sendRequest(request);
                response = getResponse();
            } catch (Exception e) {
                logger.info("multifetch reconnect due to " + e);
                // retry once
                try {
                    channel = connect();
                    sendRequest(request);
                    response = getResponse();
                } catch (SocketException e1) {
                    e1.printStackTrace();
                } catch (IOException ioe) {
                    channel = null;
                    throw ioe;
                }
            }
            Long endTime = SystemTime.getMilliseconds();
            SimpleConsumerStats.recordFetchRequest(endTime - startTime);
            SimpleConsumerStats.recordConsumptionThroughput((long) response._1.buffer().limit());
            
            // error code will be set on individual messageset inside MultiFetchResponse
            Long offsets[] = new Long[fetches.size()];
            for (int i = 0; i < fetches.size(); i++) {
                FetchRequest fetchRequest = fetches.get(i);
                offsets[i] = fetchRequest.offset;
            }
            
            return new MultiFetchResponse(response._1.buffer(), fetches.size(), offsets);
        }
    }
    
    public Long[] getOffsetsBefore(String topic, int partition, Long time, int maxNumOffsets) {
        synchronized (lock) {
            System.out.println(this.getClass().getName() + "----------------getOffsetsBefore------------------");
            getOrMakeConnection();
            Tuple2<Receive, Integer> response = null;
            try {
                sendRequest(new OffsetRequest(topic, partition, time, maxNumOffsets));
                response = getResponse();
            } catch (Exception e) {
                logger.info("getOffsetsBefore reconnect due to " + e);
                // retry once
                try {
                    channel = connect();
                    sendRequest(new OffsetRequest(topic, partition, time, maxNumOffsets));
                    response = getResponse();
                } catch (IOException ioe) {
                    channel = null;
                    ioe.printStackTrace();
                    
                }
            }
            return OffsetRequest.deserializeOffsetArray(response._1.buffer());
        }
    }
    
    
    private void sendRequest(Request request) {
        System.out.println(this.getClass().getName() + "----------------sendRequest------------------");
        Send send = new BoundedByteBufferSend(request);
        try {
            send.writeCompletely(channel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private Tuple2<Receive, Integer> getResponse() {
        System.out.println(SystemTime.getDateFormat() + " " + this.getClass().getName() + "--------1--------getResponse------------------");
        
        BoundedByteBufferReceive response = new BoundedByteBufferReceive();
        response.readCompletely(channel);
        System.out.println(SystemTime.getDateFormat() + " " + this.getClass().getName() + "--------2--------getResponse------------------");
        
        int errorCode = response.buffer().getShort();
        System.out.println(SystemTime.getDateFormat() + "-------------errorCode---------> " + errorCode);
        return new Tuple2<>(response, errorCode);
    }
    
    
    private void getOrMakeConnection() {
        if (channel == null) {
            try {
                channel = connect();
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
    }
    
}
