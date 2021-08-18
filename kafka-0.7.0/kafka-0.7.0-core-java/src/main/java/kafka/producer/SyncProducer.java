package kafka.producer;

import kafka.api.MultiProducerRequest;
import kafka.api.ProducerRequest;
import kafka.api.RequestKeys;
import kafka.common.MessageSizeTooLargeException;
import kafka.message.ByteBufferMessageSet;
import kafka.message.MessageAndOffset;
import kafka.message.MessageSet;
import kafka.network.BoundedByteBufferSend;
import kafka.utils.SystemTime;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SyncProducer {
    
    private Logger logger = Logger.getLogger(getClass());
    
    private SyncProducerConfig config;
    private long MaxConnectBackoffMs = 60000;
    private SocketChannel channel = null;
    private int sentOnConnection = 0;
    private Object lock = new Object();
    private volatile Boolean shutdown = false;
    
    public SyncProducer(SyncProducerConfig syncProducerConfig) {
        this.config = syncProducerConfig;
        logger.debug("Instantiating Scala Sync Producer");
    }
    
    private void verifySendBuffer(ByteBuffer buffer) {
        if (logger.isTraceEnabled()) {
            logger.trace("verifying sendbuffer of size " + buffer.limit());
            Short requestTypeId = buffer.getShort();
            if (requestTypeId == RequestKeys.MultiProduce) {
                try {
                    MultiProducerRequest request = MultiProducerRequest.readFrom(buffer);
                    for (ProducerRequest produce : request.produces) {
                        try {
                            for (MessageAndOffset messageAndOffset : produce.messages)
                                if (!messageAndOffset.message.isValid())
                                    logger.trace("topic " + produce.topic + " is invalid");
                        } catch (Throwable e) {
                            logger.trace("error iterating messages " + e + Utils.stackTrace(e));
                        }
                    }
                } catch (Throwable e) {
                    logger.trace("error verifying sendbuffer " + e + Utils.stackTrace(e));
                }
            }
        }
    }
    
    private void send(BoundedByteBufferSend send) {
        synchronized (lock) {
            verifySendBuffer(send.buffer.slice());
            Long startTime = SystemTime.getMilliseconds();
            getOrMakeConnection();
            try {
                send.writeCompletely(channel);
            } catch (IOException e) {
                // no way to tell if write succeeded. Disconnect and re-throw exception to let client handle retry
                disconnect();
                try {
                    throw e;
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            // TODO: do we still need this?
            sentOnConnection += 1;
            if (sentOnConnection >= config.reconnectInterval) {
                disconnect();
                channel = connect();
                sentOnConnection = 0;
            }
            Long endTime = SystemTime.getMilliseconds();
            SyncProducerStats.recordProduceRequest(endTime - startTime);
        }
    }
    
    
    public void send(String topic, Integer partition, ByteBufferMessageSet messages) {
        verifyMessageSize(messages);
        Long setSize = messages.sizeInBytes();
        if (logger.isTraceEnabled())
            logger.trace("Got message set with " + setSize + " bytes to send");
        send(new BoundedByteBufferSend(new ProducerRequest(topic, partition, messages)));
    }
    
    private void verifyMessageSize(ByteBufferMessageSet messages) {
        for (MessageAndOffset messageAndOffset : messages)
            if (messageAndOffset.message.payloadSize > config.maxMessageSize)
                throw new MessageSizeTooLargeException();
    }
    
    public void multiSend(ProducerRequest produces[]) {
        Long setSize = 0L;
        for (ProducerRequest request : produces) {
            verifyMessageSize(request.messages);
            setSize += request.sizeInBytes();
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Got multi message sets with " + setSize + " bytes to send");
        }
        send(new BoundedByteBufferSend(new MultiProducerRequest(produces)));
    }
    
    
    void send(String topic, ByteBufferMessageSet messages) {
        send(topic, ProducerRequest.RandomPartition, messages);
    }
    
    public void close() {
        synchronized (lock) {
            disconnect();
            shutdown = true;
        }
    }
    
    private void getOrMakeConnection() {
        if (channel == null) {
            channel = connect();
        }
    }
    
    private SocketChannel connect() {
        SocketChannel channel = null;
        Long connectBackoffMs = 1L;
        Long beginTimeMs = SystemTime.getMilliseconds();
        while (channel == null && !shutdown) {
            try {
                channel = SocketChannel.open();
                channel.socket().setSendBufferSize(config.bufferSize);
                channel.configureBlocking(true);
                channel.socket().setSoTimeout(config.socketTimeoutMs);
                channel.socket().setKeepAlive(true);
                channel.connect(new InetSocketAddress(config.host, config.port));
                logger.info("开始连接服务端-----------Connected to " + config.host + ":" + config.port + " for producing");
            } catch (Exception e) {
                disconnect();
                Long endTimeMs = SystemTime.getMilliseconds();
                if ((endTimeMs - beginTimeMs + connectBackoffMs) > config.connectTimeoutMs) {
                    logger.error("Producer connection timing out after " + config.connectTimeoutMs + " ms", e);
                }
                logger.error("Connection attempt failed, next attempt in " + connectBackoffMs + " ms", e);
                SystemTime.sleepByTime(connectBackoffMs);
                connectBackoffMs = Math.min(10 * connectBackoffMs, MaxConnectBackoffMs);
            }
        }
        return channel;
    }
    
    private void disconnect() {
        try {
            if (channel != null) {
                logger.info("-----关闭客户端连接--------------Disconnecting from " + config.host + ":" + config.port);
                channel.close();
                channel.socket().close();
                channel = null;
            }
        } catch (Exception e) {
            logger.error("Error on disconnect: ", e);
        }
    }
}
