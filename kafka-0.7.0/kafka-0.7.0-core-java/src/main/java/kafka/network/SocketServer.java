package kafka.network;


import kafka.api.RequestKeys;
import kafka.common.InvalidRequestException;
import kafka.server.KafkaRequestHandlers;
import kafka.utils.SystemTime;
import kafka.utils.Time;
import kafka.utils.Utils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class AbstractServerThread implements Runnable {
    protected Logger logger = Logger.getLogger(AbstractServerThread.class);
    protected Selector selector = Selector.open();        //得到一个Selecor对象
    
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
//  监听，接收客户端连接， 得到 SocketChannel, 如果是多个broker，就会在每个机器上，都会启动一个Acceptor线程
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
            serverChannel = ServerSocketChannel.open(); //创建ServerSocketChannel -> ServerSocket
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        try {
            serverChannel.configureBlocking(false);        //设置为非阻塞
            serverChannel.socket().bind(new InetSocketAddress(port));        //绑定一个端口6666, 在服务器端监听
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);   //把 serverSocketChannel 注册到  selector 关心 事件为 OP_ACCEPT
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
                // selector.select 监控所有的通道，当其中有IO操作可以进行时，将对应的SelectionKey加入内部集合中
                //这里我们等待1秒，如果没有事件发生, 返回
                ready = selector.select(500);
                if(ready == 0 ){//没有事件发生
                   // System.out.println( Thread.currentThread().getName() +  " --> 服务器等待了500 耗秒，无连接");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            //如果返回的>0, 就获取到相关的 selectionKey集合
            //1.如果返回的>0， 表示已经获取到关注的事件
            //2. selector.selectedKeys() 返回关注事件的集合
            //   通过 selectionKeys 反向获取通道
            if (ready > 0) {
                Set<SelectionKey> keys = selector.selectedKeys();
                System.out.println("selectionKeys 数量 = " + keys.size());
                
                //遍历 Set<SelectionKey>, 使用迭代器遍历
                Iterator<SelectionKey> iter = keys.iterator();
                while (iter.hasNext() && isRunning()) {
                    SelectionKey key = null;
                    try {
                        key = iter.next();  //  //获取到SelectionKey
                        iter.remove(); // //手动从集合中移动当前的selectionKey, 防止重复操作
                        
                        if (key.isAcceptable()) { //  //根据key 对应的通道发生的事件做相应处理
                            accept(key, processors[currentProcessor]);//  //如果是 OP_ACCEPT, 有新的客户端连接
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
    
    //   //如果是 OP_ACCEPT, 有新的客户端连接
    void accept(SelectionKey key, Processor processor) throws IOException {
        SelectableChannel selectableChannel = key.channel();
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectableChannel;
        SocketChannel socketChannel = serverSocketChannel.accept(); //该该客户端生成一个 SocketChannel
        System.out.println("客户端连接成功 生成了一个 socketChannel " + socketChannel.hashCode());
        if (logger.isDebugEnabled()) {
            logger.info("Accepted connection from " + socketChannel.socket().getInetAddress() + " on " + socketChannel.socket().getLocalSocketAddress());
        }
        try {
            socketChannel.configureBlocking(false);   //将  SocketChannel 设置为非阻塞
            socketChannel.socket().setTcpNoDelay(true);// TcpNoDelay=false，为启用nagle算法，也是默认值。
            processor.accept(socketChannel); // 处理这个socketChannel
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
//  处理客户端连接的核心线程
class Processor extends AbstractServerThread {
    private Logger requestLogger = Logger.getLogger("kafka.request.logger");
    /**
     * 一个基于链接节点的无界线程安全队列。此队列按照 FIFO（先进先出）原则对元素进行排序。
     * 队列的头部 是队列中时间最长的元素。队列的尾部 是队列中时间最短的元素。
     * 新的元素插入到队列的尾部，队列获取操作从队列头部获得元素。
     * 当多个线程共享访问一个公共 collection 时，ConcurrentLinkedQueue 是一个恰当的选择。此队列不允许使用 null 元素
     */
    private ConcurrentLinkedQueue<SocketChannel> newConnections = new ConcurrentLinkedQueue<SocketChannel>();
    
    private  KafkaRequestHandlers   handlers;
    private  Time time;
    private SocketServerStats stats;
    private int maxRequestSize;
    
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
            try {
                // 主要为了测试，加上了休眠时间，生成环境，应去掉
                Thread.sleep(1* 1000);
               // System.out.println("-------------正在后台运行的线程---->" + Thread.currentThread().getName());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
           
            // setup any new connections that have been queued up
            configureNewConnections(); //  配置一个新的连接，针对SocketChannel 进行配置
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
                        key = iter.next(); //获取到SelectionKey
                        iter.remove();     //手动从集合中移动当前的selectionKey, 防止重复操作
                        if (key.isReadable()) {  //发生 OP_READ
                            System.out.println(SystemTime.getDateFormat() + "------------------------------OP_READ-------------key.isReadable()---" + key.isReadable());
                            read(key); // 读取操作
                        } else if (key.isWritable()) { // OP_WRITE
                            System.out.println(SystemTime.getDateFormat() + "------------------------------OP_WRITE-------------key.isWritable()---" + key.isWritable());
                            write(key); // 写操作
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
    
    private  Send  handle(SelectionKey key, Receive request) {
        Short requestTypeId = request.buffer().getShort(); //获取客户端请求类型Id
        System.out.println(" kafka 服务器端开始处理接收到的请求： requestTypeId = " +  requestTypeId );
        requestLogger.setLevel(Level.DEBUG);
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
        Send send =  handlers.handlerFor(requestTypeId,request); // 根据客户端请求类型Id，进行不同的处理
        if (send  == null) {
            throw new InvalidRequestException("No handler found for request.....没有handler 处理这个请求!!!!");
        }
        Long start = time.nanoseconds();
        stats.recordRequest(requestTypeId, time.nanoseconds() - start);
        return send;
    }
    
    private void read(SelectionKey key) {
        SocketChannel socketChannel = channelFor(key);//  通过key 反向获取到对应channel
        Receive request = (Receive) key.attachment();  //获取到该channel关联的  request
        System.out.println(SystemTime.getDateFormat()+"--------1---read---------------request----" + request);
        if(key.attachment() == null) { // 如果为null，就创建一个对象
            request = new BoundedByteBufferReceive(maxRequestSize); //
            key.attach(request);
        }
        System.out.println(SystemTime.getDateFormat()+ "--------2---read----------------request---" + request);
        int read = request.readFrom(socketChannel);  // 从 socketChannel 中读取数据 ，并返回读取数据的长度
        System.out.println(SystemTime.getDateFormat()+"--------3---read----------------read: " + read);
    
        stats.recordBytesRead(read);
        if(logger.isTraceEnabled())
            logger.trace(read + " bytes read from " + socketChannel.socket().getRemoteSocketAddress());
        if(read < 0) {  // 说明没有读取到数据
            System.out.println(SystemTime.getDateFormat()+"----------4--------close------------------------read:" + read);
            close(key);
            return;
        } else if(request.complete()) { //  如果 从 channel 中 数据读取完成
           Send  maybeResponse = handle(key, request); // 开始进行处理
            key.attach(null);
            // if there is a response, send it, otherwise do nothing
            if(maybeResponse != null) {  // Optional.isPresent - 判断值是否存在
                // Optional.orElse - 如果值存在，返回它，否则返回默认值
                key.attach(maybeResponse); //将key进行关联--> 需要写入的对象 maybeResponse
                key.interestOps(SelectionKey.OP_WRITE);//将 key设置为写操作事件
            }
        } else { // 如果没有完成，继续注册操作事件：OP_READ
            // more reading to be done
            key.interestOps(SelectionKey.OP_READ);
            selector.wakeup();
        }
    }
    
    private void write(SelectionKey key)  throws IOException {
        Send response = (Send) key.attachment();
        SocketChannel socketChannel = channelFor(key);
        int written = response.writeTo(socketChannel);
        stats.recordBytesWritten(written);
        if(logger.isTraceEnabled())
            logger.trace(written + " bytes written to " + socketChannel.socket().getRemoteSocketAddress());
        if(response.complete()) { // 如果完成操作，就切换操作类型为 OP_READ
            key.attach(null);
            key.interestOps(SelectionKey.OP_READ);
        } else {
            key.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }
    }
    
    private void configureNewConnections() {
        while (newConnections.size() > 0) {
            SocketChannel channel = newConnections.poll();  // 从队列中获取
            if (logger.isDebugEnabled())
                logger.debug("Listening to new connection from " + channel.socket().getRemoteSocketAddress());
            try {
                //将socketChannel 注册到selector, 关注事件为 OP_READ， 同时给socketChannel
                channel.register(selector, SelectionKey.OP_READ);
            } catch (ClosedChannelException e) {
                e.printStackTrace();
            }
        }
        
    }
    
    void accept(SocketChannel socketChannel) {
        newConnections.add(socketChannel);  // 将这个 SocketChannel  添加到  ConcurrentLinkedQueue
        selector.wakeup();
    }
    
    
}

public class SocketServer {
    
    private Logger logger = Logger.getLogger(SocketServer.class);
    private SystemTime time = new SystemTime();
    
    private int port;
    private  int maxRequestSize = Integer.MAX_VALUE;
    private  int numProcessorThreads;
    private int monitoringPeriodSecs;
    private   KafkaRequestHandlers  handlers;
    
    private  Processor processors[];
    private  Acceptor acceptor;
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
            acceptor = new Acceptor(this.port, processors); //单独启动一个线程， 接收客户端连接，为了提高性能，可以初始化一个数组，和processors一样
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
