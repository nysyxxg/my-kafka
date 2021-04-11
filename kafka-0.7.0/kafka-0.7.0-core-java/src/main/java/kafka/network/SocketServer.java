package kafka.network;


import kafka.api.RequestKeys;
import kafka.common.InvalidRequestException;
import kafka.server.KafkaRequestHandlers;
import kafka.utils.SystemTime;
import kafka.utils.Time;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class AbstractServerThread implements Runnable {
    protected Logger logger = Logger.getLogger(AbstractServerThread.class);
    protected Selector selector = Selector.open();
    
    private CountDownLatch startupLatch = new CountDownLatch(1);
    private CountDownLatch shutdownLatch = new CountDownLatch(1);
    private AtomicBoolean alive = new AtomicBoolean(false);
    
    protected AbstractServerThread() throws IOException {
    }
    
    void shutdown() {
        alive.set(false);
        selector.wakeup();
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    void awaitStartup() {
        try {
            startupLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    protected void startupComplete() {
        alive.set(true);
        startupLatch.countDown();
    }
    
    
    protected void shutdownComplete() {
        shutdownLatch.countDown();
    }
    
    
    protected boolean isRunning() {
        return alive.get();
    }
    
    
}

class Acceptor extends AbstractServerThread {
    private int port;
    private Processor[] processors;
    
    protected Acceptor() throws IOException {
    }
    
    public Acceptor(int port, Processor[] processors) throws IOException {
        this();
        this.port = port;
        this.processors = processors;
    }
    
    @Override
    public void run() {
        
        ServerSocketChannel serverChannel = null;
        try {
            serverChannel = ServerSocketChannel.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        try {
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(port));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("Awaiting connections on port " + port);
        startupComplete();
        
        int currentProcessor = 0;
        while (isRunning()) {
            int ready = 0;
            try {
                ready = selector.select(500);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (ready > 0) {
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();
                while (iter.hasNext() && isRunning()) {
                    SelectionKey key = null;
                    try {
                        key = iter.next();
                        iter.remove();
                        
                        if (key.isAcceptable()) {
                            accept(key, processors[currentProcessor]);
                        } else {
                            throw new IllegalStateException("Unrecognized key state for acceptor thread.");
                        }
                        // round robin to the next processor thread
                        currentProcessor = (currentProcessor + 1) % processors.length;
                    } catch (Exception e) {
                        logger.error("Error in acceptor", e);
                    }
                }
            }
        }
        logger.debug("Closing server socket and selector.");
        try {
            serverChannel.close();
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e);
        }
        
        shutdownComplete();
        
    }
    
    
    void accept(SelectionKey key, Processor processor) throws IOException {
        SelectableChannel selectableChannel = key.channel();
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectableChannel;
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (logger.isDebugEnabled()) {
            logger.info("Accepted connection from " + socketChannel.socket().getInetAddress() + " on "
                    + socketChannel.socket().getLocalSocketAddress());
        }
        try {
            socketChannel.configureBlocking(false);
            socketChannel.socket().setTcpNoDelay(true);
            processor.accept(socketChannel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}

class Processor extends AbstractServerThread {
    private Logger requestLogger = Logger.getLogger("kafka.request.logger");
    private ConcurrentLinkedQueue<SocketChannel> newConnections = new ConcurrentLinkedQueue<SocketChannel>();
    
    KafkaRequestHandlers   handlers;
    Time time;
    SocketServerStats stats;
    int maxRequestSize;
    
    protected Processor() throws IOException {
    }
    
    public Processor(KafkaRequestHandlers handlers, SystemTime time, SocketServerStats stats, int maxRequestSize)
            throws IOException {
        this();
        this.handlers = handlers;
        this.time = time;
        this.stats = stats;
        this.maxRequestSize = maxRequestSize;
        
    }
    
    
    @Override
    public void run() {
        startupComplete();
        while (isRunning()) {
            // setup any new connections that have been queued up
            configureNewConnections();
            int ready = 0;
            try {
                ready = selector.select(500);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (ready > 0) {
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();
                while (iter.hasNext() && isRunning()) {
                    SelectionKey key = null;
                    try {
                        key = iter.next();
                        iter.remove();
                        
                        if (key.isReadable()) {
                            read(key);
                        } else if (key.isWritable()) {
                            write(key);
                        } else if (!key.isValid()) {
                            close(key);
                        } else {
                            throw new IllegalStateException("Unrecognized key state for processor thread.");
                        }
                    } catch (InvalidRequestException e) {
                        InetAddress inetAddress = channelFor(key).socket().getInetAddress();
                        logger.info("Closing socket connection to %s due to invalid request: %s".format(inetAddress.toString(), e.getMessage()));
                        close(key);
                    } catch (Throwable e) {
                        logger.error("Closing socket for " + channelFor(key).socket().getInetAddress() + " because of error", e);
                        close(key);
                    }
                }
            }
        }
        
        logger.debug("Closing selector.");
        try {
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
            logger.info(e.getMessage(), e);
        }
        shutdownComplete();
    }
    
    private SocketChannel channelFor(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        return socketChannel;
        
    }
    
    private void close(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        if (logger.isDebugEnabled())
            logger.debug("Closing connection from " + channel.socket().getRemoteSocketAddress());
        try {
            channel.socket().close();
            channel.close();
            key.attach(null);
            key.cancel();
        } catch (IOException e) {
            e.printStackTrace();
            logger.info(e.getMessage(), e);
        }
    }
    
    private Optional<Send> handle(SelectionKey key, Receive request) {
        Short requestTypeId = request.buffer().getShort();
        if (requestLogger.isTraceEnabled()) {
            
            if (requestTypeId == RequestKeys.Produce) {
                requestLogger.trace("Handling produce request from " + channelFor(key).socket().getRemoteSocketAddress());
            } else if (requestTypeId == RequestKeys.Fetch) {
                requestLogger.trace("Handling fetch request from " + channelFor(key).socket().getRemoteSocketAddress());
            } else if (requestTypeId == RequestKeys.MultiFetch) {
                requestLogger.trace("Handling multi-fetch request from " + channelFor(key).socket().getRemoteSocketAddress());
            } else if (requestTypeId == RequestKeys.MultiProduce) {
                requestLogger.trace("Handling multi-produce request from " + channelFor(key).socket().getRemoteSocketAddress());
            } else if (requestTypeId == RequestKeys.Offsets) {
                requestLogger.trace("Handling offset request from " + channelFor(key).socket().getRemoteSocketAddress());
            } else {
                throw new InvalidRequestException("No mapping found for handler id " + requestTypeId);
            }
        }
        Handler handler = handlerMapping(requestTypeId, request);
        if (handler == null)
            throw new InvalidRequestException("No handler found for request");
        Long start = time.nanoseconds();
        Optional<Send>  maybeSend = handler(request);
        stats.recordRequest(requestTypeId, time.nanoseconds() - start);
        return maybeSend;
    }
    
    private Optional<Send> handler(Receive request) {
        Send  send = new Send() {
            @Override
            public int writeTo(WritableByteChannel channel) throws IOException {
                return 0;
            }
        };
        Optional<Send> optionalSend = Optional.of(send);
        return  optionalSend;
    }
    
    private Handler handlerMapping(Short requestTypeId, Receive request) {
        return new Handler(requestTypeId, request) {
            @Override
            public Send Handler(Receive request) {
                return null;
            }
    
            @Override
            public Handler HandlerMapping(Short requestTypeId, Receive request) {
                return null;
            }
        };
    }
    
    
    private void write(SelectionKey key) {
        SocketChannel socketChannel = channelFor(key);
        Receive request = (Receive) key.attachment();
        if(key.attachment() == null) {
            request = new BoundedByteBufferReceive(maxRequestSize);
            key.attach(request);
        }
        int read = request.readFrom(socketChannel);
        stats.recordBytesRead(read);
        if(logger.isTraceEnabled())
            logger.trace(read + " bytes read from " + socketChannel.socket().getRemoteSocketAddress());
        if(read < 0) {
            close(key);
            return;
        } else if(request.complete) {
            Optional<Send> maybeResponse = handle(key, request);
            key.attach(null);
            // if there is a response, send it, otherwise do nothing
            if(maybeResponse.isPresent()) {  // Optional.isPresent - 判断值是否存在
                // Optional.orElse - 如果值存在，返回它，否则返回默认值
                key.attach(maybeResponse.orElse(null));
                key.interestOps(SelectionKey.OP_WRITE);
            }
        } else {
            // more reading to be done
            key.interestOps(SelectionKey.OP_READ);
            selector.wakeup();
        }
    }
    
    private void read(SelectionKey key)  throws IOException {
        Send response = (Send) key.attachment();
        SocketChannel socketChannel = channelFor(key);
        int written = response.writeTo(socketChannel);
        stats.recordBytesWritten(written);
        if(logger.isTraceEnabled())
            logger.trace(written + " bytes written to " + socketChannel.socket().getRemoteSocketAddress());
        if(response.complete) {
            key.attach(null);
            key.interestOps(SelectionKey.OP_READ);
        } else {
            key.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }
    }
    
    private void configureNewConnections() {
        while (newConnections.size() > 0) {
            SocketChannel channel = newConnections.poll();
            if (logger.isDebugEnabled())
                logger.debug("Listening to new connection from " + channel.socket().getRemoteSocketAddress());
            try {
                channel.register(selector, SelectionKey.OP_READ);
            } catch (ClosedChannelException e) {
                e.printStackTrace();
            }
        }
        
    }
    
    void accept(SocketChannel socketChannel) {
        newConnections.add(socketChannel);
        selector.wakeup();
    }
    
    
}

public class SocketServer {
    
    private Logger logger = Logger.getLogger(SocketServer.class);
    private SystemTime time = new SystemTime();
    
    
    int port;
    int maxRequestSize = Integer.MAX_VALUE;
    ;
    int numProcessorThreads;
    int monitoringPeriodSecs;
    KafkaRequestHandlers  handlers;
    
    Processor processors[];
    Acceptor acceptor;
    public SocketServerStats stats;
    
    public  SocketServer(int port,
                 int numProcessorThreads,
                 int monitoringPeriodSecs,
                         KafkaRequestHandlers handlers,
                 int maxRequestSize) {
        this.port = port;
        this.numProcessorThreads = numProcessorThreads;
        this.monitoringPeriodSecs = monitoringPeriodSecs;
        this.handlers = handlers;
        this.maxRequestSize = maxRequestSize;
        
        processors = new Processor[numProcessorThreads];
        try {
            acceptor = new Acceptor(port, processors);
        } catch (IOException e) {
            e.printStackTrace();
        }
        stats = new SocketServerStats(1000L * 1000L * 1000L * monitoringPeriodSecs);
    }
    
    public void startup() throws IOException {
        for (int i = 0; i < numProcessorThreads; i++) {
            processors[i] = new Processor(handlers, time, stats, maxRequestSize);
            Utils.newThread("kafka-processor-" + i, processors[i], false).start();
        }
        Utils.newThread("kafka-acceptor", acceptor, false).start();
        acceptor.awaitStartup();
    }
    
    public void shutdown() {
        acceptor.shutdown();
        for (Processor processor : processors)
            processor.shutdown();
    }
    
    
}
