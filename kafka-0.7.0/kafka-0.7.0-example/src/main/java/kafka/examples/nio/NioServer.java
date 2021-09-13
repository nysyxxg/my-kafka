package kafka.examples.nio;

import kafka.examples.util.NioUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.*;

public class NioServer {
    
    private static HashMap<String, SocketChannel> clientMap = new HashMap<String, SocketChannel>();
    
    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        
        ServerSocket serverSocket = serverSocketChannel.socket();
        serverSocket.bind(new InetSocketAddress(8899));
        
        Selector selector = Selector.open();
        
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        while (true) {
            int number = selector.select();
//            System.out.println("number:" + number);
            Set<SelectionKey> selectionKeySet = selector.selectedKeys();
            
            Iterator<SelectionKey> iterable = selectionKeySet.iterator();
            
            if (number > 0) {
                while (iterable.hasNext()) {
                    SelectionKey selectionKey = iterable.next();
                    
                    if (selectionKey.isAcceptable()) {//如果是可接收连接的
                        ServerSocketChannel ssc = (ServerSocketChannel) selectionKey.channel();
                        SocketChannel socketChannel = ssc.accept();
                        socketChannel.configureBlocking(false);
                        
                        socketChannel.register(selector, SelectionKey.OP_READ);//注册读事件
                        //  只要连接一个客户端，就保存下来
                        clientMap.put(UUID.randomUUID() + "", socketChannel);//保存下channel
                        
                        iterable.remove();
                    } else if (selectionKey.isReadable()) {//可读的
                        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                        // 收到的数据，不能大于1024个字节，所以，如果超过这个大小，需要进行继续读取数据
                        //  方法1： 一次性把所有数据读完
//                        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
//                        int readCount = socketChannel.read(byteBuffer); // 读取数据
//                        System.out.println(" ============读到的字节数=======readCount=" + readCount);
                        
                        // 方法2：首先读取客户端发送数据的长度，然后一次性读完
                        ByteBuffer byteBuffer = NioUtil.getDataByteBuffer(socketChannel);
                        int readCount = byteBuffer.capacity();
                        System.out.println(" ============读到的字节数=======readCount=" + readCount);
                        
                        //这里本该用while
                        if (readCount > 0) {//读取到数据，就写回到其他客户端
                            byteBuffer.flip();
                            
                            Charset charset = Charset.forName("UTF-8");
                            String receiveStr = new String(charset.decode(byteBuffer).array()); // 将字节数据转化为字符串
                            
                            System.out.println(socketChannel + " receive --- msg :" + receiveStr);
                            
                            String sendKey = "";
                            
                            for (Map.Entry<String, SocketChannel> entry : clientMap.entrySet()) {//第一遍遍历找到发送者
                                if (socketChannel == entry.getValue()) {
                                    sendKey = entry.getKey();
                                    break;
                                }
                            }
                            
                            for (Map.Entry<String, SocketChannel> entry : clientMap.entrySet()) {//给每个保存的连接，都发送消息
                                ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
                                writeBuffer.put((sendKey + ":" + receiveStr).getBytes());
                                
                                writeBuffer.flip();
                                SocketChannel socketChannel1 = entry.getValue();
                                if (socketChannel1.isConnected()) { // 判断是否处于连接状态
                                    socketChannel1.write(writeBuffer);
                                } else {
                                    System.out.println("---------------移除socketChannel1------------------isConnected: " + socketChannel1.isConnected());
                                }
                            }
                        }
                        iterable.remove();//这个 删除很关键  每次循环完selectionKeySet ，一定要清楚事件，不然肯定会影响下一次的事件触发，或者直接不触发下次的事件
                    }
                }
            }
            
        }
    }
    
}